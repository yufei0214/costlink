# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CostLink is a VPN expense reimbursement system with LDAP authentication, OCR-based amount recognition, and an approval workflow. The system consists of a Vue 3 frontend and Spring Boot 3.2 backend communicating via REST APIs.

## Development Commands

### Full Stack (Docker)
```bash
docker-compose up -d                    # Start all services
docker-compose up -d --build            # Rebuild and start
docker-compose logs -f                  # View logs
docker-compose down                     # Stop services
```

### Backend (costlink-backend/)
```bash
docker-compose up -d mysql              # Start MySQL first
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
npm run dev                             # Dev server (port 5173)
npm run build                           # Production build
```

## Architecture

### Backend Structure (Java 17, Spring Boot 3.2)
- **Controllers**: REST endpoints in `controller/` - Auth, User, Reimbursement, Admin
- **Services**: Business logic in `service/` - handles transactions, validation, OCR
- **Mappers**: MyBatis-Plus data access in `mapper/` with XML mappings in `resources/mapper/`
- **DTOs**: Request/Response objects in `dto/` - API contracts
- **Entities**: JPA entities in `entity/` matching `t_*` database tables
- **Security**: JWT-based auth via `JwtTokenProvider` and `JwtAuthenticationFilter`

### Frontend Structure (Vue 3, TypeScript, Vite)
- **Views**: Page components in `views/` - Login, Apply, Reimbursement, Management, Dashboard
- **Router**: `router/index.ts` - auth guards via `meta.requiresAuth` and `meta.requiresAdmin`
- **Store**: Pinia store in `stores/user.ts` - user state and auth
- **API**: Centralized in `api/index.ts` - axios instance with JWT interceptors

### Data Flow
1. Frontend calls `/api/*` endpoints (proxied via Vite in dev, nginx in prod)
2. JWT token stored in localStorage, attached via axios interceptor
3. Backend validates JWT in filter, extracts UserPrincipal for auth
4. Services handle business logic, mappers persist to MySQL

### Key Business Logic
- **Reimbursement Status Flow**: PENDING → CONFIRMED → PAID (or PENDING → REJECTED)
- **Admin Detection**: Users in `ADMIN_USERS` env var get ADMIN role on login
- **OCR Service**: Tesseract extracts amounts from uploaded images
- **Image Export**: Paid records can be batch-exported as ZIP

## Database

Tables prefixed with `t_`: `t_user`, `t_reimbursement`, `t_reimbursement_image`, `t_admin_config`

Schema initialized via `init/init.sql`. Default admin: `admin` / `admin123`

## Configuration

Backend config via environment variables (see `docker-compose.yml` or `application.yml`):
- `LDAP_ENABLED`: Enable LDAP auth (false = mock login mode)
- `ADMIN_USERS`: Comma-separated admin usernames
- `JWT_SECRET`: JWT signing key
