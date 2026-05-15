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

    private final com.kyk.mealtracker.services.MealSyncService mealSyncService;

    @GetMapping("/fetchMeals")
    public ResponseEntity<String> fetchMeals() {
        try {
            mealSyncService.fetchMealsForAllActiveCities();
            return ResponseEntity.ok("Yemekler başarıyla çekildi ve kaydedildi!");

        } catch (Exception e) {
            e.printStackTrace(); // Hata detayını görmek için
            return ResponseEntity.internalServerError()
                    .body("Veri çekme işlemi sırasında bir hata oluştu: " + e.getMessage());
        }
    }
}
