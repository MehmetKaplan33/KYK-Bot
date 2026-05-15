package com.kyk.mealtracker.repository;

import com.kyk.mealtracker.entity.BotUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    List<BotUser> findByNotificationsEnabledTrue();

    long countByNotificationsEnabledTrue();

    long countByLastActivityDateAfter(LocalDateTime date);

    long countByLastInteractionDateAfter(LocalDateTime date);

    List<BotUser> findByIsAdminTrue();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT b.cityId FROM BotUser b WHERE b.cityId IS NOT NULL")
    List<Integer> findDistinctCityIds();
}
