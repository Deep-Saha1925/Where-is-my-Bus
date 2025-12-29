
# 🚌 Where Is My Bus (WIMB)

## 📌 Project Description

**Where Is My Bus (WIMB)** is a real-time, route-based bus tracking system designed to solve the problem of **uncertain bus timings and locations**, especially in semi-urban and rural regions like North Bengal.

Unlike traditional systems that rely only on databases, WIMB uses a **hybrid approach**:
- **Dynamic data** (bus location, rides) from backend APIs
- **Static route data** (stops, distances, halt times) loaded from **Excel sheets**

This makes the system **easy to maintain, scalable, and realistic for government/private transport use**.

---

## ❓ Problem Statement

- Passengers wait without knowing the bus position
- No ETA or stop-level visibility
- Route data is hardcoded or poorly maintained
- No single platform to view live buses on a route

---

## ✅ Solution Overview

WIMB provides:

- 🔴 **Live bus tracking on map**
- 🗺️ **Route visualization with stops**
- 🧾 **Excel-based static route management**
- ⏱️ **Auto-updating bus location (no click required)**
- 🔎 **Search buses using Source → Destination**
- 📍 **Reverse-geocoded human-readable location**

---

## 🛠️ Technology Stack

### 🔹 Backend
- Java 17
- Spring Boot
- RESTful APIs
- Apache POI (Excel reading)
- MySQL (for rides & locations)

### 🔹 Frontend
- HTML, CSS, JavaScript
- Leaflet.js (Maps)
- OpenStreetMap tiles
- Font Awesome icons

---

## 🧱 Architecture

```
Driver App (GPS)
      ↓
Location API (Spring Boot)
      ↓
Database (Live Data)
      ↓
User UI (Leaflet Map)

Excel Route File
      ↓
Route Loader Service
      ↓
Route API
      ↓
Map + ETA Table
```

---

## 📂 Static Route Management (Excel)

Routes are stored in Excel files like:

```
ABC.xlsx
```

### 📑 Excel Format (Mandatory)

| routeKey | stopName | latitude | longitude | distanceFromStartKm | slackTimeMin |
|--------|----------|----------|-----------|---------------------|--------------|
| SLG_NJP | Siliguri | 26.7271  | 88.3953   | 0                   | 0            |
| SLG_NJP | Bagdogra | 26.6986  | 88.3110   | 12                  | 5            |
| SLG_NJP | NJP      | 26.6812  | 88.3290   | 18                  | 0            |

✔ Loaded once at application startup  
✔ No DB required for route data  
✔ Easy to update by non-technical staff

---

## 🔗 Backend API Endpoints

### ▶ Start Ride
```
POST /api/ride/start
```

```json
{
  "busId": 1,
  "source": "SLG",
  "destination": "NJP",
  "latitude": 26.7271,
  "longitude": 88.3953
}
```

---

### ▶ Update Location (Driver GPS)
```
POST /api/location/update
```

```json
{
  "rideId": 5,
  "latitude": 26.7210,
  "longitude": 88.3821,
  "speed": 32.5
}
```

---

### ▶ Get Active Buses on Route
```
GET /api/ride/active?source=SLG&destination=NJP
```

---

### ▶ Get Route Stops (Excel-backed)
```
GET /api/routes/{routeKey}
```

Example:
```
/api/routes/SLG_NJP
```

---


### ▶ Admin Dashboard (Track all bus location live)
```
GET /admin/active/all-buses
```


## 🖥️ Frontend Flow

### index.html
- Enter **Source & Destination**
- View **all active buses**
- Click **Track Bus**

### track.html
- Full route drawn from Excel
- Stops marked on map
- Live bus icon moves automatically
- Popup always shows current location
- ETA table rendered from route data

### admin-buses.html
- Enter **See all live buses**
- View **bus live location on map**
- Click **Track Bus**

---

## ✨ Key Features Implemented

- ✅ Route visualization from Excel
- ✅ Auto-moving bus marker
- ✅ No click needed to see location
- ✅ Reverse geocoding (OpenStreetMap)
- ✅ Smooth map pan with movement
- ✅ Works for multiple routes
- ✅ Clean and scalable design

---

## 🚀 Future Enhancements

- WebSocket-based live updates
- ETA prediction per stop
- Admin panel for Excel upload
- Alerts & notifications
- Historical route analytics

---

## 👨‍💻 Author

**Deep Saha**  
Backend & System Design Enthusiast  
Java | Spring Boot | Maps | Real-Time Systems
