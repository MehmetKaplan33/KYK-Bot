package com.kyk.mealtracker.controller;

import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.services.MealService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;

    @GetMapping("/fetchMeals")
    public ResponseEntity<String> fetchMeals() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            List<Meal> allMeals = new ArrayList<>();

            // Kahvaltı
            String breakfastUrl = "https://kykyemekliste.com/api/menu/liste?cityId=1&mealType=0";
            Meal[] breakfastMeals = restTemplate.getForObject(breakfastUrl, Meal[].class);
            if (breakfastMeals != null) {
                allMeals.addAll(Arrays.asList(breakfastMeals));
            }

            // Akşam yemeği
            String dinnerUrl = "https://kykyemekliste.com/api/menu/liste?cityId=1&mealType=1";
            Meal[] dinnerMeals = restTemplate.getForObject(dinnerUrl, Meal[].class);
            if (dinnerMeals != null) {
                allMeals.addAll(Arrays.asList(dinnerMeals));
            }

            if (allMeals.isEmpty()) {
                return ResponseEntity.badRequest().body("Yemek verileri alınamadı!");
            }

            mealService.saveAllMeals(allMeals);
            return ResponseEntity.ok("Yemekler başarıyla çekildi ve kaydedildi!");

        } catch (Exception e) {
            e.printStackTrace(); // Hata detayını görmek için
            return ResponseEntity.internalServerError()
                    .body("Veri çekme işlemi sırasında bir hata oluştu: " + e.getMessage());
        }
    }
}
