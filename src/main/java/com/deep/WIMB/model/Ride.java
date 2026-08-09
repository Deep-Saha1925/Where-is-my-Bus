package com.deep.WIMB.model;

import com.deep.WIMB.enums.RideStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String routeKey;

    // Which registered route (Route.routeCode) this ride actually runs on.
    // Null on rows created before multi-route support existed — treat null
    // as the legacy route everywhere this is read.
    private String routeCode;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private RideStatus status;

    @ManyToOne
    private Bus bus;
}