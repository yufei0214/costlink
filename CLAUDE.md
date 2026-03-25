# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CostLink is a VPN expense reimbursement system with LDAP authentication, OCR amount extraction, and admin approval/payment workflow.

- Frontend: Vue 3 + TypeScript + Vite + Element Plus
- Backend: Spring Boot 3.2 (Java 17) + Spring Security + MyBatis-Plus
- Database: MySQL 8.0
- Deployment: Docker Compose

## Development Commands

### Full stack (Docker)
```bash
docker-compose up -d                    # Start all services
docker-compose up -d --build            # Rebuild and start
docker-compose logs -f                  # Tail all service logs
docker-compose down                     # Stop services
```

### Backend (`costlink-backend/`)
```bash
docker-compose up -d mysql              # Start MySQL first
cd costlink-backend
mvn spring-boot:run                     # Run backend on :8080
mvn clean package                       # Build JAR
mvn test                                # Run all tests
mvn test -Dtest=ClassName               # Run single test class
mvn test -Dtest=ClassName#methodName    # Run single test method
```

### Frontend (`costlink-frontend/`)
```bash
cd costlink-frontend
npm install
npm run dev                             # Dev server on :5173 (proxies /api to backend)
npm run build                           # vue-tsc + vite build
npm run preview                         # Preview production build
```

Notes:
- No frontend lint script is defined in `costlink-frontend/package.json`.
- No dedicated frontend test command/framework is configured.
- No backend test files exist yet. The `mvn test` commands above are listed for reference; Spring Boot Test dependency is available but no tests have been written.

## Runtime Topology

- Browser accesses frontend (Vite dev server in local dev; Nginx container in Docker).
- Frontend sends API requests to `/api` via a centralized axios client (`costlink-frontend/src/api/index.ts`).
- Vite dev server proxies `/api` to `http://localhost:8080` (`costlink-frontend/vite.config.ts`).
- Backend exposes REST APIs under `/api/**` and persists data via MyBatis-Plus to MySQL.
- Uploaded images are stored on disk (`UPLOAD_PATH`) and served from `/api/uploads/**`.

## Backend Architecture (`com.costlink`)

- `controller/`: Domain APIs (`AuthController`, `UserController`, `ReimbursementController`, `AdminController`).
- `service/`: Core business logic (LDAP/local auth, OCR amount extraction, reimbursement lifecycle, user profile/account).
- `mapper/` + `entity/`: MyBatis-Plus persistence mappings for core tables (`t_user`, `t_reimbursement`, `t_reimbursement_image`, `t_admin_config`). No XML mapper files — mappers use `BaseMapper<T>` auto-CRUD plus `@Select` annotations for complex joins (see `ReimbursementMapper`). Note: `application.yml` declares `mapper-locations: classpath:mapper/*.xml` as a fallback, but no XML files exist.
- `dto/`: Request/response DTOs with `@Valid` annotations. All API responses wrapped in `ApiResponse<T>` (code, message, data).
- `config/`: JWT token provider/filter, Spring Security filter chain, CORS, MyBatis-Plus pagination.
- `exception/`: `GlobalExceptionHandler` (`@RestControllerAdvice`) — `BusinessException` → 400, validation errors → 400, auth errors → 403, all others → 500.

### Backend Conventions
- Entities use Lombok (`@Data`, `@TableName`, `@TableId(type = IdType.AUTO)`).
- Auto-fill timestamps via `@TableField(fill = FieldFill.INSERT)` / `FieldFill.INSERT_UPDATE`.
- Controllers receive authenticated user via `@AuthenticationPrincipal UserPrincipal`.
- MyBatis-Plus pagination is **1-indexed** (page 1 = first page), uses `LambdaQueryWrapper` for type-safe queries.
- Snake_case DB columns auto-map to camelCase Java fields (`map-underscore-to-camel-case: true`).

## Frontend Architecture

- `src/api/index.ts`: Single API layer; request interceptor injects JWT, response interceptor handles 401 by clearing token and redirecting to `/login`.
- `src/router/index.ts`: Route guards using `requiresAuth` and `requiresAdmin` meta fields. Default route `/` redirects to `/reimbursement`. Admin-only routes: `/dashboard`, `/management`.
- `src/stores/user.ts`: Pinia auth/user state.
- `src/views/*`: User pages (apply/profile/reimbursement) and admin pages (dashboard/management).
- Path alias: `@` maps to `src/` (configured in `vite.config.ts`). All imports use `@/` prefix.

