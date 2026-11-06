package com.kyk.mealtracker;

import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.services.KykYurtMenuScraperService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ScraperUnitTest {

    @Test
    public void testAdanaMonthlyMenuScraping() {
        KykYurtMenuScraperService scraper = new KykYurtMenuScraperService();
        List<Meal> meals = scraper.scrapeMonthlyMenu(1, YearMonth.of(2026, 11));

        assertNotNull(meals, "Yemek listesi null olmamalı");
        assertFalse(meals.isEmpty(), "Adana için Kasım 2026 menüsü çekilebilmeli");

        System.out.println("Çekilen toplam öğün: " + meals.size());
        meals.stream().limit(4).forEach(m -> {
            System.out.printf("Tarih: %s, Öğün: %s, 1: %s, 2: %s, 3: %s, 4: %s, Cal: %s%n",
                    m.getDate(),
                    m.getMealType() == 0 ? "Kahvaltı" : "Akşam",
                    m.getFirst(), m.getSecond(), m.getThird(), m.getFourth(),
                    m.getTotalCalories());
        });
    }
}

