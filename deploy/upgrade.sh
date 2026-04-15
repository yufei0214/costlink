#!/usr/bin/env bash
# =====================================================================
# CostLink 生产升级脚本 (2026-04-15 所属组功能)
#
# 执行动作:
#   1. 前置检查 (docker / docker-compose / SQL 脚本存在)
#   2. 备份数据库 (mysqldump -> ./backups/costlink-<ts>.sql.gz)
#   3. 执行 init/upgrade_20260415_department.sql (幂等)
#   4. 重建并重启 backend / frontend 容器
#   5. 健康检查 (backend 8080 返回 403=正常, frontend 返回 2xx/3xx)
#
# 假设:
#   - 生产部署方式为 docker-compose, 服务名: mysql / backend / frontend
#   - 本脚本在项目根目录 (含 docker-compose.yml) 执行
#
# 可覆盖的环境变量:
#   MYSQL_CONTAINER=costlink-mysql
#   MYSQL_DB=costlink
#   MYSQL_USER=costlink
#   MYSQL_PASSWORD=costlink123
#   BACKEND_HEALTH_URL=http://localhost:8080/api/auth/me
#   FRONTEND_HEALTH_URL=http://localhost/       # 容器内 nginx 映射
#   COMPOSE_CMD="docker-compose"                # 或 "docker compose"
#   SKIP_BACKUP=0                                # 1=跳过备份 (不推荐)
# =====================================================================
set -euo pipefail

# ---- Defaults ----
MYSQL_CONTAINER="${MYSQL_CONTAINER:-costlink-mysql}"
MYSQL_DB="${MYSQL_DB:-costlink}"
MYSQL_USER="${MYSQL_USER:-costlink}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-costlink123}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://localhost:8080/api/auth/me}"
FRONTEND_HEALTH_URL="${FRONTEND_HEALTH_URL:-http://localhost/}"
COMPOSE_CMD="${COMPOSE_CMD:-docker-compose}"
SKIP_BACKUP="${SKIP_BACKUP:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SQL_FILE="${PROJECT_ROOT}/init/upgrade_20260415_department.sql"
BACKUP_DIR="${SCRIPT_DIR}/backups"
TS="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/costlink-${TS}.sql.gz"

log()  { printf "\033[1;34m[%s]\033[0m %s\n" "$(date +%H:%M:%S)" "$*"; }
ok()   { printf "\033[1;32m[%s] ✓\033[0m %s\n" "$(date +%H:%M:%S)" "$*"; }
warn() { printf "\033[1;33m[%s] !\033[0m %s\n" "$(date +%H:%M:%S)" "$*" >&2; }
die()  { printf "\033[1;31m[%s] ✗\033[0m %s\n" "$(date +%H:%M:%S)" "$*" >&2; exit 1; }

confirm() {
    local prompt="${1:-Continue?}"
    local reply
    read -r -p "${prompt} [y/N]: " reply
    [[ "${reply}" =~ ^[Yy]$ ]] || die "User aborted."
}

# ---- 1) Pre-flight ----
log "Pre-flight checks"
command -v docker >/dev/null 2>&1 || die "docker not found in PATH"
if ! ${COMPOSE_CMD} version >/dev/null 2>&1; then
    die "'${COMPOSE_CMD}' not working; set COMPOSE_CMD=\"docker compose\" if using plugin"
fi
[[ -f "${SQL_FILE}" ]] || die "SQL script missing: ${SQL_FILE}"
[[ -f "${PROJECT_ROOT}/docker-compose.yml" ]] || die "docker-compose.yml not found at ${PROJECT_ROOT}"
docker ps --format '{{.Names}}' | grep -qx "${MYSQL_CONTAINER}" \
    || die "MySQL container '${MYSQL_CONTAINER}' is not running"
ok "Pre-flight passed"

log "Target environment:"
cat <<EOF
  project root : ${PROJECT_ROOT}
  mysql        : ${MYSQL_CONTAINER} (db=${MYSQL_DB}, user=${MYSQL_USER})
  sql script   : ${SQL_FILE}
  backup file  : ${BACKUP_FILE} (skip=${SKIP_BACKUP})
  compose cmd  : ${COMPOSE_CMD}
EOF
confirm "Proceed with upgrade on this environment?"

# ---- 2) Backup ----
if [[ "${SKIP_BACKUP}" = "1" ]]; then
    warn "SKIP_BACKUP=1, skipping database backup"
else
    log "Backing up database..."
    mkdir -p "${BACKUP_DIR}"
    docker exec -i "${MYSQL_CONTAINER}" \
        mysqldump --single-transaction --quick --routines --triggers \
        -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DB}" \
        2>/dev/null | gzip > "${BACKUP_FILE}"
    [[ -s "${BACKUP_FILE}" ]] || die "Backup failed or empty: ${BACKUP_FILE}"
    ok "Backup written: ${BACKUP_FILE} ($(du -h "${BACKUP_FILE}" | cut -f1))"
fi

# ---- 3) Run SQL upgrade ----
log "Running upgrade SQL (idempotent)..."
docker exec -i "${MYSQL_CONTAINER}" \
    mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DB}" \
    < "${SQL_FILE}" 2>&1 | grep -v "Using a password" || true

# Verify
DEPT_COL=$(docker exec -i "${MYSQL_CONTAINER}" \
    mysql -N -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DB}" \
    -e "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA='${MYSQL_DB}' AND TABLE_NAME='t_user' AND COLUMN_NAME='department';" \
    2>/dev/null | tr -d '[:space:]')
[[ "${DEPT_COL}" = "1" ]] || die "Post-upgrade check failed: t_user.department not found"
ok "Schema upgrade verified (t_user.department exists)"

# ---- 4) Rebuild & restart app ----
cd "${PROJECT_ROOT}"
log "Rebuilding backend & frontend images..."
${COMPOSE_CMD} build backend frontend

log "Recreating containers..."
${COMPOSE_CMD} up -d backend frontend

# ---- 5) Health check ----
log "Waiting for services to be healthy..."
for i in $(seq 1 30); do
    BE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "${BACKEND_HEALTH_URL}" || echo "000")
    # Backend returns 403 (unauthenticated) when healthy; accept 200/401/403 as "alive"
    case "${BE_CODE}" in
        200|401|403) ok "Backend up (HTTP ${BE_CODE})"; break ;;
        *) [[ "${i}" = "30" ]] && die "Backend not healthy after 30 attempts (last=${BE_CODE})" ;;
    esac
    sleep 2
done

for i in $(seq 1 15); do
    FE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "${FRONTEND_HEALTH_URL}" || echo "000")
    case "${FE_CODE}" in
        2*|3*) ok "Frontend up (HTTP ${FE_CODE})"; break ;;
        *) [[ "${i}" = "15" ]] && warn "Frontend health check inconclusive (last=${FE_CODE}); verify manually" ;;
    esac
    sleep 2
done

ok "Upgrade complete."
if [[ "${SKIP_BACKUP}" != "1" ]]; then
    cat <<EOF

Rollback (if needed):
  gunzip -c ${BACKUP_FILE} | docker exec -i ${MYSQL_CONTAINER} \\
      mysql -u${MYSQL_USER} -p<password> ${MYSQL_DB}
  # then redeploy the previous backend/frontend image tags
EOF
fi
