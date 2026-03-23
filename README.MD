# 🚌 Where Is My Bus (WIMB)

## 📌 Project Description

**Where Is My Bus (WIMB)** is a real-time, route-based bus tracking system designed to solve the problem of **uncertain bus timings and locations**, especially in semi-urban and rural regions like North Bengal.

Unlike traditional systems that rely only on databases, WIMB uses a **hybrid approach**:
- **Dynamic data** — live bus location, active rides
- **Static route data** — stops, distance, halt times loaded from **Excel files**
- **Frontend dropdown data** — synchronized automatically via **JSON**

This design makes the system **realistic, scalable, and easy to maintain** for government or private transport systems.

---

## ❓ Problem Statement

- Passengers wait without knowing the exact bus position
- No clarity on whether a bus has already crossed a stop
- Static routes are hardcoded or poorly managed
- No forward / reverse route intelligence
- Drivers have no simple way to start or resume a journey

---

## ✅ Solution Overview

WIMB provides:

- 🔴 **Live bus tracking** — real-time position updated every 3 seconds
- 🗺️ **Excel-driven route visualization** — full stop timeline, no map required
- 🔁 **Automatic forward & reverse direction handling**
- 🔎 **Source → Destination based bus search** with ETA
- 📍 **Autocomplete dropdown** based stop selection (real-world UX)
- ❌ **No false bus visibility** after crossing passenger's source stop
- 🕐 **Recent searches** — passengers see quick results on return visits
- 🌙 **Dark mode** across all pages, persisted across sessions
- 🚗 **Driver panel** — resume active ride or start new ride by bus number
- 🛡️ **Admin panel** — live map of all active buses with search

---

## 🛠️ Technology Stack

### 🔹 Backend
| Technology | Purpose |
|---|---|
| Java 17 | Core language |
| Spring Boot | REST API framework |
| Apache POI | Excel route file processing |
| MySQL | Live ride & location storage |
| Jackson | JSON sync for stop dropdowns |

### 🔹 Frontend
| Technology | Purpose |
|---|---|
| HTML + Tailwind CSS | UI layout and styling |
| Vanilla JavaScript | All interactivity |
| Font Awesome | Icons |

---

## 📸 Application Screenshots

### 🏠 Passenger Search
<img src="images/dashboard.png" width="600"/>

### 🗺 Live Bus Tracking (Timeline UI)
<img src="images/live-tracking.png" width="600"/>

### 🚗 Driver Panel
<img src="images/driver-panel.png" width="600"/>

### 🛡️ Admin Panel
<img src="images/admin-panel.png" width="600"/>

---

## 🧱 Architecture
```
Driver App (GPS)
      ↓
POST /api/ride/start  →  POST /api/ride/location
      ↓
MySQL (Active Rides + Locations)
      ↓
GET /api/location/last-loc/{rideId}
      ↓
Passenger UI (Route Timeline)

Excel Route File (.xlsx)
      ↓
RouteExcelLoader (@PostConstruct)
      ↓
In-Memory Route Cache (HashMap)
      ↓
GET /api/routes?source=X&destination=Y
      ↓
Timeline + ETA Calculation
```

---

## 📂 Route Data Management

### 📘 Excel — Single Source of Truth
```
src/main/resources/routes/route_APD_FLK.xlsx
```

### 📑 Excel Format

| stop_order | stop_name | latitude | longitude | distance_km | slack_min |
|---|---|---|---|---|---|
| 1 | Alipurduar | 26.4799 | 89.5355 | 0 | 0 |
| 2 | Alipurduar Chowpathy | 26.48083 | 89.526 | 1.5 | 2 |
| 3 | Sonapur | 26.494 | 89.368 | 10.5 | 2 |
| 4 | Falakata | 26.51721 | 89.20694 | 15 | 0 |

✔ Loaded once at application startup via `@PostConstruct`  
✔ No database required for route data  
✔ Easy to update by non-technical staff  
✔ Automatically syncs stop names to `data/stops.json` for frontend dropdowns

---

### 📗 JSON — Frontend Dropdown Sync
```
src/main/resources/static/data/stops.json
```

- Auto-generated from Excel `stop_name` column on every startup
- Powers the autocomplete dropdowns on all pages
- Prevents spelling errors in source/destination input

---

## 🔄 Direction Logic

Direction is determined automatically from stop order:

| Condition | Direction |
|---|---|
| `sourceOrder < destinationOrder` | FORWARD |
| `sourceOrder > destinationOrder` | BACKWARD |

### Passenger Visibility Rule

A bus is shown to a passenger **only if it has NOT yet crossed their source stop**.  
This is computed by comparing the bus's current stop index against the passenger's source index on the **full route**, not just the searched segment.

✔ Forward-safe ✔ Reverse-safe ✔ Real-world accurate

---

## 🔗 Backend API Endpoints