## Security and Auth Model

- Backend is stateless JWT (`SecurityConfig` + `JwtAuthenticationFilter`).
- Public endpoints: `/api/auth/login`, `/api/auth/logout`, `/api/uploads/**`.
- `/api/admin/**` requires `ROLE_ADMIN`; all other `/api/**` endpoints require authentication.
- Admin role is assigned by username membership in `ADMIN_USERS`.
- LDAP supports:
  - simple bind (`LDAP_USER_DN_PATTERN`)
  - service-account search/bind (`LDAP_BIND_DN`, `LDAP_BIND_PASSWORD`, search filter/base)
- Local/mock login is enabled when `LDAP_ENABLED=false`. Note: `application.yml` defaults `LDAP_ENABLED` to `true`; `docker-compose.yml` also sets it to `true`. Override to `false` for local dev without LDAP.

## Business Flow Essentials

- Reimbursement status lifecycle: `PENDING -> CONFIRMED -> PAID` or `PENDING -> REJECTED`.
- Typical user flow: maintain payout account -> upload proof images -> OCR amount extraction (manual correction allowed) -> submit reimbursement.
- Typical admin flow: review/confirm or reject -> mark paid -> optional batch export of paid record images as ZIP -> Excel export of filtered records (Apache POI).
- Reimbursement uses `reimbursement_month` (VARCHAR(7), format `yyyy-MM`) instead of date ranges. Users also provide an optional `remark` (VARCHAR(500)).

## Important Configuration

Backend config file: `costlink-backend/src/main/resources/application.yml` (env vars override defaults).

Key variables:
- Database: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- Auth/admin: `JWT_SECRET`, `LDAP_ENABLED`, `LDAP_URL`, `LDAP_BASE`, `LDAP_USER_DN_PATTERN`, `LDAP_BIND_DN`, `LDAP_BIND_PASSWORD`, `ADMIN_USERS`
- OCR (Qwen VL): `QWEN_API_KEY`, `QWEN_MODEL`, `QWEN_ENDPOINT`, `QWEN_TIMEOUT`
- Uploads: `UPLOAD_PATH`

Upload limits:
- 10MB per file
- 50MB per request

## Data and Local Access Notes

- Initial schema/data is bootstrapped from `init/init.sql` in Docker MySQL startup.
- `init/migrate.sql` is a safe incremental migration script (idempotent ALTERs) for upgrading existing deployments — it adds `reimbursement_month` and `remark` columns and drops the old `vpn_start_date`/`vpn_end_date` columns.
- With local/mock auth (`LDAP_ENABLED=false`), README-documented default admin is `admin / admin123`.
- In mock mode, first login for a new username can create a local user record automatically.

## Docker Build Notes

- Backend Dockerfile uses Aliyun Maven mirror (`maven.aliyun.com`) for faster builds in China.
- Frontend Dockerfile uses `registry.npmmirror.com` npm mirror.
- Both use multi-stage builds (builder → slim runtime).
- Frontend production: Nginx Alpine serves static files, proxies `/api/` to backend, handles Vue Router history mode via `try_files`. Config at `costlink-frontend/nginx.conf`.

## Non-Obvious Architectural Decisions

- **CORS allows all origins** — should be restricted for production.
- **Admin role is computed at login** from `ADMIN_USERS` env var, not stored as a DB flag. Changing admin list requires affected users to re-login.
- **LDAP attributes synced on every login** — display names and emails stay fresh from LDAP without manual updates.
- **Images stored on filesystem** (`UPLOAD_PATH`), not in DB — Docker volume mounts are required for persistence.
- **OCR is best-effort** — returns `null` if Qwen API is not configured or image is unrecognizable; frontend allows manual amount entry as fallback.
- **DB foreign keys with CASCADE** — deleting a user cascades to their reimbursements and admin config; deleting a reimbursement cascades to its images. `paid_by` uses `SET NULL` on admin deletion.
- **Excel export uses Apache POI 5.2.5** — added for admin batch export of reimbursement records with filtering support.
- **No CI/CD pipeline** — no GitHub Actions, GitLab CI, or other automation is configured.

## Documentation Drift to Watch

- Root `README.md` still mentions Tesseract, but backend runtime config uses Qwen-related env vars (`QWEN_*`). If OCR behavior/config differs, trust backend code and `application.yml` over README text.
