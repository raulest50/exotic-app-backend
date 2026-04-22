# MCP_EPOINT

Carpeta central para el levantamiento y soporte operativo del MCP auxiliar en el contenedor del backend.

## Archivos

- `docker-entrypoint.sh`
  - entrypoint principal del contenedor
  - decide si iniciar el MCP segun `ENABLE_MCP`
  - luego arranca la app Spring Boot

- `mcp-start.sh`
  - intenta iniciar el MCP en background
  - usa `MCP_COMMAND`, `MCP_BIND_HOST` y `MCP_PORT`

- `mcp-healthcheck.sh`
  - verifica si el MCP esta escuchando en localhost
  - si falla, deja warning y permite que el backend continue

- `mcp.env.example`
  - ejemplo de variables de entorno para staging

## Flujo recomendado en staging

1. Definir `ENABLE_MCP=true`.
2. Definir `MCP_COMMAND` con el comando real del MCP.
3. Mantener `MCP_BIND_HOST=127.0.0.1`.
4. Acceder al MCP solo mediante tunel SSH.

## Seguridad

- No exponer el puerto MCP publicamente.
- En produccion dejar `ENABLE_MCP=false`.
- El backend puede seguir arriba aunque el MCP falle al arrancar.
