package com.kyk.mealtracker.repository;

import com.kyk.mealtracker.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealRepository extends JpaRepository<Meal, Long> {
    Optional<Meal> findByDateAndMealTypeAndCityId(LocalDate date, Integer mealType, Integer cityId);
    void deleteByDateAndMealTypeAndCityId(LocalDate date, Integer mealType, Integer cityId);
    List<Meal> findByDate(LocalDate date);
    List<Meal> findByDateAndMealType(LocalDate date, Integer mealType);
    List<Meal> findByDateAndCityId(LocalDate date, Integer cityId);
    List<Meal> findByDateBetweenAndCityId(LocalDate startDate, LocalDate endDate, Integer cityId);
    void deleteByDate(LocalDate date);
    void deleteByDateAndCityId(LocalDate date, Integer cityId);
}
