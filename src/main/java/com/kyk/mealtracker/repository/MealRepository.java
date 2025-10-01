package com.kyk.mealtracker.repository;

import com.kyk.mealtracker.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MealRepository extends JpaRepository<Meal, Long> {
    Optional<Meal> findByDateAndMealTypeAndCityId(LocalDate date, Integer mealType, Integer cityId);
    void deleteByDateAndMealTypeAndCityId(LocalDate date, Integer mealType, Integer cityId);
}
