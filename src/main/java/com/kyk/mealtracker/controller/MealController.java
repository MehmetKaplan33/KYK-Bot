package com.kyk.mealtracker.controller;

import com.kyk.mealtracker.services.MealSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MealController {

    private final MealSyncService mealSyncService;

    @Value("${admin.secret.token:kyk_admin_secret_token_123}")
    private String adminSecretToken;

    /**
     * Güvenli senkronizasyon endpoint'i: Artık şifresiz ve açık değil, admin token gerektirir.
     */
    @GetMapping("/api/admin/fetch-meals")
    public ResponseEntity<String> fetchMeals(@RequestParam(value = "token", required = false) String token) {
        if (token == null || !token.equals(adminSecretToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Yetkisiz erişim.");
        }

        try {
            mealSyncService.fetchMealsForAllActiveCities();
            return ResponseEntity.ok("Yemekler başarıyla senkronize edildi.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hata: " + e.getMessage());
        }
    }
}
