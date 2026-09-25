#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# PreonsURL Production Deployment
# ============================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

COMPOSE="docker compose"

DOMAINS=(
    "secure.indexrender.io"
    "go.indexrender.io"
    "psecure.indexrender.io"
)

HTTP_CONFIG="nginx/conf.d/http.conf"
HTTPS_CONFIG="nginx/conf.d/https.conf"
ACTIVE_CONFIG="nginx/conf.d/indexrender.conf"

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

[[ -f "$HTTP_CONFIG" ]] \
    || fail "$HTTP_CONFIG not found."

[[ -f "$HTTPS_CONFIG" ]] \
    || fail "$HTTPS_CONFIG not found."

mkdir -p nginx/conf.d

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
    CERTBOT_EMAIL
)

for VAR in "${REQUIRED_VARS[@]}"; do

    if [[ -z "${!VAR:-}" ]]; then
        fail "$VAR is missing from .env."
    fi

done

echo "Environment OK."

# ============================================================
# Validate Compose
# ============================================================

log "Validating Docker Compose..."

$COMPOSE config >/dev/null

echo "Docker Compose configuration OK."

# ============================================================
# Make sure old conflicting containers don't block deployment
# ============================================================

log "Checking existing containers..."

# Do NOT use docker compose down here.
# This keeps existing containers running until replacements
# are ready and, importantly, does not touch volumes.

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

log "Building and starting Spring Boot services..."

$COMPOSE up -d --build \
    app \
    serve \
    psecureserve

# ============================================================
# Wait for containers
# ============================================================

log "Waiting for application containers..."

sleep 5

# ============================================================
# Show application status
# ============================================================

$COMPOSE ps

# ============================================================
# Check application containers are running
# ============================================================

for SERVICE in app serve psecureserve; do

    STATUS="$($COMPOSE ps --status running --services | grep -x "$SERVICE" || true)"

    if [[ -z "$STATUS" ]]; then
        echo
        echo "WARNING: $SERVICE is not running."
        echo
        $COMPOSE logs --tail=80 "$SERVICE" || true
        echo
        fail "$SERVICE failed to start."
    fi

done

echo "All Spring Boot services are running."

# ============================================================
# Determine SSL state
# ============================================================

log "Checking SSL certificates..."

CERTS_EXIST=true

for DOMAIN in "${DOMAINS[@]}"; do

    if ! $COMPOSE exec -T nginx \
        test -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" \
        >/dev/null 2>&1
    then

        CERTS_EXIST=false
        break

    fi

done

# ============================================================
# FIRST DEPLOYMENT
# ============================================================

if [[ "$CERTS_EXIST" == "false" ]]; then

    log "SSL certificates were not found."
    log "Using HTTP configuration for Let's Encrypt."

    cp "$HTTP_CONFIG" "$ACTIVE_CONFIG"

    # Start Nginx with HTTP configuration.
    $COMPOSE up -d nginx

    sleep 3

    log "Testing HTTP Nginx configuration..."

    $COMPOSE exec -T nginx nginx -t

    $COMPOSE exec -T nginx nginx -s reload || true

    # ========================================================
    # Check DNS / HTTP accessibility
    # ========================================================

    log "Requesting Let's Encrypt certificates..."

    DOMAIN_ARGS=()

    for DOMAIN in "${DOMAINS[@]}"; do
        DOMAIN_ARGS+=("-d" "$DOMAIN")
    done

    $COMPOSE run --rm certbot certonly \
        --webroot \
        --webroot-path=/var/www/certbot \
        --email "$CERTBOT_EMAIL" \
        --agree-tos \
        --no-eff-email \
        "${DOMAIN_ARGS[@]}"

    log "Let's Encrypt certificates successfully created."

    # ========================================================
    # Switch to HTTPS
    # ========================================================

    log "Switching Nginx to HTTPS configuration..."

    cp "$HTTPS_CONFIG" "$ACTIVE_CONFIG"

    $COMPOSE exec -T nginx nginx -t

    $COMPOSE exec -T nginx nginx -s reload

    log "HTTPS configuration enabled."

else

    # ========================================================
    # EXISTING DEPLOYMENT
    # ========================================================

    log "Existing SSL certificates detected."

    log "Using HTTPS configuration."

    cp "$HTTPS_CONFIG" "$ACTIVE_CONFIG"

    # Start Nginx if it isn't running.
    $COMPOSE up -d nginx

    sleep 3

    log "Testing Nginx configuration..."

    $COMPOSE exec -T nginx nginx -t

    log "Reloading Nginx..."

    $COMPOSE exec -T nginx nginx -s reload

fi

# ============================================================
# Final certificate verification
# ============================================================

log "Verifying SSL certificates..."

for DOMAIN in "${DOMAINS[@]}"; do

    if $COMPOSE exec -T nginx \
        test -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem"
    then

        echo "✓ $DOMAIN certificate exists"

    else

        echo "WARNING: $DOMAIN certificate not found"

    fi

done

# ============================================================
# Final Nginx test
# ============================================================

log "Final Nginx configuration test..."

$COMPOSE exec -T nginx nginx -t

# ============================================================
# Final status
# ============================================================

log "Deployment status..."

$COMPOSE ps

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
echo "Nginx:"
echo "    HTTP  -> HTTPS"
echo "    Ports -> 80 / 443"
echo
echo "Database:"
echo "    MariaDB -> internal Docker network"
echo
echo "============================================================"