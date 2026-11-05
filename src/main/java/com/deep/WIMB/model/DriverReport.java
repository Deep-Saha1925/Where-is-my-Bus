/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A report a driver sends to the admin: breakdown, road blockage / diversion,
 * new route or stop request, or anything else. The admin triages it through
 * the status workflow and can turn it into a public {@link Notice}.
 */
@Entity
@Table(name = "driver_report", indexes = {
        @Index(name = "idx_driver_report_status", columnList = "status"),
        @Index(name = "idx_driver_report_bus_created", columnList = "busNumber, createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DriverReport {

    public static final String BREAKDOWN = "BREAKDOWN";
    public static final String ROAD_BLOCKAGE = "ROAD_BLOCKAGE";
    public static final String ROUTE_REQUEST = "ROUTE_REQUEST";
    public static final String OTHER = "OTHER";

    public static final String NEW = "NEW";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String NEED_INFO = "NEED_INFO";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Taken from the verified driver token, never from the request body. */
    @Column(nullable = false, length = 40)
    private String busNumber;

    @Column(nullable = false, length = 20)
    private String type;

    /** Optional free text: where it happened / which route or stop is meant. */
    @Column(length = 150)
    private String place;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 20)
    private String status = NEW;

    /** Internal admin note. Not shown to the driver (drivers see status only). */
    @Column(length = 500)
    private String adminNote;

    /** Set when the admin turned this report into a public notice. */
    private Long publishedNoticeId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
