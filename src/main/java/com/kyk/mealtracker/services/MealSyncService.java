package com.kyk.mealtracker.services;

import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.BotUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealSyncService {

    private static final Logger logger = LoggerFactory.getLogger(MealSyncService.class);

    private final MealService mealService;
    private final RestTemplate restTemplate;
    private final BotUserRepository botUserRepository;

    public void fetchMealsForAllActiveCities() {
        LocalDate today = LocalDate.now();
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        List<Integer> activeCityIds = botUserRepository.findDistinctCityIds();
        if (activeCityIds.isEmpty()) {
            activeCityIds.add(1); // Default
        }

        logger.info("Manuel/Otomatik yemek kontrolü başlatılıyor... Tarih: {}, Aktif Şehirler: {}", today, activeCityIds);

        for (Integer cityId : activeCityIds) {
            for (LocalDate date = today; !date.isAfter(endOfMonth); date = date.plusDays(1)) {
                try {
                    checkAndSaveMeal(date, 0, cityId);
                    checkAndSaveMeal(date, 1, cityId);
                } catch (Exception e) {
                    logger.error("{} tarihli ve {} ID'li şehir için menü kontrolünde hata: {}", date, cityId, e.getMessage());
                }
            }
        }
        logger.info("Yemek kontrolü tamamlandı.");
    }

    public void fetchMealsForDateAndCity(LocalDate date, Integer cityId) {
        checkAndSaveMeal(date, 0, cityId);
        checkAndSaveMeal(date, 1, cityId);
    }

    private void checkAndSaveMeal(LocalDate date, int mealType, int cityId) {
        String url = String.format("https://kykyemekliste.com/yurt-tunnel/menu/liste?cityId=%d&mealType=%d", cityId, mealType);
        try {
            Meal[] meals = restTemplate.getForObject(url, Meal[].class);
            if (meals != null) {
                Arrays.stream(meals)
                        .filter(meal -> meal.getDate().equals(date))
                        .filter(this::isValidMeal)
                        .reduce((first, second) -> second)
                        .ifPresent(meal -> {
                            meal.setCityId(cityId); // Ensure cityId is set
                            mealService.saveMealIfNotExists(meal);
                        });
            }
        } catch (Exception e) {
            logger.error("API çağrısı hatası - URL: {}, Hata: {}", url, e.getMessage());
            throw e;
        }
    }

    private boolean isValidMeal(Meal meal) {
        if (meal == null) return false;

        String[] items = {
                meal.getFirst(),
                meal.getSecond(),
                meal.getThird(),
                meal.getFourth()
        };

        int validItemCount = 0;
        for (String item : items) {
            if (item == null || item.trim().isEmpty()) continue;

            String lowerItem = item.toLowerCase().trim();
            if (lowerItem.contains("@") || lowerItem.contains("mail")) return false;
            if (lowerItem.contains("gönderip") || lowerItem.contains("katkı sağla") ||
                lowerItem.contains("uygulamaya") || lowerItem.contains("listesini") ||
                lowerItem.contains("daha hızlı") || lowerItem.contains("girilmesine")) return false;

            if (item.trim().length() >= 3 && item.trim().length() <= 100) {
                validItemCount++;
            }
        }
        return validItemCount >= 3;
    }
}
