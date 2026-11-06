package com.kyk.mealtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "meals")
@Getter
@Setter
@EqualsAndHashCode(of = {"date", "mealType", "cityId"})
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

    @Column(name = "first_item")
    private String first;
    private String firstCalories;

    @Column(name = "second_item")
    private String second;
    private String secondCalories;

    @Column(name = "third_item")
    private String third;
    private String thirdCalories;

    @Column(name = "fourth_item")
    private String fourth;
    private String fourthCalories;

    private String totalCalories;
}