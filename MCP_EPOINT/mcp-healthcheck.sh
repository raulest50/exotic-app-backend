#!/usr/bin/env bash
set -euo pipefail

log() {
  local timestamp
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '%s %s %s\n' "$timestamp" "${MCP_LOG_PREFIX:-[MCP]}" "$*"
}

MCP_BIND_HOST_VALUE="${MCP_BIND_HOST:-127.0.0.1}"
MCP_PORT_VALUE="${MCP_PORT:-8765}"
MCP_PID_FILE_VALUE="${MCP_PID_FILE:-/tmp/mcp.pid}"
MCP_HEALTHCHECK_TIMEOUT_SECONDS_VALUE="${MCP_HEALTHCHECK_TIMEOUT_SECONDS:-5}"

if [[ -f "$MCP_PID_FILE_VALUE" ]]; then
  existing_pid="$(cat "$MCP_PID_FILE_VALUE" 2>/dev/null || true)"
  if [[ -n "${existing_pid:-}" ]] && ! kill -0 "$existing_pid" 2>/dev/null; then
    log "WARN: El PID registrado para el MCP ya no esta vivo."
    exit 1
  fi
fi

if MCP_BIND_HOST_VALUE="$MCP_BIND_HOST_VALUE" \
   MCP_PORT_VALUE="$MCP_PORT_VALUE" \
   MCP_HEALTHCHECK_TIMEOUT_SECONDS_VALUE="$MCP_HEALTHCHECK_TIMEOUT_SECONDS_VALUE" \
   node -e "
const net = require('net');
const host = process.env.MCP_BIND_HOST_VALUE || '127.0.0.1';
const port = Number(process.env.MCP_PORT_VALUE || '8765');
const timeoutMs = Number(process.env.MCP_HEALTHCHECK_TIMEOUT_SECONDS_VALUE || '5') * 1000;
const socket = net.createConnection({ host, port });
socket.setTimeout(timeoutMs);
socket.on('connect', () => { socket.end(); process.exit(0); });
socket.on('timeout', () => { socket.destroy(); process.exit(1); });
socket.on('error', () => process.exit(1));
"; then
  log "Healthcheck MCP OK en ${MCP_BIND_HOST_VALUE}:${MCP_PORT_VALUE}."
  exit 0
fi

log "WARN: Healthcheck MCP fallo en ${MCP_BIND_HOST_VALUE}:${MCP_PORT_VALUE}."
exit 1
