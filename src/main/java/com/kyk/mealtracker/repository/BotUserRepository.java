package com.kyk.mealtracker.repository;

import com.kyk.mealtracker.entity.BotUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    List<BotUser> findByNotificationsEnabledTrue();

    @org.springframework.data.jpa.repository.Query("SELECT b FROM BotUser b WHERE b.notificationsEnabled = true AND (b.breakfastHour = :breakfastHour OR (:breakfastHour = 7 AND b.breakfastHour IS NULL))")
    List<BotUser> findByNotificationsEnabledTrueAndBreakfastHour(Integer breakfastHour);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM BotUser b WHERE b.notificationsEnabled = true AND (b.dinnerHour = :dinnerHour OR (:dinnerHour = 15 AND b.dinnerHour IS NULL))")
    List<BotUser> findByNotificationsEnabledTrueAndDinnerHour(Integer dinnerHour);

    long countByNotificationsEnabledTrue();

    long countByLastActivityDateAfter(LocalDateTime date);

    long countByLastInteractionDateAfter(LocalDateTime date);

    List<BotUser> findByIsAdminTrue();

    java.util.Optional<BotUser> findFirstByUsernameIgnoreCase(String username);

    long countByIsAdminTrue();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT b.cityId FROM BotUser b WHERE b.cityId IS NOT NULL")
    List<Integer> findDistinctCityIds();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(b.cityId, 1), COUNT(b) FROM BotUser b GROUP BY COALESCE(b.cityId, 1) ORDER BY COUNT(b) DESC")
    List<Object[]> countUsersByCity();
}
