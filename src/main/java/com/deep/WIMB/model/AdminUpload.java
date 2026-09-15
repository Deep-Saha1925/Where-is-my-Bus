package com.deep.WIMB.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Keeps the most recently uploaded admin spreadsheet around so it can be
 * downloaded again later. One row per {@link #kind} — each new upload of the
 * same kind overwrites the previous one, so this table never grows.
 *
 * Stored in Postgres rather than on disk for the same reason Route.fileData
 * is: the container's local disk doesn't survive a restart or redeploy.
 */
@Entity
@Table(name = "admin_upload")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpload {

    /** Logical slot, e.g. "departures". Unique — one stored file per kind. */
    public static final String KIND_DEPARTURES = "departures";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String kind;

    @Column(nullable = false)
    private String fileName;

    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] fileData;

    private LocalDateTime uploadedAt;

    /** How many usable rows were parsed out of this file, for the admin UI. */
    private int rowCount;

    /** Short human summary, e.g. "3 routes updated, 1 code skipped". */
    @Column(length = 500)
    private String summary;
}
