# Cierre automático de OCM

La migración `V111` agrega a `orden_compra` la fecha de recepción completa y la auditoría del cierre. La directiva `CIERRE_AUTOMATICO_OCM` se crea desactivada. No se rellenan fechas históricas ni se cierran órdenes durante la migración.

## Comportamiento

- `DESACTIVADO`: conserva el cierre manual. Se sigue registrando cuándo una recepción pasa de incompleta a completa.
- `RECEPCION_COMPLETA`: cierra la OCM al completar todos sus materiales.
- `PLAZO`: cierra después de N días enteros positivos desde la hora de recepción completa, usando `America/Bogota`. Un proceso revisa los vencimientos cada minuto y procesa hasta 100 órdenes por ciclo.
- Cada paso de desactivado a activado fija un nuevo `activadoDesde`, calculado por el servidor. Solo se incluyen recepciones completadas desde ese corte. Cambiar el modo o los días mientras sigue activado conserva el corte y aplica la configuración actual a las OCM abiertas elegibles.
- La recepción completa se verifica por material, agrupando renglones repetidos y sumando con `BigDecimal` las cantidades positivas recibidas por OCM/COMPRA/GENERAL. Un exceso en un material no compensa el faltante de otro. El porcentaje agregado de la bandeja es solamente informativo.
- Una edición de cantidades que vuelve incompleta una OCM pendiente borra la fecha; si luego vuelve a completarse registra una nueva. Una OCM histórica que ya estaba completa y no tiene fecha no recibe una fecha inventada por una consulta, edición irrelevante ni cierre masivo.
- El botón administrativo de cierre manual incluye **todas** las OCM pendientes completas, incluso históricas y aquellas que esperan un plazo. Se confirman IDs explícitos, en solicitudes de hasta 100. Cada OCM se revalida; la respuesta distingue cerradas, omitidas y fallidas. No se añaden órdenes nuevas a la selección durante el cierre.
- Se conserva el cierre manual individual con recepción parcial y al menos una transacción, así como el límite de recepciones configurado por proveedor.
- El cierre registra fecha, origen y usuario real cuando es manual; el automático no inventa un usuario. Repetir un cierre ya realizado no cambia la auditoría. No se generan movimientos, pagos ni asientos contables por cerrar.
- Una solicitud que quedó abierta en otra sesión no puede reabrir ni cancelar una OCM ya cerrada. El backend hace cumplir el estado terminal que ya presenta la interfaz de Compras.

La configuración se bloquea antes que la OCM. El registro de recepción, su fecha de completitud y el cierre inmediato participan en la misma transacción. El proceso programado y el cierre manual masivo usan una transacción independiente por orden, con bloqueo de la OCM. El proceso programado vuelve a leer la directiva bloqueada antes de cerrar: no usa una configuración obsoleta obtenida al seleccionar candidatos.

## API e interfaz

Prefijo `/api/super-master-directives/ocm-cierre`:

| Método y ruta | Contrato |
| --- | --- |
| `GET /config` | `{modo, dias, activadoDesde}` |
| `PUT /config` | Recibe `{modo, dias}`; devuelve la configuración persistida. El corte no se recibe del cliente. |
| `GET /completas` | Lista de IDs, proveedor, emisión, recepción completa y cierre previsto. La interfaz permite revisar todas las páginas. |
| `POST /cerrar-completas` | Recibe `{ordenCompraIds: [...]}`; devuelve `{cerradas, omitidas, fallidas}`. |

`GET /ingresos_almacen/ocm/{ordenCompraId}/estado-recepcion` devuelve el estado de recepción y auditoría, incluso si la OCM ya está cerrada. Permite actualizar la confirmación del asistente sin modificar el contrato del POST de recepción. Si falla esa consulta, la recepción guardada sigue mostrándose como exitosa.

Modificar la configuración, previsualizar y ejecutar el cierre masivo requiere `super_master`, o `master` con `ENABLE_MASTER_SUPERMASTER_DIRECTIVES_ACCESS` activada. El endpoint genérico de directivas rechaza cambios a esta clave. Las consultas de configuración y estado requieren autenticación mediante la configuración de seguridad existente.

La sección está en **Directivas Maestras → General**, con botón propio para guardar de forma atómica. El detalle de Compras y el asistente de ingreso muestran recepción completa, cierre previsto y auditoría del cierre.

## Validación pendiente

Se añadieron pruebas unitarias de cantidades decimales, materiales repetidos, fechas/corte, plazo, desactivación, permisos, reintentos, cierre masivo parcial y coordinación de recepción. No se ejecutaron builds, pruebas ni despliegues en este equipo, conforme a las instrucciones del workspace.

Validar primero compilación y pruebas en el equipo de desarrollo autorizado; después desplegar backend y frontend a **staging**, con la directiva inicialmente apagada. Revisar los contratos nuevos en `exotic-app-e2e`; los endpoints anteriores conservan rutas y payloads.

Casos de aceptación en staging:

1. Directiva apagada: registrar recepción parcial y completa; comprobar que solo la segunda registra fecha y ninguna cierra automáticamente. Confirmar que el límite por proveedor conserva su comportamiento.
2. Completar una OCM antes de activar. Activar: debe seguir abierta y aparecer en la previsualización manual. Otra OCM completada después sí debe cerrarse en modo inmediato.
3. Con plazo de un día, verificar la hora exacta prevista. Antes de vencer permanece abierta; después se cierra durante un ciclo. Para una prueba acelerada de fecha en staging se requiere autorización específica, no cambiar el reloj o datos de producción.
4. Aumentar la cantidad de una OCM completa que espera plazo: pasa a incompleta y limpia la fecha. Completar de nuevo: reinicia el plazo. Reducir cantidades hasta completarla también registra la transición real.
5. Cambiar plazo/modo mientras está activada; comprobar que conserva el corte. Desactivar y reactivar: las OCM completas anteriores al nuevo corte quedan para cierre manual.
6. Recepciones con varios materiales, varios lotes, renglones repetidos y fracciones que suman un entero. Revisar el porcentaje agregado para descartar multiplicación por joins y verificar la decisión de cierre por material.
7. Abrir dos sesiones sobre una misma OCM y competir recepción/cierre/edición. No debe duplicarse el cierre, sobrescribirse la auditoría ni aceptarse un ingreso posterior al cierre. Ante un error transaccional, verificar en PostgreSQL que recepción y fecha/cierre se revierten juntas.
8. Previsualizar un cierre masivo y modificar/cerrar una candidata desde otra sesión. Confirmar: omite las que ya no corresponden, conserva las demás, no incorpora candidatas nuevas. Verificar fallos parciales y reintentos.
9. Confirmar que el usuario normal no puede usar PUT ni el cierre masivo. Desactivar el acceso de `master`: se deniega; `super_master` conserva acceso.
10. Tras guardar, comprobar mensaje de éxito, estado de OCM y detalle en Compras. Interrumpir únicamente la consulta de estado: no debe sugerir que la recepción guardada falló. Verificar stock y contabilidad sin efectos adicionales por el cierre.
