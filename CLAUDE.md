# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CostLink is a VPN expense reimbursement system with LDAP authentication, OCR-based amount recognition, and an approval workflow. Vue 3 frontend + Spring Boot 3.2 backend + MySQL 8.0.

## Development Commands

### Full Stack (Docker)
```bash
docker-compose up -d                    # Start all services
docker-compose up -d --build            # Rebuild and start
docker-compose down                     # Stop services
```

### Backend (costlink-backend/)
```bash
docker-compose up -d mysql              # Start MySQL first (required)
cd costlink-backend
mvn spring-boot:run                     # Run backend (port 8080)
mvn clean package                       # Build JAR
mvn test                                # Run tests
mvn test -Dtest=ClassName               # Run single test class
```

### Frontend (costlink-frontend/)
```bash
cd costlink-frontend
npm install
npm run dev                             # Dev server (port 5173), proxies /api to :8080
npm run build                           # Type-check (vue-tsc) + production build
```
No linter is configured. No test framework is set up.

## Architecture

### Backend (Java 17, Spring Boot 3.2, MyBatis-Plus)
Base package: `com.costlink`
- **controller/**: REST endpoints — `AuthController`, `UserController`, `ReimbursementController`, `AdminController`
- **service/**: Business logic — `AuthService` (LDAP + mock login), `ReimbursementService`, `UserService`, `OcrService` (Qwen VL API)
- **mapper/**: MyBatis-Plus mapper interfaces (no XML mappings — all queries via MP API)
- **entity/**: MyBatis-Plus entities with `@TableName("t_*")` annotations, Lombok `@Data`
- **dto/**: Request/Response objects (API contracts)
- **config/**: `SecurityConfig`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `UserPrincipal`, `MybatisPlusConfig`, `WebConfig`
- **exception/**: `BusinessException` + `GlobalExceptionHandler` (global `@RestControllerAdvice`)

### Frontend (Vue 3, TypeScript, Vite, Element Plus)
- **api/index.ts**: All API calls centralized here; axios instance with JWT interceptor (401 → redirect to login)
- **router/index.ts**: Route guards — `meta.requiresAuth` (default true), `meta.requiresAdmin`
- **stores/user.ts**: Pinia store for user state and auth
- **views/**: `Layout.vue` (shell), `Login`, `Apply`, `Profile`, `Reimbursement`, `Management` (admin), `Dashboard` (admin)

### Security
- Stateless JWT auth; token in localStorage, sent as `Bearer` header
- `/api/auth/login` and `/api/uploads/**` are public; `/api/admin/**` requires ADMIN role; all others require authentication
- `UserPrincipal` extracted from JWT in `JwtAuthenticationFilter` and available via `@AuthenticationPrincipal`

### Key Business Logic
- **Reimbursement Status Flow**: PENDING → CONFIRMED → PAID (or PENDING → REJECTED)
- **Admin Detection**: Users listed in `ADMIN_USERS` env var get ADMIN role at login/user-creation time
- **Mock Login Mode** (`LDAP_ENABLED=false`): Any credentials work; user auto-created on first login
- **LDAP Auth**: Supports both simple-bind and service-account modes (configured via `LDAP_BIND_DN`)
- **OCR**: Qwen VL multimodal API extracts payment amounts from uploaded purchase screenshots (configured via `QWEN_API_KEY`); falls back to regex extraction if direct parse fails
- **Image Export**: Batch-export paid records' images as ZIP

## Database

MySQL 8.0. Tables: `t_user`, `t_reimbursement`, `t_reimbursement_image`, `t_admin_config`

Schema initialized via `init/init.sql`. Default admin: `admin` / `admin123`

## Configuration

Backend config in `application.yml`, all overridable via env vars (see `docker-compose.yml`):
- `LDAP_ENABLED`: Enable LDAP auth (default `true`; `false` = mock login mode)
- `ADMIN_USERS`: Comma-separated admin usernames
- `JWT_SECRET`: JWT signing key
- `UPLOAD_PATH`: File upload directory (default `/app/uploads`)
- `QWEN_API_KEY`: Qwen VL API key for OCR amount recognition
- `QWEN_MODEL`: Qwen VL model name (default `qwen-vl-plus`)
- `QWEN_ENDPOINT`: DashScope API endpoint
