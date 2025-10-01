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
}
