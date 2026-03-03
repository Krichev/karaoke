#!/bin/bash
# Backend Deployment Script
# Used by GitHub Actions CI/CD pipeline

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

COMPOSE_FILE="${1:-docker-compose.prod.yml}"
ACTION="${2:-deploy}"

# Pre-flight checks
preflight_check() {
    log_info "Running pre-flight checks..."
    
    if ! docker network inspect challenger_network &>/dev/null; then
        log_error "challenger_network does not exist! Run Ansible infrastructure setup first."
        exit 1
    fi
    
    if [ ! -f ".env" ]; then
        log_error ".env file not found! CI/CD should create this."
        exit 1
    fi
    
    log_info "Pre-flight checks passed."
}

deploy() {
    log_info "Deploying with ${COMPOSE_FILE}..."
    
    preflight_check
    
    # Pull latest image
    log_info "Pulling latest image..."
    docker compose -f "${COMPOSE_FILE}" pull
    
    # Stop existing container gracefully
    log_info "Stopping existing container (if any)..."
    docker compose -f "${COMPOSE_FILE}" down --timeout 30 || true
    
    # Start new container
    log_info "Starting new container..."
    docker compose -f "${COMPOSE_FILE}" up -d
    
    # Wait for health check
    log_info "Waiting for health check..."
    for i in {1..30}; do
        if docker compose -f "${COMPOSE_FILE}" ps | grep -q "(healthy)"; then
            log_info "Container is healthy!"
            docker compose -f "${COMPOSE_FILE}" ps
            exit 0
        fi
        echo "  Attempt $i/30 - waiting..."
        sleep 5
    done
    
    log_error "Health check failed after 30 attempts!"
    docker compose -f "${COMPOSE_FILE}" logs --tail=50
    exit 1
}

stop() {
    log_info "Stopping services..."
    docker compose -f "${COMPOSE_FILE}" down --timeout 30
    log_info "Services stopped."
}

logs() {
    docker compose -f "${COMPOSE_FILE}" logs -f --tail=100
}

status() {
    docker compose -f "${COMPOSE_FILE}" ps
}

case "${ACTION}" in
    deploy|start)
        deploy
        ;;
    stop)
        stop
        ;;
    logs)
        logs
        ;;
    status)
        status
        ;;
    *)
        echo "Usage: $0 <compose-file> {deploy|stop|logs|status}"
        echo ""
        echo "Examples:"
        echo "  $0 docker-compose.prod.yml deploy"
        echo "  $0 docker-compose.dev.yml deploy"
        echo "  $0 docker-compose.prod.yml logs"
        exit 1
        ;;
esac
