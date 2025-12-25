# 🚌 Where Is My Bus (WIMB)

## 📌 Project Description

**Where Is My Bus (WIMB)** is a real-time bus tracking system designed
to solve the common problem of **uncertain bus timings and locations**.\
Passengers often wait without knowing whether a bus is nearby or
delayed. This project provides **live bus locations on a map**, helping
users make informed travel decisions.

------------------------------------------------------------------------

## ❓ Problem Statement

-   No real-time visibility of buses
-   Passengers depend on guesswork and delays
-   No centralized system to track active buses on a route

### ✅ Solution

WIMB provides: - Live bus tracking using GPS updates - Route-based bus
search (Source → Destination) - Real-time map updates using polling -
Clean, user-friendly UI

------------------------------------------------------------------------

## 🛠️ Technology Stack & Approach

### 🔹 Backend (Spring Boot)

-   Java + Spring Boot
-   REST APIs
-   JPA + Hibernate
-   MySQL
-   Handles ride lifecycle and GPS updates

### 🔹 Frontend

-   HTML, CSS, JavaScript
-   Leaflet.js for maps
-   Font Awesome for icons
-   OpenStreetMap tiles

### 🔹 Architecture

    Driver App → Location API → Database
    User UI → Active Ride API → Live Map

------------------------------------------------------------------------

## 🔗 Backend API Endpoints

### ▶ Start Ride

    POST /api/ride/start

``` json
{
  "busId": 1,
  "source": "C",
  "destination": "D",
  "latitude": 22.5726,
  "longitude": 88.3639
}
```

### ▶ Update Location (Driver GPS)

    POST /api/location/update

``` json
{
  "rideId": 5,
  "latitude": 22.5730,
  "longitude": 88.3642,
  "speed": 32.5
}
```

### ▶ Get Active Buses on Route

    GET /api/ride/active?source=C&destination=D

``` json
[
  {
    "rideId": 5,
    "busNumber": "WB-23A-1245",
    "source": "C",
    "destination": "D",
    "latitude": 22.5730,
    "longitude": 88.3642
  }
]
```

### ▶ Cancel Ride

    PUT /api/ride/cancel/{rideId}

------------------------------------------------------------------------

## 🖥️ Frontend Structure

    /frontend
    │
    ├── index.html   → Search buses by route
    ├── track.html   → Live tracking of selected bus

### ✔ Features

-   Route-based bus listing
-   Click to track individual bus
-   Live movement on map
-   Bus icons using Font Awesome
-   Automatic cleanup of inactive buses

------------------------------------------------------------------------

## 🚀 Future Enhancements

-   WebSocket-based real-time updates
-   ETA prediction
-   Driver mobile app
-   User authentication
-   Notifications & alerts

------------------------------------------------------------------------

## 👨‍💻 Author

**Deep Saha**\
Backend & System Design Enthusiast\
Java \| Spring Boot \| Maps \| Real-time Systems
