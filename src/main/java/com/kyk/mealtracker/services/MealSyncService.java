package com.kyk.mealtracker.services;

import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.BotUserRepository;
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
                Thread.sleep(1200); // Siteleri yormamak için nazik bekleme
            } catch (InterruptedException ignored) {}
        }
        logger.info("Tüm aktif şehirlerin çoklu kaynak menü senkronizasyonu tamamlandı.");
    }

    /**
     * Belirli bir şehir için belirtilen ayın menüsünü tüm kaynaklardan (kykyemek.com + kykyurtmenu.com + yurtmenu.net)
     * çekip birleştirir (merge) ve DB'ye yazar.
     */
    public int syncCityMenu(int cityId, YearMonth yearMonth) {
        String cityName = CityHelper.getCityName(cityId);
        logger.info("Çoklu kaynak senkronizasyonu: Şehir: {} (Plaka: {}), Ay: {}", cityName, cityId, yearMonth);

        Map<String, Meal> mergedMap = new LinkedHashMap<>();

        // 1. Kaynak: kykyemek.com (Tam 30 günlük aylık liste + detaylı kalori/alternatif yemekler)
        List<Meal> source1Meals = kykYemekComScraper.scrapeMonthlyMenu(cityId, yearMonth);
        for (Meal m : source1Meals) {
            if (MealValidator.isValidMeal(m)) {
                mergedMap.put(mealKey(m.getDate(), m.getMealType()), m);
            }
        }

        // 2. Kaynak: kykyurtmenu.com (81 İl Aylık Arşiv + Günlük JSON-LD)
        List<Meal> source2Meals = kykYurtMenuScraper.scrapeMonthlyMenu(cityId, yearMonth);
        for (Meal m : source2Meals) {
            if (MealValidator.isValidMeal(m)) {
                String key = mealKey(m.getDate(), m.getMealType());
                Meal existing = mergedMap.get(key);
                if (existing == null || isRicherMeal(m, existing)) {
                    mergedMap.put(key, m);
                }
            }
        }

        // 3. Kaynak (Canlı Günlük/Yarın Kurtarıcı): Bugün ve yarın için hala eksik öğün varsa yurtmenu.net ve tekil sayfaları yokla
        LocalDate today = LocalDate.now(TURKEY_ZONE);
        rescueSingleDateIfMissing(cityId, today, yearMonth, mergedMap);
        rescueSingleDateIfMissing(cityId, today.plusDays(1), yearMonth, mergedMap);

        List<Meal> finalMeals = new ArrayList<>(mergedMap.values());
        if (!finalMeals.isEmpty()) {
            mealService.saveAllMeals(finalMeals);
            logger.info("Başarıyla kaydedildi: {} için {} adet birleştirilmiş öğün (Kaynak1: {}, Kaynak2: {})",
                    cityName, finalMeals.size(), source1Meals.size(), source2Meals.size());
            return finalMeals.size();
        } else {
            logger.warn("{} için hiçbir kaynakta geçerli menü verisi bulunamadı.", cityName);
            return 0;
        }
    }

    /**
     * Kullanıcının istediği tarih ve şehir için veritabanında eksik öğün varsa
     * anında çoklu kaynaklardan tamamlayıp güncel listeyi döner.
     */
    public List<Meal> ensureMealsForDateAndCity(LocalDate date, int cityId) {
        List<Meal> existing = mealService.getMealsByDateAndCity(date, cityId);
        if (existing.size() >= 2) {
            return existing;
        }

        // Eğer bu şehir için o ay veritabanında hiç kayıt yoksa önce aylık senkronizasyonu çalıştır
        YearMonth ym = YearMonth.from(date);
        List<Meal> monthMeals = mealService.getMealsByDateRangeAndCity(ym.atDay(1), ym.atEndOfMonth(), cityId);
        if (monthMeals.isEmpty()) {
            syncCityMenu(cityId, ym);
            existing = mealService.getMealsByDateAndCity(date, cityId);
            if (existing.size() >= 2) {
                return existing;
            }
        }

        // Hala o günün kahvaltı veya akşam yemeği eksikse tekil gün kurtarıcıları çalıştır
        boolean hasBreakfast = existing.stream().anyMatch(m -> m.getMealType() != null && m.getMealType() == 0);
        boolean hasDinner = existing.stream().anyMatch(m -> m.getMealType() != null && m.getMealType() == 1);

        List<Meal> rescued = new ArrayList<>();

        // yurtmenu.net üzerinden o günü dene
        List<Meal> ymNetMeals = yurtMenuNetScraper.scrapeDayMeals(cityId, date);
        for (Meal m : ymNetMeals) {
            if (MealValidator.isValidMeal(m)) {
                rescued.add(m);
                if (m.getMealType() == 0) hasBreakfast = true;
                if (m.getMealType() == 1) hasDinner = true;
            }
        }

        // kykyurtmenu.com tekil gün detay sayfasından dene
        if (!hasBreakfast) {
            Meal bf = kykYurtMenuScraper.scrapeSingleDayMeal(cityId, date, 0);
            if (MealValidator.isValidMeal(bf)) rescued.add(bf);
        }
        if (!hasDinner) {
            Meal dn = kykYurtMenuScraper.scrapeSingleDayMeal(cityId, date, 1);
            if (MealValidator.isValidMeal(dn)) rescued.add(dn);
        }

        if (!rescued.isEmpty()) {
            mealService.saveAllMeals(rescued);
            existing = mealService.getMealsByDateAndCity(date, cityId);
        }

        return existing;
    }

    private void rescueSingleDateIfMissing(int cityId, LocalDate date, YearMonth targetMonth, Map<String, Meal> mergedMap) {
        if (!YearMonth.from(date).equals(targetMonth)) return;

        String bfKey = mealKey(date, 0);
        String dnKey = mealKey(date, 1);

        if (!mergedMap.containsKey(bfKey) || !mergedMap.containsKey(dnKey)) {
            List<Meal> dayMeals = yurtMenuNetScraper.scrapeDayMeals(cityId, date);
            for (Meal m : dayMeals) {
                if (MealValidator.isValidMeal(m)) {
                    mergedMap.putIfAbsent(mealKey(m.getDate(), m.getMealType()), m);
                }
            }
        }

        if (!mergedMap.containsKey(bfKey)) {
            Meal bf = kykYurtMenuScraper.scrapeSingleDayMeal(cityId, date, 0);
            if (MealValidator.isValidMeal(bf)) mergedMap.put(bfKey, bf);
        }

        if (!mergedMap.containsKey(dnKey)) {
            Meal dn = kykYurtMenuScraper.scrapeSingleDayMeal(cityId, date, 1);
            if (MealValidator.isValidMeal(dn)) mergedMap.put(dnKey, dn);
        }
    }

    private String mealKey(LocalDate date, Integer mealType) {
        return date + ":" + mealType;
    }

    private boolean isRicherMeal(Meal candidate, Meal current) {
        int candCount = countNonEmptyItems(candidate);
        int currCount = countNonEmptyItems(current);
        return candCount > currCount;
    }

    private int countNonEmptyItems(Meal m) {
        int c = 0;
        if (m.getFirst() != null && !m.getFirst().isBlank()) c++;
        if (m.getSecond() != null && !m.getSecond().isBlank()) c++;
        if (m.getThird() != null && !m.getThird().isBlank()) c++;
        if (m.getFourth() != null && !m.getFourth().isBlank()) c++;
        return c;
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
