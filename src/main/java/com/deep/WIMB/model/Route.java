package com.deep.WIMB.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // Optional, admin-entered list of bus numbers that run this route.
    // This is purely static/informational — completely separate from the
    // live Ride mechanism above. It exists so the "search buses between
    // depots" feature can answer "does a service exist here" even when
    // nothing is live right now. Buses can (and do) rotate day to day, so
    // this list is a best-effort roster, not a guarantee of what's running.
    @ElementCollection
    @CollectionTable(name = "route_buses", joinColumns = @JoinColumn(name = "route_id"))
    @Column(name = "bus_number")
    private List<String> busNumbers = new ArrayList<>();

    // Optional, admin-entered scheduled departure times for this route (e.g.
    // "06:00", "09:30"), stored as plain strings — no fixed schedule ID, no
    // per-day rules, no live-status blending. Every entry is assumed to run
    // daily. This powers the depot-to-depot timetable search: one result row
    // per departure time, same idea as a train timetable but deliberately
    // simpler.
    @ElementCollection
    @CollectionTable(name = "route_departures", joinColumns = @JoinColumn(name = "route_id"))
    @Column(name = "departure_time")
    private List<String> departureTimes = new ArrayList<>();
}