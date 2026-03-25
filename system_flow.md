# MediBook — System Design

![MediBook System Architecture](C:\Users\HP Pavilion Gaming\.gemini\antigravity\brain\97496c5a-33f5-4a64-8828-e33e3288f641\medibook_system_design_1774461614267.png)


## 1. Overall Architecture

```mermaid
graph TD
    subgraph Mobile["📱 React Native Mobile App"]
        UI[Screens / UI Components]
        RTK[RTK Query - API Layer]
        Redux[Redux Store + Persist]
        WS[useWebSocket Hook\n STOMP over SockJS]
        FCM_MOB[usePushNotifications Hook\n Firebase SDK]
    end

    subgraph Backend["☕ Spring Boot Backend :9091"]
        Auth[AuthController\n /api/auth]
        Appt[AppointmentController\n /api/appointments]
        Notif[NotificationController\n /api/notifications]
        Slot[SlotController\n /api/slots]
        WS_BROKER[STOMP WebSocket Broker\n /ws]
        FCM_SVC[FcmPushService\n Firebase Admin SDK]
        SVC[Service Layer\n Business Logic]
        REPO[JPA Repositories]
    end

    subgraph External["☁️ External Services"]
        DB[(PostgreSQL\n Database)]
        FIREBASE[Google Firebase Cloud\n Messaging FCM]
    end

    UI --> RTK
    RTK -- REST API calls --> Auth
    RTK -- REST API calls --> Appt
    RTK -- REST API calls --> Notif
    RTK -- REST API calls --> Slot
    WS -- STOMP Subscribe --> WS_BROKER
    FCM_MOB -- Token Registration --> Notif

    Auth --> SVC
    Appt --> SVC
    Notif --> SVC
    Slot --> SVC
    SVC --> REPO
    REPO --> DB
    SVC -- Broadcast --> WS_BROKER
    SVC --> FCM_SVC
    WS_BROKER -- Real-time Push --> WS
    FCM_SVC -- Push via HTTP --> FIREBASE
    FIREBASE -- System Drawer / Wake --> FCM_MOB
```

---

## 2. Authentication Flow

```mermaid
sequenceDiagram
    actor User
    participant App as React Native App
    participant Backend as Spring Boot
    participant DB as PostgreSQL

    User->>App: Enter email + password
    App->>Backend: POST /api/auth/login
    Backend->>DB: Lookup user by email
    DB-->>Backend: User found
    Backend->>Backend: Validate BCrypt password
    Backend-->>App: 200 OK + JWT Token + User Info
    App->>App: Store JWT in Redux (persisted)
    App->>App: Navigate to Home Screen
    Note over App,Backend: All subsequent requests include\nAuthorization: Bearer <JWT>
```

---

## 3. Appointment Booking Flow

```mermaid
sequenceDiagram
    actor Patient
    participant App as React Native App
    participant Backend as Spring Boot
    participant DB as PostgreSQL
    participant WS as WebSocket Broker
    participant FCM as Firebase FCM

    Patient->>App: Select Doctor → Select Slot → Book
    App->>Backend: POST /api/appointments\n{slotId}
    Backend->>DB: Create appointment (PENDING)
    Backend->>DB: Mark slot as BOOKED
    Backend->>DB: Save Notification for Patient
    Backend->>WS: Broadcast to Patient via STOMP
    Backend->>DB: Save Notification for Doctor
    Backend->>WS: Broadcast to Doctor via STOMP
    Backend->>FCM: Send HIGH priority push to Patient token
    Backend->>FCM: Send HIGH priority push to Doctor token
    Backend-->>App: 201 Created + AppointmentResponse
    WS-->>App: Live bell icon update (if app is open)
    FCM-->>App: System Drawer notification (if app is in background)
```

---

## 4. Appointment Confirmation Flow (Doctor)

```mermaid
sequenceDiagram
    actor Doctor
    participant App as React Native App
    participant Backend as Spring Boot
    participant DB as PostgreSQL
    participant FCM as Firebase FCM

    Doctor->>App: Tap "Confirm" on appointment card
    App->>Backend: PUT /api/appointments/{id}/confirm
    Backend->>DB: Update status to CONFIRMED
    Backend->>DB: Save "Appointment Confirmed" Notification for Patient
    Backend->>FCM: Send push notification to Patient's device token
    FCM-->>Patient Phone: 🔔 "Your appointment is confirmed!"
    Backend-->>App: 200 OK + Updated Appointment
```

---

## 5. Real-Time Notification Flow

```mermaid
flowchart LR
    subgraph AppOpen["App is OPEN (Foreground)"]
        direction TB
        STOMP["STOMP WebSocket\n receives message"] --> BELL["Bell icon updates\n instantly in UI"]
        FCM_FG["Firebase onMessage\n listener fires"] --> TOAST["Toast popup\n shows at top of screen"]
    end

    subgraph AppClosed["App is CLOSED / Background"]
        direction TB
        FCM_BG["Firebase Background\n Message Handler fires"] --> DRAWER["Android System\n Drawer notification drops"]
        DRAWER --> OPEN["User taps → App opens"]
    end

    Backend["Spring Boot\n sendNotification()"] --> STOMP
    Backend --> FCM_FG
    Backend --> FCM_BG
```

---

## 6. FCM Device Token Lifecycle

```mermaid
flowchart TD
    LOGIN["User Logs In"] --> PERM{"Android 13+?\nRequest POST_NOTIFICATIONS"}
    PERM -- Denied --> NOPUSH["No push\nnotifications"]
    PERM -- Granted --> FIREBASE["Firebase SDK\ngetToken()"]
    FIREBASE --> TOKEN["Unique Device FCM Token"]
    TOKEN --> API["POST /api/notifications/device-token\n{fcmToken, deviceType: ANDROID}"]
    API --> DB[(Save to\ndevice_tokens table\nlinked to User)]
    EXPIRE["Token Expires /\nApp Reinstalled"] --> REFRESH["onTokenRefresh fires"]
    REFRESH --> API
```

---

## 7. Tech Stack Summary

| Layer | Technology |
|---|---|
| **Mobile Frontend** | React Native + TypeScript |
| **State Management** | Redux Toolkit + RTK Query |
| **Persistence** | redux-persist (AsyncStorage) |
| **Navigation** | React Navigation v6 |
| **Real-Time (Foreground)** | STOMP over SockJS WebSocket |
| **Push Notifications** | Firebase Cloud Messaging (FCM) |
| **Backend Framework** | Spring Boot 3.5 (Java 17) |
| **Security** | Spring Security + JWT |
| **ORM** | Spring Data JPA + Hibernate |
| **Database** | PostgreSQL 14 |
| **Build Tool** | Maven |
