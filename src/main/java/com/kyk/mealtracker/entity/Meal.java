package com.kyk.mealtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "meals")
@Getter // Data yerine daha güvenli olan Getter/Setter kullanabilirsin
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Meal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    private Integer mealType;
    private Integer cityId;

    private String first;
    private String firstCalories;

    private String second;
    private String secondCalories;

    private String third;
    private String thirdCalories;

    private String fourth;
    private String fourthCalories;

    private String totalCalories;
}