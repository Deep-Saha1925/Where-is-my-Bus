package com.deep.WIMB.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String routeCode;   // e.g. APD_FLK — the internal key used everywhere

    @Column(nullable = false)
    private String routeName;   // e.g. "Alipurduar ⇄ Falakata" — shown to admin/drivers

    @Column(nullable = false)
    private String filePath;    // e.g. data/routes/APD_FLK.xlsx

    private int stopCount;

    private LocalDateTime uploadedAt;
}