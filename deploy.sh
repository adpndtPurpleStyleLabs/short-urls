#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# PreonsURL Production Deployment
# ============================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

COMPOSE="docker compose"

# Existing reverse proxy container
NGINX_CONTAINER="reachly-nginx"

# ============================================================
# Logging
# ============================================================

log() {
    echo
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo
    echo "ERROR: $1"
    echo
}

fail() {
    error "$1"
    exit 1
}

# ============================================================
# Error handler
# ============================================================

trap 'error "Deployment failed at line $LINENO."' ERR

# ============================================================
# Root check
# ============================================================

if [[ "$EUID" -ne 0 ]]; then
    fail "This script must be run as root."
fi

# ============================================================
# Required files
# ============================================================

log "Checking required files..."

[[ -f ".env" ]] \
    || fail ".env not found."

[[ -f "docker-compose.yml" ]] \
    || fail "docker-compose.yml not found."

[[ -f "schema-mariadb.sql" ]] \
    || fail "schema-mariadb.sql not found."

echo "Required files OK."

# ============================================================
# Docker check
# ============================================================

log "Checking Docker..."

command -v docker >/dev/null 2>&1 \
    || fail "Docker is not installed."

$COMPOSE version >/dev/null 2>&1 \
    || fail "Docker Compose is not available."

echo "Docker OK."

# ============================================================
# Load .env
# ============================================================

log "Loading environment..."

set -a
source .env
set +a

# ============================================================
# Required environment variables
# ============================================================

REQUIRED_VARS=(
    MARIADB_ROOT_PASSWORD
    MARIADB_DATABASE
    MARIADB_USER
    MARIADB_PASSWORD
    JWT_SECRET
    SHORTENER_SECRET
    MAILTRAP_API_TOKEN
)

for VAR in "${REQUIRED_VARS[@]}"; do

    if [[ -z "${!VAR:-}" ]]; then
        fail "$VAR is missing from .env."
    fi

done

echo "Environment OK."

# ============================================================
# Validate Docker Compose
# ============================================================

log "Validating Docker Compose..."

$COMPOSE config >/dev/null

echo "Docker Compose configuration OK."

# ============================================================
# Check existing reverse proxy
# ============================================================

log "Checking existing Nginx reverse proxy..."

if ! docker ps --format '{{.Names}}' | grep -qx "$NGINX_CONTAINER"; then
    fail "Existing $NGINX_CONTAINER container is not running."
fi

echo "✓ $NGINX_CONTAINER is running."

# ============================================================
# Start MariaDB
# ============================================================

log "Starting MariaDB..."

$COMPOSE up -d mariadb

# ============================================================
# Wait for MariaDB
# ============================================================

log "Waiting for MariaDB..."

DB_READY=false

for i in {1..60}; do

    if $COMPOSE exec -T mariadb \
        mariadb-admin ping \
        -h localhost \
        -u root \
        "-p${MARIADB_ROOT_PASSWORD}" \
        >/dev/null 2>&1
    then

        DB_READY=true
        echo "MariaDB is ready."
        break

    fi

    echo "Waiting for MariaDB... ($i/60)"
    sleep 2

done

if [[ "$DB_READY" != "true" ]]; then
    fail "MariaDB did not become ready."
fi

# ============================================================
# Start application services
# ============================================================

# Dynamically resolve git commit ref for docker build and runtime
COMMIT_REF="$(git rev-parse --short HEAD 2>/dev/null || echo '')"
export COMMIT_REF
export GIT_COMMIT="$COMMIT_REF"

log "Building and starting Spring Boot services (Commit Ref: ${COMMIT_REF:-unknown})..."

$COMPOSE up -d --build \
    app \
    serve \
    psecureserve

# ============================================================
# Wait for application containers
# ============================================================

log "Waiting for application containers..."

sleep 5

# ============================================================
# Check application containers
# ============================================================

for SERVICE in app serve psecureserve; do

    STATUS="$(
        $COMPOSE ps --status running --services |
        grep -x "$SERVICE" || true
    )"

    if [[ -z "$STATUS" ]]; then

        echo
        echo "WARNING: $SERVICE is not running."
        echo

        $COMPOSE logs --tail=100 "$SERVICE" || true

        echo

        fail "$SERVICE failed to start."
    fi

    echo "✓ $SERVICE is running."

done

# ============================================================
# Check Spring Boot logs for obvious startup failure
# ============================================================

log "Checking application startup..."

for SERVICE in app serve psecureserve; do

    if $COMPOSE logs --tail=30 "$SERVICE" 2>&1 |
        grep -qiE "APPLICATION FAILED TO START|BUILD FAILURE|Exception in thread"
    then

        echo
        echo "WARNING: Possible startup error detected in $SERVICE."
        echo

        $COMPOSE logs --tail=100 "$SERVICE" || true

        fail "$SERVICE reported a startup error."
    fi

done

echo "Spring Boot startup checks passed."

# ============================================================
# Reload existing Nginx
# ============================================================

log "Testing existing Nginx configuration..."

docker exec "$NGINX_CONTAINER" nginx -t

log "Reloading existing Nginx..."

docker exec "$NGINX_CONTAINER" nginx -s reload

echo "✓ Nginx configuration reloaded."

# ============================================================
# Final container status
# ============================================================

log "Final deployment status..."

$COMPOSE ps

echo
echo "Existing Nginx:"
docker ps \
    --filter "name=$NGINX_CONTAINER" \
    --format "  {{.Names}} -> {{.Status}}"

# ============================================================
# Final output
# ============================================================

echo
echo "============================================================"
echo "              PREONSURL DEPLOYMENT COMPLETE"
echo "============================================================"
echo

echo "Services:"
echo
echo "  App:"
echo "    https://secure.indexrender.io"
echo
echo "  Serve:"
echo "    https://go.indexrender.io"
echo
echo "  PSecure:"
echo "    https://psecure.indexrender.io"
echo

echo "Reverse Proxy:"
echo "    $NGINX_CONTAINER"
echo "    HTTP  -> HTTPS"
echo "    Ports -> 80 / 443"
echo

echo "Database:"
echo "    MariaDB -> internal Docker network"
echo

echo "============================================================"