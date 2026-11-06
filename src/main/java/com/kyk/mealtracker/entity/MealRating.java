package com.kyk.mealtracker.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "meal_ratings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_chat_id", "date", "meal_type", "city_id"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MealRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_chat_id", nullable = false)
    private Long userChatId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "meal_type", nullable = false)
    private Integer mealType; // 0: Kahvaltı, 1: Akşam

    @Column(name = "city_id", nullable = false)
    private Integer cityId;

    @Column(nullable = false)
    private Integer score; // 1: Kötüydü, 3: İdare eder, 5: Çok iyi
}

