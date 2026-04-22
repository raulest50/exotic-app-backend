#!/usr/bin/env bash
set -euo pipefail

log() {
  local timestamp
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '%s %s %s\n' "$timestamp" "${MCP_LOG_PREFIX:-[MCP]}" "$*"
}

log_tool_status() {
  local tool_name="$1"
  shift

  if command -v "$tool_name" >/dev/null 2>&1; then
    log "$tool_name disponible en $(command -v "$tool_name")"
    "$@" 2>&1 | while IFS= read -r line; do
      log "$tool_name: $line"
    done
  else
    log "WARN: $tool_name no esta disponible en el runtime."
  fi
}

normalize_bool() {
  printf '%s' "${1:-false}" | tr '[:upper:]' '[:lower:]'
}

MCP_DIR="/app/MCP_EPOINT"
MCP_ENABLED="$(normalize_bool "${ENABLE_MCP:-false}")"

log "PATH=$PATH"
log_tool_status pg_dump pg_dump --version
log_tool_status pg_restore pg_restore --version
log_tool_status node node --version
log_tool_status npm npm --version

if [[ -n "${MCP_COMMAND:-}" ]]; then
  log "MCP_COMMAND definido."
else
  log "WARN: MCP_COMMAND no esta definido."
fi

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
