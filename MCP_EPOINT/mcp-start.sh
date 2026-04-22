#!/usr/bin/env bash
set -euo pipefail

log() {
  local timestamp
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '%s %s %s\n' "$timestamp" "${MCP_LOG_PREFIX:-[MCP]}" "$*"
}

MCP_COMMAND_VALUE="${MCP_COMMAND:-}"
MCP_BIND_HOST_VALUE="${MCP_BIND_HOST:-127.0.0.1}"
MCP_PORT_VALUE="${MCP_PORT:-8765}"
MCP_PID_FILE_VALUE="${MCP_PID_FILE:-/tmp/mcp.pid}"
MCP_STARTUP_GRACE_SECONDS_VALUE="${MCP_STARTUP_GRACE_SECONDS:-2}"

if [[ -z "$MCP_COMMAND_VALUE" ]]; then
  log "WARN: ENABLE_MCP=true pero MCP_COMMAND esta vacio. No se iniciara el MCP."
  exit 1
fi

if [[ -f "$MCP_PID_FILE_VALUE" ]]; then
  existing_pid="$(cat "$MCP_PID_FILE_VALUE" 2>/dev/null || true)"
  if [[ -n "${existing_pid:-}" ]] && kill -0 "$existing_pid" 2>/dev/null; then
    log "Ya existe un proceso MCP corriendo con PID $existing_pid."
    exit 0
  fi
fi

log "Iniciando MCP con bind ${MCP_BIND_HOST_VALUE}:${MCP_PORT_VALUE}."

(
  export MCP_BIND_HOST="$MCP_BIND_HOST_VALUE"
  export MCP_PORT="$MCP_PORT_VALUE"
  exec bash -lc "$MCP_COMMAND_VALUE"
) &

mcp_pid=$!
echo "$mcp_pid" > "$MCP_PID_FILE_VALUE"

sleep "$MCP_STARTUP_GRACE_SECONDS_VALUE"

if ! kill -0 "$mcp_pid" 2>/dev/null; then
  log "WARN: El proceso MCP termino prematuramente despues del arranque."
  exit 1
fi

log "Proceso MCP lanzado en background con PID $mcp_pid."
