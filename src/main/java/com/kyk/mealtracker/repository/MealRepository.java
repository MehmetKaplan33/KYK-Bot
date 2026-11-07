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

    @org.springframework.data.jpa.repository.Query("""
        SELECT m FROM Meal m
        WHERE m.cityId = :cityId
          AND m.date >= :startDate
          AND (
            LOWER(m.first) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(m.second) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(m.third) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(m.fourth) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY m.date ASC
    """)
    List<Meal> searchMealsByKeyword(Integer cityId, LocalDate startDate, String keyword);
    void deleteByDate(LocalDate date);
    void deleteByDateAndCityId(LocalDate date, Integer cityId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT m.cityId) FROM Meal m WHERE m.date = :date")
    long countDistinctCitiesWithMealsOnDate(LocalDate date);
}
