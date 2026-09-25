#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

COMPOSE="docker compose"

echo "=========================================="
echo "      PreonsURL SSL Renewal"
echo "=========================================="

echo
echo "Checking certificates..."

$COMPOSE run --rm certbot renew --quiet

echo
echo "Testing Nginx..."

$COMPOSE exec -T nginx nginx -t

echo
echo "Reloading Nginx..."

$COMPOSE exec -T nginx nginx -s reload

echo
echo "SSL renewal check completed."