package com.kyk.mealtracker.repository;

import com.kyk.mealtracker.entity.BotUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    List<BotUser> findByNotificationsEnabledTrue();
}