### ▶ Start Ride
```
POST /api/ride/start
```
```json
{
  "busNumber": "WB-23A-1245",
  "routeKey": "ALIPURDUAR_FALAKATA",
  "latitude": 26.4799,
  "longitude": 89.5355
}
```

### ▶ Update Location (Driver GPS)
```
POST /api/ride/location
```
```json
{
  "rideId": 5,
  "latitude": 26.494,
  "longitude": 89.368,
  "accuracy": 12.5
}
```

### ▶ Get Last Known Location
```
GET /api/location/last-loc/{rideId}
```

### ▶ Get Active Buses on Route
```
GET /api/ride/active?source=ALIPURDUAR&destination=FALAKATA
```

### ▶ Get All Active Buses
```
GET /api/ride/active/all
```

### ▶ Get Route Stops
```
GET /api/routes?source=ALIPURDUAR&destination=FALAKATA
```

### ▶ Cancel Ride
```
PUT /api/ride/cancel/{rideId}
```

---

## 🖥️ Frontend Pages

### `index.html` — Passenger Search
- Search by source & destination with autocomplete
- Recent searches saved to localStorage — quick results on return
- Live bus cards with ETA shown instantly
- Click **Track Bus** to open the timeline

### `track.html` — Live Route Timeline
- Full route displayed as a vertical stop timeline (like "Where is my Train")
- Passed stops grayed out, current stop highlighted with animated 🚌 icon
- ETA shown per stop from bus's current position
- Yellow banner when bus hasn't reached passenger's boarding stop yet
- `📍 Your stop` and `🏁 Your dest` markers within the full route
- Dark mode, live refresh every 3 seconds

### `driver.html` — Driver Panel
- Enter bus number → check for active ride
- If active ride found: resume and end it
- If no active ride: start new ride with source/destination
- GPS coordinates fetched from Excel route data (no GPS dependency at start)
- Live location updates sent every GPS tick

### `admin-buses.html` — Admin Panel
- Card grid of all active buses
- Live stats: bus count, unique routes
- Search by bus number, source or destination
- Recent admin searches saved to localStorage
- Auto-refreshes every 5 seconds
- Click **View on Map** to track any bus

---

## ✨ Key Highlights

- **No map dependency** on passenger side — timeline UI is faster and clearer
- **Excel = master data** — routes updated without touching code
- **JSON auto-synced** from Excel on every startup
- **Direction-aware filtering** — works for both forward and reverse journeys
- **Full route always shown** — passengers see where the bus is even before it reaches them
- **Resume ride** — drivers can refresh page without losing their active ride
- **Consistent dark mode** — shared across all pages via localStorage
- **Designed for real transport systems** in low-connectivity areas

---

## 🚀 Future Enhancements

- [ ] WebSocket live tracking (replace polling)
- [ ] Admin Excel upload panel (no restart needed)
- [ ] Push notifications when bus is nearby
- [ ] Multi-route support per Excel file
- [ ] Driver authentication
- [ ] Passenger seat booking
- [ ] Analytics dashboard for operators

---

## 🗂️ Project Structure
```
WIMB/
├── src/main/java/com/deep/WIMB/
│   ├── controller/
│   │   ├── RideController.java
│   │   ├── LocationController.java
│   │   ├── RouteController.java
│   │   └── DriverController.java
│   ├── service/
│   │   ├── RideService.java
│   │   ├── LocationService.java
│   │   └── RouteExcelLoader.java
│   ├── model/
│   │   ├── Ride.java
│   │   └── Location.java
│   └── dto/
│       ├── StartRideRequest.java
│       ├── ActiveRideResponse.java
│       └── RouteStop.java
├── src/main/resources/
│   ├── static/
│   │   ├── index.html
│   │   ├── track.html
│   │   ├── driver.html
│   │   ├── admin-buses.html
│   │   ├── js/
│   │   │   ├── index.js
│   │   │   ├── track.js
│   │   │   ├── driver.js
│   │   │   └── admin-buses.js
│   │   └── data/
│   │       └── stops.json
│   ├── routes/
│   │   └── route_APD_FLK.xlsx
│   └── application.properties
└── pom.xml
```

---

## ⚙️ Setup & Run

### Prerequisites
- Java 17+
- MySQL 8+
- Maven

### Steps
```bash
# 1. Clone the repository
git clone https://github.com/Deep-Saha1925/Where-is-my-Bus.git
cd Where-is-my-Bus

# 2. Create MySQL database
mysql -u root -p
CREATE DATABASE wimb;

# 3. Configure application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/wimb
spring.datasource.username=root
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update

# 4. Run the application
./mvnw spring-boot:run

# 5. Open in browser
http://localhost:8080
```

---

## 👨‍💻 Author

**Deep Saha**  
Backend & System Design Enthusiast  
Java | Spring Boot | Real-Time Systems | North Bengal

[![GitHub](https://img.shields.io/badge/GitHub-Deep--Saha1925-181717?style=flat&logo=github)](https://github.com/Deep-Saha1925)