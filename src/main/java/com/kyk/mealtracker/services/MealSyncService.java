package com.kyk.mealtracker.services;

import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.BotUserRepository;
import com.kyk.mealtracker.util.CalorieHelper;
import com.kyk.mealtracker.util.CityHelper;
import com.kyk.mealtracker.util.MealValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MealSyncService {

    private static final Logger logger = LoggerFactory.getLogger(MealSyncService.class);
    private static final ZoneId TURKEY_ZONE = ZoneId.of("Europe/Istanbul");

    private final MealService mealService;
    private final KykYemekComScraperService kykYemekComScraper;
    private final KykYurtMenuScraperService kykYurtMenuScraper;
    private final YurtMenuNetScraperService yurtMenuNetScraper;
    private final BotUserRepository botUserRepository;

    // Hangi (şehir, tarih) ikilisinin detay kaynaklardan çapraz doğrulandığını takip eder
    private final Set<String> detailVerifiedDays = ConcurrentHashMap.newKeySet();

    /**
     * Botta kayıtlı olan ve aktif kullanıcı bulunan tüm şehirler için menüleri çeker.
     */
    public void fetchMealsForAllActiveCities() {
        YearMonth currentMonth = YearMonth.now(TURKEY_ZONE);

        List<Integer> activeCityIds = botUserRepository.findDistinctCityIds();
        if (activeCityIds.isEmpty()) {
            activeCityIds = new ArrayList<>();
            activeCityIds.add(1); // Varsayılan Adana
        }

        logger.info("Çoklu kaynak şehir menü senkronizasyonu başlatılıyor... Ay: {}, Şehir Sayısı: {}", currentMonth, activeCityIds.size());

        for (Integer cityId : activeCityIds) {
            syncCityMenu(cityId, currentMonth);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }
        logger.info("Tüm aktif şehirlerin çoklu kaynak menü senkronizasyonu tamamlandı.");
    }

    /**
     * Belirli bir şehir için belirtilen ayın menüsünü tüm kaynaklardan (kykyemek.com + kykyurtmenu.com + yurtmenu.net)
     * çekip yemek bazında çapraz doğrulayarak birleştirir (field-level smart merge) ve DB'ye yazar.
     */
    public int syncCityMenu(int cityId, YearMonth yearMonth) {
        String cityName = CityHelper.getCityName(cityId);
        logger.info("Çoklu kaynak çapraz senkronizasyonu: Şehir: {} (Plaka: {}), Ay: {}", cityName, cityId, yearMonth);

        Map<String, Meal> mergedMap = new LinkedHashMap<>();

        // 1. Kaynak: kykyemek.com (Tam 30 günlük aylık liste + detaylı kalori/alternatif yemekler)
        List<Meal> source1Meals = kykYemekComScraper.scrapeMonthlyMenu(cityId, yearMonth);
        for (Meal m : source1Meals) {
            if (MealValidator.isValidMeal(m)) {
                putOrMerge(mergedMap, m);
            }
        }

        // 2. Kaynak: kykyurtmenu.com (81 İl Aylık Arşiv)
        List<Meal> source2Meals = kykYurtMenuScraper.scrapeMonthlyMenu(cityId, yearMonth);
        for (Meal m : source2Meals) {
            if (MealValidator.isValidMeal(m)) {
                putOrMerge(mergedMap, m);
            }
        }

        // 3. Yakın günler (Bugün + 3 gün) için detay sayfaları ve yurtmenu.net üzerinden çapraz doğrulama & zenginleştirme
        LocalDate today = LocalDate.now(TURKEY_ZONE);
        for (int i = 0; i <= 3; i++) {
            LocalDate d = today.plusDays(i);
            if (YearMonth.from(d).equals(yearMonth)) {
                crossVerifyAndEnrichDate(cityId, d, mergedMap);
                detailVerifiedDays.add(dayVerifyKey(cityId, d));
            }
        }

        List<Meal> finalMeals = new ArrayList<>(mergedMap.values());
        if (!finalMeals.isEmpty()) {
            mealService.saveAllMeals(finalMeals);
            logger.info("Başarıyla kaydedildi: {} için {} adet çapraz doğrulanmış öğün (Kaynak1: {}, Kaynak2: {})",
                    cityName, finalMeals.size(), source1Meals.size(), source2Meals.size());
            return finalMeals.size();
        } else {
            logger.warn("{} için hiçbir kaynakta geçerli menü verisi bulunamadı.", cityName);
            return 0;
        }
    }

    /**
     * Kullanıcının istediği tarih ve şehir için veritabanındaki veriyi kontrol eder;
     * eğer o gün henüz detay kaynaklarla çapraz doğrulanmadıysa veya eksik öğün varsa
     * tüm kaynaklardan (kykyurtmenu detay JSON + yurtmenu.net) karşılaştırıp eksikleri ve kesik isimleri tamamlar.
     */
    public List<Meal> ensureMealsForDateAndCity(LocalDate date, int cityId) {
        YearMonth ym = YearMonth.from(date);
        List<Meal> monthMeals = mealService.getMealsByDateRangeAndCity(ym.atDay(1), ym.atEndOfMonth(), cityId);
        if (monthMeals.isEmpty()) {
            syncCityMenu(cityId, ym);
        }

        List<Meal> existing = mealService.getMealsByDateAndCity(date, cityId);
        String vKey = dayVerifyKey(cityId, date);

        // Eğer bu gün daha önce detay kaynaklarla çapraz doğrulandıysa ve 2 öğün de tamamsa doğrudan dön
        if (detailVerifiedDays.contains(vKey) && existing.size() >= 2) {
            return existing;
        }

        Map<String, Meal> dayMap = new LinkedHashMap<>();
        for (Meal m : existing) {
            if (MealValidator.isValidMeal(m)) {
                dayMap.put(mealKey(m.getDate(), m.getMealType()), m);
            }
        }

        // Tüm tekil detay kaynaklardan (yurtmenu.net + kykyurtmenu.com Next.js tam JSON) çapraz doğrula ve birleştir
        crossVerifyAndEnrichDate(cityId, date, dayMap);
        detailVerifiedDays.add(vKey);

        List<Meal> enrichedList = new ArrayList<>(dayMap.values());
        if (!enrichedList.isEmpty()) {
            mealService.saveAllMeals(enrichedList);
            return mealService.getMealsByDateAndCity(date, cityId);
        }

        return existing;
    }

    private void crossVerifyAndEnrichDate(int cityId, LocalDate date, Map<String, Meal> targetMap) {
        // A) yurtmenu.net üzerinden o günün kahvaltı ve akşam yemeğini çekip karşılaştır
        List<Meal> ymNetMeals = yurtMenuNetScraper.scrapeDayMeals(cityId, date);
        for (Meal m : ymNetMeals) {
            if (MealValidator.isValidMeal(m)) {
                putOrMerge(targetMap, m);
            }
        }

        // B) kykyurtmenu.com tekil gün detay sayfasındaki tam JSON ("/" ile kesilmemiş tam isimler ve gerçek kaloriler)
        Meal detailBf = kykYurtMenuScraper.scrapeSingleDayMeal(cityId, date, 0);
        if (MealValidator.isValidMeal(detailBf)) {
            putOrMerge(targetMap, detailBf);
        }

        Meal detailDn = kykYurtMenuScraper.scrapeSingleDayMeal(cityId, date, 1);
        if (MealValidator.isValidMeal(detailDn)) {
            putOrMerge(targetMap, detailDn);
        }
    }

    private void putOrMerge(Map<String, Meal> map, Meal candidate) {
        String key = mealKey(candidate.getDate(), candidate.getMealType());
        Meal current = map.get(key);
        if (current == null) {
            map.put(key, candidate);
        } else {
            map.put(key, mergeTwoMeals(current, candidate));
        }
    }

    /**
     * İki farklı kaynaktan gelen aynı gün ve öğüne ait menüyü YEMEK BAZINDA (first, second, third, fourth)
     * karşılaştırır; kesik kalmış ("Meyveli" gibi) veya alternatifleri eksik isimleri tam olanla tamamlar.
     */
    private Meal mergeTwoMeals(Meal a, Meal b) {
        String first = pickBetterDishName(a.getFirst(), b.getFirst());
        String second = pickBetterDishName(a.getSecond(), b.getSecond());
        String third = pickBetterDishName(a.getThird(), b.getThird());
        String fourth = pickBetterDishName(a.getFourth(), b.getFourth());

        String fc = pickBetterCalorieStr(a.getFirstCalories(), b.getFirstCalories(), first);
        String sc = pickBetterCalorieStr(a.getSecondCalories(), b.getSecondCalories(), second);
        String tc = pickBetterCalorieStr(a.getThirdCalories(), b.getThirdCalories(), third);
        String foc = pickBetterCalorieStr(a.getFourthCalories(), b.getFourthCalories(), fourth);

        // Toplam kalori: Gerçek detay kalorisi varsa veya yeniden hesaplanan toplam daha doğruysa onu kullan
        int c1 = CalorieHelper.getOrEstimateCalories(first, fc);
        int c2 = CalorieHelper.getOrEstimateCalories(second, sc);
        int c3 = CalorieHelper.getOrEstimateCalories(third, tc);
        int c4 = CalorieHelper.getOrEstimateCalories(fourth, foc);
        int sumCal = c1 + c2 + c3 + c4;

        Integer totA = CalorieHelper.parseCalorieNumber(a.getTotalCalories());
        Integer totB = CalorieHelper.parseCalorieNumber(b.getTotalCalories());
        int finalTotal = sumCal;
        if (b.getFirstCalories() != null && b.getFirstCalories().contains("/") && totB != null && totB > 0) {
            finalTotal = totB;
        } else if (a.getFirstCalories() != null && a.getFirstCalories().contains("/") && totA != null && totA > 0) {
            finalTotal = totA;
        }

        return Meal.builder()
                .id(a.getId() != null ? a.getId() : b.getId())
                .date(a.getDate())
                .mealType(a.getMealType())
                .cityId(a.getCityId())
                .first(first).firstCalories(fc)
                .second(second).secondCalories(sc)
                .third(third).thirdCalories(tc)
                .fourth(fourth).fourthCalories(foc)
                .totalCalories(String.valueOf(finalTotal))
                .build();
    }

    /**
     * İki yemek isminden hangisi daha eksiksizse (örn: "/" ile alternatif içeriyorsa veya kesik değilse) onu seçer.
     */
    private String pickBetterDishName(String a, String b) {
        String cleanA = KykYurtMenuScraperService.fixTruncatedDishName(a != null ? a.trim() : "");
        String cleanB = KykYurtMenuScraperService.fixTruncatedDishName(b != null ? b.trim() : "");

        if (cleanA.isEmpty()) return cleanB.isEmpty() ? null : cleanB;
        if (cleanB.isEmpty()) return cleanA;

        boolean aHasSlash = cleanA.contains("/");
        boolean bHasSlash = cleanB.contains("/");

        // Eğer bir kaynakta "/" ile alternatif yemek de yazıyorsa (örn: "Meyveli / Çikolatalı Yaş Pasta" veya "Ezogelin / Mantar Çorba") onu tercih et
        if (bHasSlash && !aHasSlash) return cleanB;
        if (aHasSlash && !bHasSlash) return cleanA;

        // Eğer biri diğerinin kesilmiş hali veya daha kısa haliyse uzun ve tam olanı seç
        if (cleanB.length() > cleanA.length()) return cleanB;
        return cleanA;
    }

    private String pickBetterCalorieStr(String calA, String calB, String dishName) {
        if (calB != null && calB.contains("/")) return calB;
        if (calA != null && calA.contains("/")) return calA;
        if (calB != null && !calB.isBlank()) return calB;
        if (calA != null && !calA.isBlank()) return calA;
        int est = CalorieHelper.getOrEstimateCalories(dishName, null);
        return est > 0 ? String.valueOf(est) : null;
    }

    private String mealKey(LocalDate date, Integer mealType) {
        return date + ":" + mealType;
    }

    private String dayVerifyKey(int cityId, LocalDate date) {
        return cityId + ":" + date;
    }

    /**
     * Kullanıcı yeni bir şehir seçtiğinde botu dondurmadan arka planda o şehri senkronize eder.
     */
    @Async
    public void syncCityMenuAsync(int cityId) {
        syncCityMenu(cityId, YearMonth.now(TURKEY_ZONE));
    }

    public void fetchMealsForDateAndCity(LocalDate date, Integer cityId) {
        ensureMealsForDateAndCity(date, cityId);
    }
}
