package com.deep.WIMB.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Location implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @JsonIgnore because Jackson serializing this field means walking every
    // getter on Ride (routeKey, status, bus, ...). Ride here is very often
    // an unloaded Hibernate proxy (see LocationService#addLocation, which
    // deliberately avoids a DB fetch on this hot path) -- Jackson calling
    // those getters would force exactly the DB round trip that was avoided,
    // just moved one step later (into JSON serialization, whether that's
    // the HTTP response, the JSON stored in Redis, or a WebSocket
    // broadcast). Still needed as a real JPA relation so the Location row
    // gets the correct foreign key when eventually persisted to Postgres --
    // just never serialized to JSON. rideId below is the JSON-safe stand-in
    // for anything that needs to know which ride this location belongs to.
    @JsonIgnore
    @ManyToOne
    private Ride ride;

    // Plain copy of ride.getId(), safe to serialize without touching the
    // Ride proxy at all (id-only access never triggers Hibernate to load
    // the rest of the row). This is what gets sent to the frontend and
    // stored in Redis instead of the full nested ride object. @Transient:
    // this is purely a transport convenience, never persisted -- the real
    // foreign key is (and stays) the `ride` relation above. Without
    // @Transient, Hibernate's default naming would collide: both `ride`
    // (-> ride_id) and a persisted `rideId` field would map to the exact
    // same "ride_id" column name.
    @Transient
    private Long rideId;

    private double latitude;
    private double longitude;
    private LocalDateTime timestamp;
}