# Intelligent Alert Escalation & Resolution System

> **MoveInSync Engineering Case Study**

![Java](https://img.shields.io/badge/Java-23-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?style=flat-square&logo=spring)
![React](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-336791?style=flat-square&logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-Upstash-DC382D?style=flat-square&logo=redis)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3.4-38BDF8?style=flat-square&logo=tailwindcss)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

---

## 📺 Demonstration Video

> **[▶ Watch on Video](https://drive.google.com/file/d/1Seh0XA4Z44Hqwh2AYh-0NX1lk_CYGAYY/view?usp=sharing)**

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Tech Stack](#3-tech-stack)
4. [Core Features](#4-core-features)
5. [API Reference](#5-api-reference)
6. [System Robustness & Design Decisions](#6-system-robustness--design-decisions)
7. [Local Setup](#7-local-setup)
8. [Project Structure](#8-project-structure)

---

## 1. Project Overview

This system is built to solve a real operational problem in fleet management: **alert fatigue**. Operations teams at MoveInSync deal with a constant stream of driver alerts (speeding, compliance failures, harsh braking). Without intelligent escalation and automatic closure, the dashboard becomes noise — genuine critical events get buried.

The solution is split into two layers:

| Layer                        | Role                                                                                                                                                                                                                                |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend "Brain"**          | Spring Boot REST API — ingests alerts, evaluates rules, escalates automatically, auto-closes stale alerts via a scheduled worker, and surfaces audit history for every state change.                                                |
| **Frontend "Control Panel"** | React/Vite SPA — a live operations dashboard showing real-time stats, a trends chart, a Top Offenders leaderboard, paginated alert feeds, and a drill-down modal with full state transition history and a manual resolution button. |

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    React / Vite Frontend                     │
│  Dashboard → Stats │ Trends │ Top Offenders │ Alert Feeds   │
│                     Alert Drill-Down Modal                   │
└───────────────────────────┬─────────────────────────────────┘
                            │  HTTP (JWT Bearer)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot Backend                        │
│                                                              │
│  POST /api/alerts                                            │
│       │                                                      │
│       ▼                                                      │
│  AlertService (persist → RuleEngine → re-persist → event)   │
│       │                                                      │
│       ├──► RuleEngine (Strategy Pattern, O(1) dispatch)      │
│       │         └── OverspeedingRuleStrategy                 │
│       │         └── ComplianceRuleStrategy                   │
│       │                                                      │
│       └──► TransitionPublisher → AuditListener (@Async)      │
│                                                              │
│  AutoCloseProcessor (@Scheduled every 5 min)                 │
│                                                              │
│  DashboardController  ──►  DashboardService                  │
└───────────────────────────┬─────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                ▼                       ▼
        PostgreSQL (Neon)         Redis (Upstash)
        alerts table              leaderboard cache
        alert_transition_history
```

---

## 3. Tech Stack

### Backend

| Concern         | Technology                                            |
| --------------- | ----------------------------------------------------- |
| Language        | Java 23                                               |
| Framework       | Spring Boot 3.2.5                                     |
| Security        | Spring Security + JWT (HS256)                         |
| ORM             | Hibernate / Spring Data JPA                           |
| Database        | PostgreSQL via [Neon](https://neon.tech) (serverless) |
| Caching         | Redis via [Upstash](https://upstash.com)              |
| Connection pool | HikariCP (tuned for serverless cold-start)            |
| Scheduling      | Spring `@Scheduled`                                   |
| Validation      | Jakarta Bean Validation                               |

### Frontend

| Concern     | Technology                   |
| ----------- | ---------------------------- |
| Framework   | React 18 + Vite 5            |
| Styling     | Tailwind CSS 3.4             |
| Charts      | Recharts 2                   |
| Icons       | Lucide React                 |
| HTTP Client | Axios (with JWT interceptor) |
| Routing     | React Router DOM 6           |

---

## 4. Core Features

### 4.1 Centralized Alert Ingestion

A single `POST /api/alerts` endpoint accepts a normalized payload. Sensitive fields (database ID, timestamps, initial status) are never accepted from the caller — they are assigned server-side.

```json
// POST /api/alerts
// All three fields are required. Missing or invalid values return HTTP 400
// with a structured JSON error body listing every constraint violation.
{
  "driverId": "DRV-001",
  "sourceType": "SPEED_MONITOR",
  "severity": "WARNING",
  "metadata": {
    "speed_kmh": 92,
    "limit_kmh": 80,
    "location": "NH-48, Gurugram"
  }
}
```

**Deduplication (Idempotency):** A composite unique index on `(driverId, sourceType, minute_bucket)` prevents the same event from being logged twice within the same minute. Duplicate submissions return `HTTP 409 Conflict` instead of silently inserting a duplicate row.

---

### 4.2 Lightweight, Config-Driven Rule Engine

The Rule Engine uses the **Strategy Pattern** for zero-friction extensibility. Rules are defined in `application-rules.yml` — no code change is required to add or tune a threshold.

```yaml
# application-rules.yml — change thresholds without recompiling
rules:
  strategies:
    SPEED_MONITOR:
      escalate-if-count: 3 # 3rd speeding event within the window → CRITICAL
      window-mins: 60
    COMPLIANCE:
      auto-close-if: "document_valid"
```

**Dispatch is O(1):** At startup, `RuleEngine` builds a `Map<sourceType → RuleStrategy>`. Every inbound alert is routed in a single map lookup — no `if-else` chains, no `switch` statements.

**Adding a new rule requires exactly two steps:**

1. Create a `@Component` implementing `RuleStrategy`.
2. Add its threshold config to `application-rules.yml`.

Spring auto-discovers and registers it. No changes to `RuleEngine` or `AlertService`.

---

### 4.3 Auto-Close Background Worker

`AutoCloseProcessor` runs every 5 minutes via `@Scheduled`. It finds all alerts that have been `OPEN` or `ESCALATED` for more than **24 hours** and transitions them to `AUTO_CLOSED`.

**Why this is safe to run multiple times (Idempotency):** The query targets alerts where `status IN ('OPEN', 'ESCALATED')`. An already-closed alert will never match, so re-running the job — due to a restart, retry, or clock skew — produces zero side effects.

Each auto-closure fires an `AlertTransitionEvent`, which `AuditListener` handles asynchronously (`@Async`) to write a history record without blocking the scheduler thread.

---

### 4.4 Event-Driven Audit Trail

Every status transition is recorded in `alert_transition_history`. The flow is decoupled: `AlertService` publishes a Spring `ApplicationEvent`; `AuditListener` handles it on a separate thread pool. This means history writes never slow down the critical path of alert ingestion.

---

### 4.5 Real-Time Operations Dashboard

The frontend dashboard auto-refreshes every 30 seconds and surfaces four widgets:

| Widget        | API Endpoint                                   | Description                                |
| ------------- | ---------------------------------------------- | ------------------------------------------ |
| Summary Stats | `GET /api/dashboard/stats`                     | Total, open, escalated, auto-closed counts |
| Trend Chart   | `GET /api/dashboard/trends`                    | Daily alert counts by status (last N days) |
| Top Offenders | `GET /api/dashboard/leaderboard`               | Top 5 drivers by active alert count        |
| Alert Feeds   | `GET /api/dashboard/alerts/active` / `/closed` | Paginated active and closed alert lists    |

---

### 4.6 Alert Drill-Down & Manual Resolution

Clicking any alert row opens a modal that shows:

- Full alert metadata
- Complete state transition history (sourced from `alert_transition_history`)
- A **"Resolve Manually"** button for `OPEN` / `ESCALATED` alerts

The resolve action calls `PUT /api/alerts/{id}/resolve`. The backend enforces a **state-machine guard**: attempting to resolve an already-`RESOLVED` or `AUTO_CLOSED` alert returns `HTTP 409 Conflict` — not a silent no-op.

---

## 5. API Reference

### Authentication

```http
POST /api/auth/login

{
  "username": "admin",
  "password": "admin123"
}

# Response: { "token": "<JWT>" }
# Include in all subsequent requests as:
# Authorization: Bearer <JWT>
```

### Alerts

| Method | Path                       | Description               |
| ------ | -------------------------- | ------------------------- |
| `POST` | `/api/alerts`              | Ingest a new alert        |
| `PUT`  | `/api/alerts/{id}/resolve` | Manually resolve an alert |

### Dashboard

| Method | Path                                          | Description              |
| ------ | --------------------------------------------- | ------------------------ |
| `GET`  | `/api/dashboard/stats`                        | Aggregate counts         |
| `GET`  | `/api/dashboard/leaderboard`                  | Top offenders            |
| `GET`  | `/api/dashboard/trends?tz=Asia/Kolkata`       | Daily trend data         |
| `GET`  | `/api/dashboard/alerts/active?page=0&size=10` | Paginated active alerts  |
| `GET`  | `/api/dashboard/alerts/closed?page=0&size=10` | Paginated closed alerts  |
| `GET`  | `/api/dashboard/alerts/{id}/history`          | State transition history |

---

## 6. System Robustness & Design Decisions

### JWT Authentication

All endpoints (except `POST /api/auth/login`) require a valid HS256 JWT. The `JwtAuthenticationFilter` validates the token on every request. A missing, expired, or tampered token returns `HTTP 401 Unauthorized` — handled by `JwtAuthenticationEntryPoint` with a structured JSON error body rather than an HTML redirect.

### Input Validation (400 Bad Request)

All inbound DTOs are annotated with Jakarta Bean Validation constraints (`@NotBlank`, `@NotNull`). `GlobalExceptionHandler` catches `MethodArgumentNotValidException` and returns a structured response listing every constraint violation by field name — no stack traces exposed to the caller.

### State-Machine Guards (409 Conflict)

`AlertService` validates the current status before applying a transition. Trying to resolve an alert that is not in a resolvable state throws `AlertStateException`, which `GlobalExceptionHandler` maps to `HTTP 409 Conflict`. This prevents double-resolution race conditions.

### Deduplication (409 Conflict)

A database-level unique constraint (not application-level) on `(driverId, sourceType, minute_bucket)` is the source of truth for deduplication. `AlertService` catches `DataIntegrityViolationException` and translates it to `HTTP 409`, ensuring only one event per driver per source per minute reaches the database.

### Redis Caching

The Top Offenders leaderboard is cached in Upstash Redis with a 5-minute TTL under the key `topDrivers`. This prevents a full aggregation query against `alerts` on every dashboard load. The cache is invalidated on any alert state change.

### HikariCP — Tuned for Serverless Cold-Start

Neon PostgreSQL suspends its compute after a period of inactivity. Without tuning, the default HikariCP settings would throw a connection error on the first request after a cold start.

```yaml
hikari:
  initialization-fail-timeout: -1 # don't crash on startup; retry until Neon wakes
  maximum-pool-size: 5 # stay within Neon free-tier connection limit
  connection-test-query: SELECT 1 # validate before handing a connection to the app
```

### OOP Design

The codebase is structured around standard layered architecture:

- **Controllers** — handle HTTP concerns only (routing, status codes, DTO mapping)
- **Services** — own business logic and transaction boundaries
- **Repositories** — Spring Data JPA interfaces; custom JPQL/native queries for aggregations
- **Rule Strategies** — each rule is an independent `@Component`; `RuleEngine` composes them

---

## 7. Local Setup

### Prerequisites

- Java 23+
- Maven 3.9+ (or use the IntelliJ bundled Maven)
- Node.js 20+

### Step 1 — Clone the repository

```bash
git clone https://github.com/<your-username>/moveinsync-alert-system.git
cd moveinsync-alert-system
```

### Step 2 — Configure credentials

Open `src/main/resources/application.yml` and replace the placeholder values:

```yaml
spring:
  datasource:
    # Add your Neon (or any PostgreSQL) JDBC URL
    url: jdbc:postgresql://<YOUR_NEON_HOST>/neondb?sslmode=require&connectTimeout=10&socketTimeout=60&tcpKeepAlive=true
    username: <YOUR_DB_USERNAME>
    password: <YOUR_DB_PASSWORD>

  data:
    redis:
      # Add your Upstash Redis URL (rediss:// for TLS)
      url: rediss://default:<YOUR_REDIS_PASSWORD>@<YOUR_UPSTASH_HOST>:6379

jwt:
  # Generate a secure random Base64 string (minimum 32 bytes)
  secret: <YOUR_JWT_SECRET>
```

> **Security note:** These credentials are intentionally absent from the public repository. Never commit real secrets to source control.

### Step 3 — Run the backend

```bash
# This flag points the JVM at a custom hosts file required for Neon DNS resolution
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djdk.net.hosts.file=$HOME/jvm-hosts"
```

The backend starts on `http://localhost:8080`.

### Step 4 — Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:5173` (or `5174` if 5173 is occupied). The Vite dev server proxies all `/api` requests to `localhost:8080`.

### Step 5 — Log in

Navigate to `http://localhost:5173` and log in with:

```
Username: admin
Password: admin123
```

### Step 6 — Seed sample data (optional)

Use the included `test-edge-cases.http` file (compatible with the VS Code REST Client or IntelliJ HTTP Client) to fire sample alert payloads and observe escalation, deduplication, and auto-close behaviour.

---

## 8. Project Structure

```
.
├── src/main/java/com/moveinsync/alertsystem/
│   ├── config/          # AppConfig, RuleProperties (@ConfigurationProperties), SecurityConfig
│   ├── controller/      # AlertController, AuthController, DashboardController
│   ├── dto/             # Request/Response DTOs (decoupled from JPA entities)
│   ├── entity/          # Alert, AlertTransitionHistory (JPA entities)
│   ├── enums/           # AlertSeverity, AlertStatus
│   ├── event/           # AlertTransitionEvent, AuditListener (@Async), TransitionPublisher
│   ├── exception/       # GlobalExceptionHandler, AlertStateException, ResourceNotFoundException
│   ├── repository/      # AlertRepository, AlertTransitionHistoryRepository (Spring Data JPA)
│   ├── rules/           # RuleEngine (Strategy dispatcher), RuleStrategy (interface),
│   │                    # OverspeedingRuleStrategy, ComplianceRuleStrategy
│   ├── security/        # JwtUtil, JwtAuthenticationFilter, JwtAuthenticationEntryPoint
│   ├── service/         # AlertService, DashboardService
│   └── worker/          # AutoCloseProcessor (@Scheduled)
│
├── src/main/resources/
│   ├── application.yml          # Main config (datasource, Redis, JWT, HikariCP tuning)
│   └── application-rules.yml   # Rule engine thresholds (externalised, no recompile needed)
│
└── frontend/
    ├── src/
    │   ├── api/           # api.js — Axios instance + all endpoint functions
    │   ├── components/    # AlertDrillDownModal, AlertsFeed, TopOffenders, TrendsChart, ...
    │   ├── pages/         # Dashboard, Login
    │   └── main.jsx
    └── vite.config.js     # /api proxy → localhost:8080
```

---

_Built for the MoveInSync Backend Engineering Case Study._
