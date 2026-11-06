package com.kyk.mealtracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "bot_users")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BotUser {
    @Id
    private Long chatId;

    private String username;

    private boolean notificationsEnabled;

    private LocalDateTime lastInteractionDate;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Builder.Default
    @Column(name = "is_admin", nullable = false)
    private Boolean isAdmin = false;

    @Column(name = "last_activity_date")
    private LocalDateTime lastActivityDate;

    @Builder.Default
    @Column(name = "city_id")
    private Integer cityId = 1;

    @Builder.Default
    @Column(name = "breakfast_hour")
    private Integer breakfastHour = 7; // Varsayılan 07:00

    @Builder.Default
    @Column(name = "dinner_hour")
    private Integer dinnerHour = 15; // Varsayılan 15:00
}
