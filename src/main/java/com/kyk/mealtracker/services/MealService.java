package com.kyk.mealtracker.services;

import com.kyk.mealtracker.entity.Meal;
import com.kyk.mealtracker.repository.MealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealService {

    private final MealRepository mealRepository;

    @Transactional
    public void saveAllMeals(List<Meal> meals) {
        for (Meal meal : meals) {
            try {
                mealRepository.findByDateAndMealTypeAndCityId(meal.getDate(), meal.getMealType(), meal.getCityId())
                        .ifPresentOrElse(
                                existingMeal -> {
                                    // ID'yi koruyarak güncelleme yap
                                    meal.setId(existingMeal.getId());
                                    mealRepository.save(meal);
                                },
                                () -> {
                                    // Yeni kayıt ekle
                                    meal.setId(null); // ID'yi null yaparak yeni kayıt oluştur
                                    mealRepository.save(meal);
                                }
                        );
            } catch (Exception e) {
                e.printStackTrace();
                // Bir kayıt hata verirse diğerlerine devam et
                continue;
            }
        }
    }

    @Transactional
    public void saveMealIfNotExists(Meal meal) {
        mealRepository.findByDateAndMealTypeAndCityId(meal.getDate(), meal.getMealType(), meal.getCityId())
                .ifPresentOrElse(
                        existingMeal -> {
                            // Sadece mevcut kayıt yoksa veya değişiklik varsa güncelle
                            if (!existingMeal.equals(meal)) {
                                meal.setId(existingMeal.getId());
                                mealRepository.save(meal);
                            }
                        },
                        () -> {
                            meal.setId(null);
                            mealRepository.save(meal);
                        }
                );
    }

    public List<Meal> getMealsByDate(LocalDate date) {
        return mealRepository.findByDate(date);
    }

    public List<Meal> getMealsByDateAndType(LocalDate date, Integer mealType) {
        return mealRepository.findByDateAndMealType(date, mealType);
    }
}
