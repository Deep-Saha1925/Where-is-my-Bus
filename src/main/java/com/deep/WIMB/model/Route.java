package com.deep.WIMB.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    private String filePath;    // legacy/informational only — kept for display, no longer read from

    // The actual Excel file content, stored directly in Postgres. This is the
    // source of truth for loading a route's stops — unlike a path on the
    // container's local disk, this survives every restart and redeploy,
    // since it lives in the (persistent, hosted) database along with
    // everything else about the route.
    // Note: intentionally NOT @Lob. On Hibernate 6 + Postgres, @Lob on a
    // byte[] defaults to the Large Object (oid/bigint) strategy, which
    // doesn't match a "bytea" column and causes an insert-time type
    // mismatch. @JdbcTypeCode(VARBINARY) maps this correctly to bytea.
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = true, columnDefinition = "bytea")
    private byte[] fileData;

    private int stopCount;

    private LocalDateTime uploadedAt;
}