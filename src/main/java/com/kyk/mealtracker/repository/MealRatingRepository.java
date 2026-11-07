package com.kyk.mealtracker.repository;

import com.kyk.mealtracker.entity.MealRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface MealRatingRepository extends JpaRepository<MealRating, Long> {

    Optional<MealRating> findByUserChatIdAndDateAndMealTypeAndCityId(Long userChatId, LocalDate date, Integer mealType, Integer cityId);

    long countByDateAndMealTypeAndCityId(LocalDate date, Integer mealType, Integer cityId);

    @Query("SELECT AVG(r.score) FROM MealRating r WHERE r.date = :date AND r.mealType = :mealType AND r.cityId = :cityId")
    Double getAverageScore(LocalDate date, Integer mealType, Integer cityId);

    long countByDate(LocalDate date);

    @Query("SELECT AVG(r.score) FROM MealRating r WHERE r.date = :date")
    Double getOverallAverageScoreByDate(LocalDate date);

    @Query("SELECT AVG(r.score) FROM MealRating r")
    Double getOverallAverageScoreAllTime();
}

