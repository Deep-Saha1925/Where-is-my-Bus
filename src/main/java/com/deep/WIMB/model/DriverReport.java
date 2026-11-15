/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A report sent to the admin by a driver (breakdown, road blockage / diversion,
 * new route or stop request, ...) or by a passenger (wrong bus location, bus not
 * running, staff behaviour, app problem, ...). The admin triages it through
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

    /** Who sent the report. Rows created before this column existed have null = DRIVER. */
    public static final String SOURCE_DRIVER = "DRIVER";
    public static final String SOURCE_PASSENGER = "PASSENGER";

    /** busNumber value for a passenger report that is not about one particular bus. */
    public static final String NO_BUS = "-";

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

    /** Internal admin note. NEVER shown to the driver (see adminReply for what the driver does see). */
    @Column(length = 500)
    private String adminNote;

    /**
     * Message the admin wrote FOR the driver: the answer to a question, what extra information is needed,
     * why a request was rejected... It is shown in the driver app next to the status. Only used on driver
     * reports; a passenger has no account, so nobody could read a reply on a passenger report.
     */
    @Column(length = 500)
    private String adminReply;

    /** When adminReply was last written or changed (null = no reply). The driver app uses it to flag "new". */
    private LocalDateTime repliedAt;

    /** Set when the admin turned this report into a public notice. */
    private Long publishedNoticeId;

    /** DRIVER or PASSENGER (null on old rows = DRIVER). */
    @Column(length = 20)
    private String source = SOURCE_DRIVER;

    /** Optional phone / email a passenger chose to leave. Admin-only, never sent to drivers. */
    @Column(length = 100)
    private String contact;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * createdAt / updatedAt are zone-less (stored in the server's own time zone, UTC on Render),
     * so a browser would read them as ITS local time and show the wrong clock time. These two
     * extra JSON fields carry the same moment as an ISO instant ending in "Z", which every
     * browser converts to the viewer's own time zone correctly.
     */
    @JsonProperty("createdAtInstant")
    public String createdAtInstant() { return toInstant(createdAt); }

    @JsonProperty("updatedAtInstant")
    public String updatedAtInstant() { return toInstant(updatedAt); }

    @JsonProperty("repliedAtInstant")
    public String repliedAtInstant() { return toInstant(repliedAt); }

    public static String toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toString();
    }
}