package com.kyk.mealtracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "meals")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Meal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer mealType;

    @Column(nullable = false)
    private Integer cityId;

    @Column(length = 100)
    private String first;
    private String firstCalories;

    @Column(length = 100)
    private String second;
    private String secondCalories;

    @Column(length = 100)
    private String third;
    private String thirdCalories;

    @Column(length = 100)
    private String fourth;
    private String fourthCalories;

    private Integer totalCalories;
}
