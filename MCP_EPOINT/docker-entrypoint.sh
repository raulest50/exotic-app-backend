#!/usr/bin/env bash
set -euo pipefail

log() {
  local timestamp
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '%s %s %s\n' "$timestamp" "${MCP_LOG_PREFIX:-[MCP]}" "$*"
}

normalize_bool() {
  printf '%s' "${1:-false}" | tr '[:upper:]' '[:lower:]'
}

MCP_DIR="/app/MCP_EPOINT"
MCP_ENABLED="$(normalize_bool "${ENABLE_MCP:-false}")"

if [[ "$MCP_ENABLED" == "true" ]]; then
  log "ENABLE_MCP=true. Intentando iniciar MCP auxiliar."

  if "$MCP_DIR/mcp-start.sh"; then
    if "$MCP_DIR/mcp-healthcheck.sh"; then
      log "MCP disponible en ${MCP_BIND_HOST:-127.0.0.1}:${MCP_PORT:-8765}."
    else
      log "WARN: El MCP intento arrancar pero no supero el healthcheck. El backend continuara sin bloquearse."
    fi
  else
    log "WARN: El MCP no pudo iniciarse. El backend continuara sin bloquearse."
  fi
else
  log "ENABLE_MCP=false. Se iniciara solo el backend Spring Boot."
fi

exec "$@"
