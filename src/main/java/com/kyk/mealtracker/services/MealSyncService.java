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
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealSyncService {

    private static final Logger logger = LoggerFactory.getLogger(MealSyncService.class);

    private final MealService mealService;
    private final KykYurtMenuScraperService scraperService;
    private final BotUserRepository botUserRepository;

    /**
     * Botta kayıtlı olan ve aktif kullanıcı bulunan tüm şehirler için menüleri çeker.
     * Ayın mevcut ayı ve gerekiyorsa sonraki ayı için senkronize eder.
     */
    public void fetchMealsForAllActiveCities() {
        YearMonth currentMonth = YearMonth.now();

        List<Integer> activeCityIds = botUserRepository.findDistinctCityIds();
        if (activeCityIds.isEmpty()) {
            activeCityIds = new ArrayList<>();
            activeCityIds.add(1); // Varsayılan Adana
        }

        logger.info("Şehir menüleri senkronizasyonu başlatılıyor... Ay: {}, Şehir Sayısı: {}", currentMonth, activeCityIds.size());

        for (Integer cityId : activeCityIds) {
            syncCityMenu(cityId, currentMonth);
            try {
                Thread.sleep(1500); // Siteleri yormamak için nazik bekleme
            } catch (InterruptedException ignored) {}
        }
        logger.info("Tüm aktif şehirlerin menü senkronizasyonu tamamlandı.");
    }

    /**
     * Belirli bir şehir için belirtilen ayın menüsünü çeker ve DB'ye yazar.
     */
    public int syncCityMenu(int cityId, YearMonth yearMonth) {
        String cityName = CityHelper.getCityName(cityId);
        logger.info("Senkronize ediliyor: Şehir: {} (Plaka: {}), Ay: {}", cityName, cityId, yearMonth);

        List<Meal> meals = scraperService.scrapeMonthlyMenu(cityId, yearMonth);
        List<Meal> validMeals = meals.stream()
                .filter(MealValidator::isValidMeal)
                .toList();

        if (!validMeals.isEmpty()) {
            mealService.saveAllMeals(validMeals);
            logger.info("Başarıyla kaydedildi: {} için {} adet geçerli öğün", cityName, validMeals.size());
            return validMeals.size();
        } else {
            logger.warn("{} için geçerli menü verisi bulunamadı veya henüz yayınlanmamış.", cityName);
            return 0;
        }
    }

    /**
     * Kullanıcı yeni bir şehir seçtiğinde botu dondurmadan arka planda o şehri senkronize eder.
     */
    @Async
    public void syncCityMenuAsync(int cityId) {
        syncCityMenu(cityId, YearMonth.now());
    }

    public void fetchMealsForDateAndCity(LocalDate date, Integer cityId) {
        syncCityMenu(cityId, YearMonth.from(date));
    }
}
