<div align="center">

<img src="images/logo.png" width="120" alt="WIMB Logo"/>

<h1>Where Is My Bus</h1>
<h3>Real-time bus tracking for North Bengal</h3>


[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=java)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-4169E1?style=flat-square&logo=postgresql)](https://neon.tech)
[![Redis](https://img.shields.io/badge/Redis-Upstash-red?style=flat-square&logo=redis)](https://upstash.com)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3.x-005F0F?style=flat-square&logo=thymeleaf)](https://www.thymeleaf.org)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-CSS-38bdf8?style=flat-square&logo=tailwindcss)](https://tailwindcss.com)
[![License](https://img.shields.io/badge/License-MIT-purple?style=flat-square)](LICENSE)

*Solving the problem of uncertain bus timings in semi-urban and rural regions*

[Features](#-features) · [Tech Stack](#-tech-stack) · [Screenshots](#-screenshots) · [Setup](#-setup--run) · [API Docs](#-api-endpoints) · [Author](#-author)

</div>

---

## 📖 About

**Where Is My Bus (WIMB)** is a real-time, route-based bus tracking system built for North Bengal. Unlike traditional systems, WIMB uses a **hybrid approach** combining live GPS data with Excel-driven route and access-control files — making it practical, offline-friendly, and maintainable by non-technical staff, without needing anyone to touch code.

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
- 🏢 Pick your depot + enter your depot's code (no personal login needed)
- 🔐 Code verified once per shift, then remembered on that device
- 🛣️ Pick your Route first — Start Location / Destination are then scoped to just that route's stops, not a global list
- ↩️ Resume active ride after refresh
- 🗺️ GPS coordinates from Excel (no GPS needed at start)
- 📡 Live location sent every GPS tick, protected by a per-bus driver token

</td>
</tr>
<tr>
<td width="50%">

### 🛡️ For Admins
- 🔐 Secure login (Spring Security)
- 📊 Live card grid of all active buses
- 🔎 Search by bus number, source or destination
- 🔄 Auto-refresh every 5 seconds
- 🏢 **Depot Codes tab** — upload an Excel file to add/update every depot's driver code, no restart needed
- 🛣️ **Routes tab** — add new routes via Excel upload, rename a route and/or replace its stops, or delete one entirely — all without a restart
- 📄 Built-in "download example template" for both depot codes and routes, so the file format is never a guessing game

</td>
<td width="50%">

### ⚙️ System Highlights
- 📊 Excel = single source of truth for routes **and** depot codes
- 🛣️ Multiple independent routes can coexist — each with its own stop list, own name, own code
- 🔁 Auto forward & reverse direction detection
- ❌ No false bus visibility after source stop
- 🗂️ JSON auto-synced from Excel on startup
- 🔐 Admin auth via config file, no DB needed
- 🔑 Driver actions protected by a short-lived, per-bus token — not just an open endpoint
- ⚡ Redis buffer for high-frequency GPS writes
- 🔄 Auto batch flush Redis → PostgreSQL every 60s

</td>
</tr>
</table>

---

## 🛠️ Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Core language |
| Spring Boot | 4.x | REST API framework |
| Spring Security | 6.x | Admin authentication |
| Thymeleaf | 3.x | Server-rendered views for `driver.html`, `admin.html`, `admin-buses.html` |
| Apache POI | 5.x | Excel processing (routes **and** depot codes, via `WorkbookFactory` — accepts `.xlsx`, `.xls`, `.xlsm`) |
| PostgreSQL (Neon, hosted) | 16+ | Persistent ride, location & route storage |
| Redis (Upstash, hosted) | — | High-frequency GPS location buffer + driver token store |
| Jackson | 2.x | JSON sync for stop dropdowns |

> ⚠️ Both the database and Redis connection strings live in `application.properties` — make sure real credentials aren't committed to a public repo. Use environment variables or a secrets manager for anything beyond local dev.

### Frontend
| Technology | Purpose |
|---|---|
| HTML5 | Page structure |
| Tailwind CSS | Layout utilities & dark mode |
| Google Fonts (Oswald, Inter, JetBrains Mono) | Brand type system — Oswald for headers/labels, Inter for body text, JetBrains Mono for IDs/codes/data |
| Vanilla JavaScript | All interactivity |
| Font Awesome | Icons (admin map view) |
| Leaflet.js | Live map view of all active buses |

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
│    Depot + code verified once  →  X-Driver-Token issued │
│         Route picked  →  source/destination scoped to it │
│         GPS coordinates sent every tick, with token      │
└────────────────────────┬────────────────────────────────┘
                         │ POST /api/driver/verify (once)
                         │ GET  /api/routes/list, /api/routes/stops
                         │ POST /api/ride/start   (X-Driver-Token)
                         │ POST /api/ride/location (X-Driver-Token)
                         ▼
┌─────────────────────────────────────────────────────────┐
│              SPRING BOOT BACKEND                        │
│                                                         │
│   DriverAuthController ─► DriverAccessService (depot codes)│
│                        └─► DriverTokenService (Redis)    │
│   RideController  ──►  RideService                       │
│   LocationController ─► LocationService                 │
│   RouteController ──►  RouteExcelLoader ─► Cache         │
│   AdminDepotController ─► DriverAccessService.reload()   │
│   AdminRouteController ─► RouteRepository + RouteExcelLoader│
│                              │                          │
│                              ▼                          │
│                     RedisLocationService                 │
│                        │         │                      │
│               Write fast│         │Read latest          │
│                        ▼         ▼                      │
│                    ┌────────────────┐                   │
│                    │     REDIS      │  ← GPS buffer     │
│                    │ location:ride: │  ← driver:token:  │
│                    └───────┬────────┘                   │
│                            │ Flush every 60s            │
│                            │ or on ride end             │
│                            ▼                            │
│                    ┌────────────────┐                   │
│                    │   POSTGRESQL   │  ← Ride, Location, │
│                    │                │    Route, Bus      │
│                    └────────────────┘                   │
└────────────┬────────────────────────────────────────────┘
             │
             │ GET last-loc / active rides
             ▼
┌────────────────────┐   ┌───────────────────────────────┐
│   PASSENGER UI     │   │        EXCEL FILES            │
│                    │   │                               │
│  Route Timeline    │   │  Routes: stop_name, lat, lng,  │
│  ETA per stop      │   │  distance_km, slack_min        │
│  Live bus icon     │   │  Depot codes: depot_name, code │
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
     /admin-buses.html (Active Buses / Depot Codes / Routes tabs)
```

---

## ⚡ Redis Integration

WIMB uses Redis (hosted on Upstash) for two things: a **high-speed write buffer** for GPS location updates, and a lightweight **driver token store**.

### Why Redis for GPS?
GPS updates arrive every few seconds per active ride. Writing each update directly to the database causes high I/O and slow response times. Redis solves this by acting as an in-memory buffer.

### How it works

```
GPS Update (every ~3s)
        ↓
   Redis List (instant ~0.1ms write)
   key: location:ride:{rideId}
        ↓
   Scheduler (every 60s)  ──OR──  Ride ends (immediate flush)
        ↓
   PostgreSQL (batch saveAll)
```

### Performance Comparison

| Aspect | Without Redis | With Redis |
|---|---|---|
| Write speed | ~5–10ms (DB) | ~0.1ms (Redis) |
| DB load | Every 3s hit | Batched every 60s |
| Live tracking | Query DB each time | Read from Redis cache |
| Data safety | Always persisted | Flushed on ride end |

### Redis Key Structure
```
location:ride:1     →  List of JSON location objects
location:ride:2     →  List of JSON location objects
driver:token:<uuid> →  Bus number this token was issued for (TTL ~12h, one shift)
```

### Flush Strategy
- **Scheduled flush** — `LocationFlushScheduler` runs every 60 seconds, drains all Redis keys to the database
- **Ride end flush** — When a ride is cancelled or a new ride starts for the same bus, remaining Redis data is immediately flushed so no location data is ever lost
- **Token expiry** — driver tokens expire on their own after ~12 hours; no cleanup job needed

---

## 🔑 Driver Verification (Depot Codes)

Drivers don't get individual accounts. Instead, each **depot** (e.g. Alipurduar, Siliguri, Cooch Behar) has one shared code, managed entirely by the admin through an Excel upload — no code changes, no restart.

### How it works
```
Driver opens driver.html
        │
        ▼
Picks their depot + enters that depot's code  ──►  POST /api/driver/verify
        │                                                  │
        │                                     DriverAccessService checks
        │                                     depot_name + code against
        │                                     the uploaded Excel file
        │                                                  │
        │                                                  ▼
        │                                     DriverTokenService issues a
        │                                     token (stored in Redis,
        │                                     ~12h TTL, tied to the bus
        │                                     number entered)
        ▼
Token saved in the browser (localStorage) — remembered for the rest of
the shift, so the code only needs to be entered once per device.
        │
        ▼
Every ride action (start / location / cancel) sends the token in the
X-Driver-Token header. The server checks the token resolves to the
same bus number the request claims to control.
```

### Depot codes Excel format

| depot_name | code |
|---|---|
| ALIPURDUAR | 101 |
| SILIGURI | 201 |
| COOCHBEHAR | 301 |

- Uploaded from the admin panel's **Depot Codes** tab
- Accepts `.xlsx`, `.xls`, or `.xlsm` — Apache POI's `WorkbookFactory` reads the actual file signature, not just the extension
- Each upload **replaces** the entire depot list (not merged)
- Stored on disk outside the build, at the path set by `wimb.depot.file.path` — so it survives restarts but doesn't need a rebuild to change
- The admin panel can also download a ready-made example file to fill in and re-upload

> 💡 This protects the write endpoints themselves (`/api/ride/start`, `/api/ride/location`, `/api/ride/cancel/**`), not just the driver page — even if someone finds the URL directly, they can't start or move a ride without a valid token.

---

## 🛣️ Multi-Route Support

Originally WIMB shipped with exactly **one** hardcoded route (`routes/route_APD_FLK.xlsx`, loaded once at startup). Real operation needs many independent routes — different corridors, different stop lists, added and removed over time without a redeploy. That's now a first-class concept.

### How it works

- Every route is a real `Route` record (code, display name, file location) backed by Postgres — not just a derived text string.
- Admin manages routes from the **Routes tab**, same interaction pattern as Depot Codes:
    - **Add** — pick a route code (e.g. `APD_FLK`) + display name (`Alipurduar ⇄ Falakata`) + an Excel file. Adding is additive — it doesn't touch other routes.
    - **Update** — rename a route and/or replace its stop file in place. The route code itself never changes.
    - **Delete** — removes the route from the in-memory cache, deletes its Excel file off disk, and removes the DB row.
    - **Download example template** — a ready-to-edit `.xlsx` with the right headers, same as the depot codes template.
- Drivers now **pick their Route explicitly** before choosing Start Location / Destination (mirrors picking a Depot). The stop pickers are then scoped to only that route's stops — not a global list — which is what makes it safe for the same stop name (a shared junction town, say) to legitimately appear on more than one route without any ambiguity.

### Route Excel format (same shape, whichever route it's for)

| stop_order | stop_name | latitude | longitude | distance_km | slack_min |
|:---:|---|:---:|:---:|:---:|:---:|
| 1 | Alipurduar | 26.4799 | 89.5355 | 0 | 0 |
| 2 | Alipurduar Chowpathy | 26.48083 | 89.526 | 1.5 | 2 |
| 3 | Sonapur | 26.494 | 89.368 | 10.5 | 2 |
| 4 | Falakata | 26.51721 | 89.20694 | 15 | 0 |

- Accepts `.xlsx`, `.xls`, or `.xlsm`, same as depot codes
- Admin-added routes are stored on disk at `wimb.route.dir` (default `data/routes/`), outside the build
- The original `routes/route_APD_FLK.xlsx` still loads at startup exactly as before, under a fixed internal key — nothing that already worked was disturbed by adding this

### ⚠️ Known current limitation

`Ride` doesn't yet store *which route* it's actually on — that's the next piece of work. Practically, this means:
- ✅ Starting a ride on any registered route (legacy or newly-added) works correctly — coordinates resolve against the right route.
- ✅ GPS tracking (`/api/ride/location`) is entirely route-agnostic, so it's unaffected either way.
- ⚠️ The passenger-facing live timeline (`track.html`) still resolves stop lookups without knowing which route a ride belongs to, so it can behave incorrectly for rides on anything other than the legacy route. Fixing this properly means giving `Ride`/`StartRideRequest` a real route reference instead of the current derived `"SOURCE_DESTINATION"` string — planned, not yet done.

---

## 🔐 Admin Security

Admin panel is protected by **Spring Security** using `InMemoryUserDetailsManager`.  
**No database required for admin auth** — credentials live in `application.properties`.
```properties
wimb.admin.username=admin
wimb.admin.password=wimb@2025
```

> 💡 To change credentials, update `application.properties` and restart. No code changes needed.

### Access Control

| URL | Who can access |
|---|:---:|
| `/index.html` `/track.html` `/driver.html` | 🌐 Everyone |
| `/api/ride/active` `/api/location/**` `/api/routes/**` | 🌐 Everyone |
| `/api/driver/**` (verify, depots list) | 🌐 Everyone |
| `/api/ride/start` `/api/ride/location` `/api/ride/cancel/**` | 🌐 Public route, 🔑 but requires a valid `X-Driver-Token` header — enforced in the controller, not by Spring Security |
| `/login.html` | 🌐 Everyone |
| `/admin-buses.html` | 🔐 Admin only |
| `/api/ride/active/all` | 🔐 Admin only *(also read by the driver's own "resume ride" check)* |
| `/admin/depots`, `/admin/depots/upload`, `/admin/depots/template` | 🔐 Admin only |
| `/admin/routes`, `/admin/routes/upload`, `/admin/routes/{routeCode}` (update/delete), `/admin/routes/template` | 🔐 Admin only |

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
<summary><b>🔑 Driver Verification APIs</b></summary>

#### Get Depot Names (for the dropdown)
```http
GET /api/driver/depots
```
Returns depot names only — never the codes.

#### Verify Depot + Code
```http
POST /api/driver/verify
Content-Type: application/json

{
  "depotName": "ALIPURDUAR",
  "code": "101",
  "busNumber": "WB-23A-1245"
}
```
Returns a token on success, tied to the given bus number for ~12 hours:
```json
{ "token": "a1b2c3d4-..." }
```

</details>

<details>
<summary><b>🚌 Ride APIs</b></summary>

#### Start Ride
```http
POST /api/ride/start
Content-Type: application/json
X-Driver-Token: a1b2c3d4-...

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
X-Driver-Token: a1b2c3d4-...

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

#### Get All Active Buses *(Admin — also used by the driver's resume check)*
```http
GET /api/ride/active/all
```

#### Cancel Ride
```http
PUT /api/ride/cancel/{rideId}
X-Driver-Token: a1b2c3d4-...
```

</details>

<details>
<summary><b>📍 Location APIs</b></summary>

#### Get Last Known Location
```http
GET /api/location/last-loc/{rideId}
```
> ⚡ Returns from Redis if ride is active, falls back to the database if not buffered.

</details>

<details>
<summary><b>🗺️ Route APIs (public)</b></summary>

#### Get Route Stops Between Two Stops
```http
GET /api/routes?source=ALIPURDUAR&destination=FALAKATA&routeCode=APD_FLK
```
`routeCode` is optional — omitting it falls back to the original legacy route, unchanged from before multi-route support existed.

#### List All Routes (for the driver's route picker)
```http
GET /api/routes/list
```
Returns code, name, and stop count only — never the server file path.

#### Get Every Stop on One Route
```http
GET /api/routes/stops?routeCode=APD_FLK
```

</details>

<details>
<summary><b>🏢 Admin Depot APIs</b></summary>

#### View Current Depot Codes
```http
GET /admin/depots
```

#### Upload New Depot Codes File
```http
POST /admin/depots/upload
Content-Type: multipart/form-data

file: depot-codes.xlsx
```
Replaces the entire depot list and reloads it immediately — no restart.

#### Download Example Template
```http
GET /admin/depots/template
```
Returns a ready-to-edit `.xlsx` with the correct headers and sample rows.

</details>

<details>
<summary><b>🛣️ Admin Route APIs</b></summary>

#### List All Routes (full detail)
```http
GET /admin/routes
```

#### Add a New Route
```http
POST /admin/routes/upload
Content-Type: multipart/form-data

routeCode: APD_FLK
routeName: Alipurduar ⇄ Falakata
file: route.xlsx
```
Fails if the route code already exists — use Update instead.

#### Update a Route (name and/or file)
```http
PUT /admin/routes/{routeCode}
Content-Type: multipart/form-data

routeName: Alipurduar ⇄ Falakata (revised)
file: (optional — omit to keep the existing stops)
```

#### Delete a Route
```http
DELETE /admin/routes/{routeCode}
```
Removes it from the cache, deletes the Excel file from disk, and removes the DB row.

#### Download Example Template
```http
GET /admin/routes/template
```

</details>

---

## 🖥️ Frontend Pages

| Page | Who uses it | Description |
|---|---|---|
| `index.html` | Passenger | Search buses, recent searches, quick results |
| `track.html` | Passenger | Live route timeline, ETA, bus position, dark mode |
| `driver.html` | Driver | Depot + code verification, route picker (stop lists scoped to it), start/resume ride, live GPS updates |
| `login.html` | Admin | Secure login form |
| `admin-buses.html` | Admin | Tabbed panel — **Active Buses** (live cards, search), **Depot Codes** (upload, template, current list), **Routes** (add, update, delete, template, current list) |

---

## 🗂️ Project Structure
```
WIMB/
├── src/main/java/com/deep/WIMB/
│   ├── config/
│   │   ├── SecurityConfig.java          # Spring Security rules
│   │   ├── RedisConfig.java             # Redis template config
│   │   └── CorsConfig.java
│   ├── controller/
│   │   ├── RideController.java
│   │   ├── LocationController.java
│   │   ├── RouteController.java         # public route lookup/list/stops
│   │   ├── DriverAuthController.java    # /api/driver/verify, /api/driver/depots
│   │   ├── AdminDepotController.java    # /admin/depots (view/upload/template)
│   │   ├── AdminRouteController.java    # /admin/routes (view/add/update/delete/template)
│   │   ├── DriverController.java        # serves the driver.html view (Thymeleaf)
│   │   ├── AdminController.java         # serves admin.html / admin-buses.html views
│   │   ├── HomeController.java
│   │   └── CsrfController.java
│   ├── service/
│   │   ├── RideService.java
│   │   ├── LocationService.java
│   │   ├── RedisLocationService.java    # Redis read/write/clear
│   │   ├── RouteExcelLoader.java        # loads/caches every registered route's Excel
│   │   ├── DriverAccessService.java     # loads/reloads depot codes Excel
│   │   └── DriverTokenService.java      # issues/resolves driver tokens (Redis)
│   ├── exception/
│   │   ├── DriverNotVerifiedException.java
│   │   └── ApiExceptionHandler.java     # turns errors into JSON, not HTML pages
│   ├── scheduler/
│   │   └── LocationFlushScheduler.java  # batch flush Redis → PostgreSQL
│   ├── model/
│   │   ├── Ride.java
│   │   ├── Bus.java
│   │   ├── Route.java                   # routeCode, routeName, filePath, stopCount
│   │   └── Location.java               # implements Serializable
│   ├── enums/
│   │   └── RideStatus.java
│   ├── repository/
│   │   ├── RideRepository.java
│   │   ├── BusRepository.java
│   │   └── RouteRepository.java
│   └── dto/
│       ├── StartRideRequest.java
│       ├── LocationUpdateRequest.java
│       ├── ActiveRideResponse.java
│       ├── RouteStop.java
│       └── RouteSummary.java            # public-safe route listing (no file paths)
│
├── src/main/resources/
│   ├── templates/                       # Thymeleaf views
│   │   ├── driver.html
│   │   ├── admin.html
│   │   └── admin-buses.html
│   ├── static/
│   │   ├── index.html                   # Passenger search
│   │   ├── track.html                   # Route timeline
│   │   ├── login.html                   # Admin login
│   │   ├── js/
│   │   │   ├── index.js
│   │   │   ├── track.js
│   │   │   ├── driver.js
│   │   │   └── admin-buses.js
│   │   └── data/
│   │       └── stops.json               # auto-generated from the legacy route's Excel
│   ├── routes/
│   │   └── route_APD_FLK.xlsx           # legacy route master data (build-time)
│   └── application.properties
│
├── data/
│   ├── depot-codes.xlsx                 # depot codes (runtime, admin-uploaded)
│   └── routes/                          # every admin-added route's Excel file
│       └── APD_FLK.xlsx
│
└── pom.xml
```

---

## ⚙️ Setup & Run

### Prerequisites

- Java 21+
- A PostgreSQL database (Neon or any Postgres instance)
- Maven 3.8+
- A Redis instance (Upstash or any Redis-compatible instance)

### Steps
```bash
# 1. Clone the repository
git clone https://github.com/Deep-Saha1925/Where-is-my-Bus.git
cd Where-is-my-Bus
```
```bash
# 2. Configure application.properties
```
```properties
# Database (PostgreSQL — e.g. a free Neon project)
spring.datasource.url=jdbc:postgresql://<host>/<db>?sslmode=require
spring.datasource.username=<user>
spring.datasource.password=<password>
spring.jpa.hibernate.ddl-auto=update

# Redis (e.g. a free Upstash database)
spring.data.redis.host=<host>
spring.data.redis.port=<port>
spring.data.redis.password=<password>
spring.data.redis.ssl.enabled=true

# Admin credentials
wimb.admin.username=admin
wimb.admin.password=wimb@2025

# Depot codes file (created/updated via the admin panel — no need to
# create it manually; the app just waits for the first upload)
wimb.depot.file.path=data/depot-codes.xlsx

# Directory admin-added routes are stored in (same idea — created automatically)
wimb.route.dir=data/routes
```
> 🔒 Don't commit real credentials here if this repo is public — use environment variables or `application-local.properties` (gitignored) for anything beyond a personal dev setup.

```bash
# 3. Build and run
./mvnw spring-boot:run
```
```bash
# 4. Open in browser
open http://localhost:8080             # Passenger view
open http://localhost:8080/driver.html # Driver panel
open http://localhost:8080/login.html  # Admin login
```
```bash
# 5. First-time setup
# Log in as admin →
#   Depot Codes tab: download the example template, fill in real depots, upload.
#   Routes tab: download the example template, fill in a real route, upload.
# Drivers can verify and start rides immediately after — no restart required.
```

### Verify Redis is working
```bash
# After starting a ride, check Redis (via redis-cli pointed at your Upstash instance,
# or the Upstash web console's Data Browser)
KEYS *                          # should show location:ride:1 and driver:token:* entries
LLEN location:ride:1            # number of buffered locations
LRANGE location:ride:1 0 -1     # all buffered location JSON
TTL driver:token:<uuid>         # seconds remaining before that driver's token expires
```

---

## 🚀 Future Enhancements

- [ ] WebSocket live tracking (replace 3s polling)
- [x] ~~Admin Excel upload panel (no restart needed)~~ — done, for depot codes **and** routes
- [x] ~~Multi-route support per Excel file~~ — done: admin can add/update/delete routes, driver picks one explicitly
- [ ] Give `Ride` a real route reference, so `track.html` resolves correctly for any route, not just the legacy one
- [ ] Push notifications when bus is nearby
- [ ] Per-bus (not just per-depot) driver codes, for finer-grained access control
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