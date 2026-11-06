package com.kyk.mealtracker.util;

import com.kyk.mealtracker.entity.Meal;

public final class MealValidator {

    private MealValidator() {}

    public static boolean isValidMeal(Meal meal) {
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

            String lower = item.toLowerCase().trim();
            if (lower.contains("@") || lower.contains("mail") ||
                lower.contains("gönderip") || lower.contains("katkı sağla") ||
                lower.contains("uygulamaya") || lower.contains("listesini") ||
                lower.contains("daha hızlı") || lower.contains("girilmesine") ||
                lower.equalsIgnoreCase("veri yok")) {
                continue;
            }

            if (item.trim().length() >= 2 && item.trim().length() <= 120) {
                validItemCount++;
            }
        }
        return validItemCount >= 1;
    }
}

