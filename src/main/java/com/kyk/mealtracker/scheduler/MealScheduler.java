package com.kyk.mealtracker.scheduler;

import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.services.MealService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class MealScheduler {

    private final MealService mealService;
    private final RestTemplate restTemplate;
    private static final Logger logger = LoggerFactory.getLogger(MealScheduler.class);

    @Scheduled(cron = "0 0 6 * * ?") // Her gün sabah 06:00'da çalışır
    public void fetchDailyMeals() {
        LocalDate today = LocalDate.now();
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        logger.info("Günlük yemek kontrolü başlatılıyor... Tarih: {}", today);

        // Bugünden ay sonuna kadar olan tarihleri kontrol et
        for (LocalDate date = today; !date.isAfter(endOfMonth); date = date.plusDays(1)) {
            try {
                // Kahvaltı menüsü
                checkAndSaveMeal(date, 0);
                // Akşam yemeği menüsü
                checkAndSaveMeal(date, 1);

                logger.info("{} tarihli menüler kontrol edildi.", date);
            } catch (Exception e) {
                logger.error("{} tarihli menü kontrolünde hata: {}", date, e.getMessage());
            }
        }

        logger.info("Günlük yemek kontrolü tamamlandı.");
    }

    private void checkAndSaveMeal(LocalDate date, int mealType) {
        String url = String.format("https://kykyemekliste.com/api/menu/liste?cityId=1&mealType=%d", mealType);
        try {
            Meal[] meals = restTemplate.getForObject(url, Meal[].class);

            if (meals != null) {
                Arrays.stream(meals)
                      .filter(meal -> meal.getDate().equals(date))
                      .findFirst()
                      .ifPresent(meal -> {
                          try {
                              mealService.saveMealIfNotExists(meal);
                              logger.info("Tarih: {}, Öğün: {} için menü kaydedildi.", date, mealType == 0 ? "Kahvaltı" : "Akşam Yemeği");
                          } catch (Exception e) {
                              logger.error("Menü kaydetme hatası - Tarih: {}, Öğün: {}, Hata: {}", date, mealType, e.getMessage());
                          }
                      });
            }
        } catch (Exception e) {
            logger.error("API çağrısı hatası - URL: {}, Hata: {}", url, e.getMessage());
            throw e;
        }
    }
}
