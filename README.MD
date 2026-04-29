<div align="center">

<img src="images/logo.png" width="120" alt="WIMB Logo"/>

<h1>Where Is My Bus</h1>
<h3>Real-time bus tracking for North Bengal</h3>


[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square&logo=mysql)](https://www.mysql.com)
[![Redis](https://img.shields.io/badge/Redis-7.x-red?style=flat-square&logo=redis)](https://redis.io)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-CSS-38bdf8?style=flat-square&logo=tailwindcss)](https://tailwindcss.com)
[![License](https://img.shields.io/badge/License-MIT-purple?style=flat-square)](LICENSE)

*Solving the problem of uncertain bus timings in semi-urban and rural regions*

[Features](#-features) · [Tech Stack](#-tech-stack) · [Screenshots](#-screenshots) · [Setup](#-setup--run) · [API Docs](#-api-endpoints) · [Author](#-author)

</div>

---

## 📖 About

**Where Is My Bus (WIMB)** is a real-time, route-based bus tracking system built for North Bengal. Unlike traditional systems, WIMB uses a **hybrid approach** combining live GPS data with Excel-driven static route files — making it practical, offline-friendly, and maintainable by non-technical staff.

> No app install needed. Works in any browser. Designed for low-connectivity areas.

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🧑‍💼 For Passengers
- 🔍 Search buses by source & destination
- 🕐 Recent searches with quick results
- 📍 Live route timeline (like "Where is my Train")
- 🚌 Animated bus icon on current stop
- ⏱️ ETA per stop from bus position
- 🌙 Dark mode support

</td>
<td width="50%">

### 🚗 For Drivers
- 🔑 Login with bus number
- ↩️ Resume active ride after refresh
- 🗺️ GPS coordinates from Excel (no GPS needed at start)
- 📡 Live location sent every GPS tick

</td>
</tr>
<tr>
<td width="50%">

### 🛡️ For Admins
- 🔐 Secure login (Spring Security)
- 📊 Live card grid of all active buses
- 🔎 Search by bus number, source or destination
- 🕐 Recent searches saved
- 🔄 Auto-refresh every 5 seconds

</td>
<td width="50%">

### ⚙️ System Highlights
- 📊 Excel = single source of truth for routes
- 🔁 Auto forward & reverse direction detection
- ❌ No false bus visibility after source stop
- 🗂️ JSON auto-synced from Excel on startup
- 🔐 Admin auth via config file, no DB needed
- ⚡ Redis buffer for high-frequency GPS writes
- 🔄 Auto batch flush Redis → MySQL every 60s

</td>
</tr>
</table>

---

## 🛠️ Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Core language |
| Spring Boot | 3.x | REST API framework |
| Spring Security | 6.x | Admin authentication |
| Apache POI | 5.x | Excel route file processing |
| MySQL | 8.0 | Persistent ride & location storage |
| Redis | 7.x | High-frequency GPS location buffer |
| Jackson | 2.x | JSON sync for stop dropdowns |

### Frontend
| Technology | Purpose |
|---|---|
| HTML5 | Page structure |
| Tailwind CSS | Styling & dark mode |
| Vanilla JavaScript | All interactivity |
| Font Awesome | Icons |

---

## 📸 Screenshots

<table>
<tr>
<td align="center" width="50%">
<img src="images/dashboard.png" width="100%"/>
<b>🏠 Passenger Search</b>
</td>
<td align="center" width="50%">
<img src="images/live-tracking.png" width="100%"/>
<b>🗺️ Live Route Timeline</b>
</td>
</tr>
<tr>
<td align="center" width="50%">
<img src="images/start-ride.png" width="100%"/>
<b>🚗 Driver Panel</b>
</td>
<td align="center" width="50%">
<img src="images/admin-panel.png" width="100%"/>
<b>🛡️ Admin Panel</b>
</td>
</tr>
</table>

---

## 🧱 Architecture
```
┌─────────────────────────────────────────────────────────┐
│                      DRIVER APP                         │
│              GPS coordinates every tick                 │
└────────────────────────┬────────────────────────────────┘
                         │ POST /api/ride/start
                         │ POST /api/ride/location
                         ▼
┌─────────────────────────────────────────────────────────┐
│              SPRING BOOT BACKEND                        │
│                                                         │
│   RideController  ──►  RideService                      │
│   LocationController ─► LocationService                 │
│   RouteController ──►  RouteExcelLoader ─► Cache        │
│                              │                          │
│                              ▼                          │
│                     RedisLocationService                 │
│                        │         │                      │
│               Write fast│         │Read latest          │
│                        ▼         ▼                      │
│                    ┌────────────────┐                   │
│                    │     REDIS      │  ← GPS buffer     │
│                    │ location:ride: │                   │
│                    └───────┬────────┘                   │
│                            │ Flush every 60s            │
│                            │ or on ride end             │
│                            ▼                            │
│                    ┌────────────────┐                   │
│                    │     MYSQL      │  ← Persistent     │
│                    └────────────────┘                   │
└────────────┬────────────────────────────────────────────┘
             │
             │ GET last-loc / active rides
             ▼
┌────────────────────┐   ┌───────────────────────────────┐
│   PASSENGER UI     │   │        EXCEL FILE             │
│                    │   │                               │
│  Route Timeline    │   │  stop_name, lat, lng,         │
│  ETA per stop      │   │  distance_km, slack_min       │
│  Live bus icon     │   │                               │
└────────────────────┘   └───────────────────────────────┘

                    ADMIN ACCESS
                         │
                         ▼
              Spring Security Filter
                         │
              InMemoryUserDetailsManager
              (credentials from .properties)
                         │
                         ▼
                 /admin-buses.html
```

---

## ⚡ Redis Integration

WIMB uses Redis as a **high-speed write buffer** for GPS location updates.

### Why Redis?
GPS updates arrive every few seconds per active ride. Writing each update directly to MySQL causes high I/O and slow response times. Redis solves this by acting as an in-memory buffer.

### How it works

```
GPS Update (every ~3s)
        ↓
   Redis List (instant ~0.1ms write)
   key: location:ride:{rideId}
        ↓
   Scheduler (every 60s)  ──OR──  Ride ends (immediate flush)
        ↓
      MySQL (batch saveAll)
```

### Performance Comparison

| Aspect | Without Redis | With Redis |
|---|---|---|
| Write speed | ~5–10ms (MySQL) | ~0.1ms (Redis) |
| DB load | Every 3s hit | Batched every 60s |
| Live tracking | Query DB each time | Read from Redis cache |
| Data safety | Always persisted | Flushed on ride end |

### Redis Key Structure
```
location:ride:1   →  List of JSON location objects
location:ride:2   →  List of JSON location objects
```

### Flush Strategy
- **Scheduled flush** — `LocationFlushScheduler` runs every 60 seconds, drains all Redis keys to MySQL
- **Ride end flush** — When a ride is cancelled or a new ride starts for the same bus, remaining Redis data is immediately flushed so no location data is ever lost

---

## 📂 Route Data Management

Routes are managed entirely through **Excel files** — no code changes needed.
```
src/main/resources/routes/route_APD_FLK.xlsx
```

### Excel Format

| stop_order | stop_name | latitude | longitude | distance_km | slack_min |
|:---:|---|:---:|:---:|:---:|:---:|
| 1 | Alipurduar | 26.4799 | 89.5355 | 0 | 0 |
| 2 | Alipurduar Chowpathy | 26.48083 | 89.526 | 1.5 | 2 |
| 3 | Sonapur | 26.494 | 89.368 | 10.5 | 2 |
| 4 | Falakata | 26.51721 | 89.20694 | 15 | 0 |

> ✅ Loaded once at startup · ✅ No DB needed · ✅ Auto-syncs `stops.json`

---

## 🔐 Admin Security

Admin panel is protected by **Spring Security** using `InMemoryUserDetailsManager`.  
**No database required** — credentials live in `application.properties`.
```properties
wimb.admin.username=admin
wimb.admin.password=wimb@2025
```

> 💡 To change credentials, update `application.properties` and restart. No code changes needed.

### Access Control

| URL | Who can access |
|---|:---:|
| `/index.html` `/track.html` `/driver.html` | 🌐 Everyone |
| `/api/ride/active` `/api/ride/start` `/api/location/**` `/api/routes/**` | 🌐 Everyone |
| `/login.html` | 🌐 Everyone |
| `/admin-buses.html` | 🔐 Admin only |
| `/api/ride/active/all` | 🔐 Admin only |

---

## 🔄 Direction Logic
```
sourceOrder < destinationOrder  →  FORWARD  ▶
sourceOrder > destinationOrder  →  BACKWARD ◀
```

A bus is shown to a passenger **only if it has NOT yet crossed their source stop** — computed by comparing the bus's position on the **full route**, not just the searched segment.

---

## 🔗 API Endpoints

<details>
<summary><b>🚌 Ride APIs</b></summary>

#### Start Ride
```http
POST /api/ride/start
Content-Type: application/json

{
  "busNumber": "WB-23A-1245",
  "routeKey": "ALIPURDUAR_FALAKATA",
  "latitude": 26.4799,
  "longitude": 89.5355
}
```

#### Update Location
```http
POST /api/ride/location
Content-Type: application/json

{
  "rideId": 5,
  "latitude": 26.494,
  "longitude": 89.368,
  "accuracy": 12.5
}
```

#### Get Active Buses on Route
```http
GET /api/ride/active?source=ALIPURDUAR&destination=FALAKATA
```

#### Get All Active Buses *(Admin)*
```http
GET /api/ride/active/all
```

#### Cancel Ride
```http
PUT /api/ride/cancel/{rideId}
```

</details>

<details>
<summary><b>📍 Location APIs</b></summary>

#### Get Last Known Location
```http
GET /api/location/last-loc/{rideId}
```
> ⚡ Returns from Redis if ride is active, falls back to MySQL if not buffered.

</details>

<details>
<summary><b>🗺️ Route APIs</b></summary>

#### Get Route Stops
```http
GET /api/routes?source=ALIPURDUAR&destination=FALAKATA
```

</details>

---

## 🖥️ Frontend Pages

| Page | Who uses it | Description |
|---|---|---|
| `index.html` | Passenger | Search buses, recent searches, quick results |
| `track.html` | Passenger | Live route timeline, ETA, bus position |
| `driver.html` | Driver | Start/resume ride, live GPS updates |
| `login.html` | Admin | Secure login form |
| `admin-buses.html` | Admin | All active buses, search, live stats |

---

## 🗂️ Project Structure
```
WIMB/
├── src/main/java/com/deep/WIMB/
│   ├── config/
│   │   ├── SecurityConfig.java          # Spring Security rules
│   │   └── RedisConfig.java             # Redis template config
│   ├── controller/
│   │   ├── RideController.java
│   │   ├── LocationController.java
│   │   ├── RouteController.java
│   │   ├── DriverController.java
│   │   └── CsrfController.java
│   ├── service/
│   │   ├── RideService.java
│   │   ├── LocationService.java
│   │   ├── RedisLocationService.java    # Redis read/write/clear
│   │   └── RouteExcelLoader.java        # Loads Excel on startup
│   ├── scheduler/
│   │   └── LocationFlushScheduler.java  # Batch flush Redis → MySQL
│   ├── model/
│   │   ├── Ride.java
│   │   └── Location.java               # implements Serializable
│   └── dto/
│       ├── StartRideRequest.java
│       ├── LocationUpdateRequest.java
│       ├── ActiveRideResponse.java
│       └── RouteStop.java
│
├── src/main/resources/
│   ├── static/
│   │   ├── index.html                   # Passenger search
│   │   ├── track.html                   # Route timeline
│   │   ├── driver.html                  # Driver panel
│   │   ├── login.html                   # Admin login
│   │   ├── admin-buses.html             # Admin panel
│   │   ├── js/
│   │   │   ├── index.js
│   │   │   ├── track.js
│   │   │   ├── driver.js
│   │   │   └── admin-buses.js
│   │   └── data/
│   │       └── stops.json               # Auto-generated from Excel
│   ├── routes/
│   │   └── route_APD_FLK.xlsx           # Route master data
│   └── application.properties
└── pom.xml
```

---

## ⚙️ Setup & Run

### Prerequisites

- Java 17+
- MySQL 8+
- Maven 3.8+
- Redis 7+

### Steps
```bash
# 1. Clone the repository
git clone https://github.com/Deep-Saha1925/Where-is-my-Bus.git
cd Where-is-my-Bus
```
```bash
# 2. Install and start Redis (Ubuntu)
sudo apt install redis-server -y
sudo systemctl start redis
sudo systemctl enable redis

# Verify Redis is running
redis-cli ping   # should return PONG
```
```bash
# 3. Create MySQL database
mysql -u root -p
```
```sql
CREATE DATABASE wimb;
EXIT;
```
```bash
# 4. Configure application.properties
```
```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/wimb
spring.datasource.username=root
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000ms

# Admin credentials
wimb.admin.username=admin
wimb.admin.password=wimb@2025
```
```bash
# 5. Build and run
./mvnw spring-boot:run
```
```bash
# 6. Open in browser
open http://localhost:8080             # Passenger view
open http://localhost:8080/login.html  # Admin login
```

### Verify Redis is working
```bash
# After starting a ride, check Redis
redis-cli
KEYS *                          # should show location:ride:1 etc.
LLEN location:ride:1            # number of buffered locations
LRANGE location:ride:1 0 -1     # all buffered location JSON
```

---

## 🚀 Future Enhancements

- [ ] WebSocket live tracking (replace 3s polling)
- [ ] Admin Excel upload panel (no restart needed)
- [ ] Push notifications when bus is nearby
- [ ] Multi-route support per Excel file
- [ ] Driver authentication with OTP
- [ ] Passenger seat booking
- [ ] Analytics dashboard for operators
- [ ] Mobile app (Android)

---

## 👨‍💻 Author

<div align="center">

**Deep Saha**

*Backend & System Design Enthusiast*

Java · Spring Boot · Real-Time Systems · North Bengal

[![GitHub](https://img.shields.io/badge/GitHub-Deep--Saha1925-181717?style=for-the-badge&logo=github)](https://github.com/Deep-Saha1925)

---

*Built with ❤️ for North Bengal*

</div>