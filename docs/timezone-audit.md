# Timezone Audit

Zona horaria oficial del backend: `America/Bogota` (`UTC-5`).

Este documento inventaria los puntos donde el backend genera, persiste, serializa o usa fechas sensibles a timezone. La correccion aplicada en esta fase centraliza la hora del backend en Bogota, sin cambiar el tipo de columnas `timestamp without time zone` ya existentes.

## 1. Persistidos generados por Hibernate/JVM (`@CreationTimestamp`)

Estos campos dependen de la zona horaria efectiva de la JVM. En entornos remotos con JVM en UTC pueden quedar corridos si no se fija la zona global.

- `compras/OrdenCompraMateriales.fechaEmision`
- `compras/Proveedor.fechaRegistro`
- `compras/FacturaCompra.fechaCompra`
- `activos/fijos/compras/OrdenCompraActivo.fechaEmision`
- `activos/fijos/compras/FacturaCompraActivo.fechaCompra`
- `activos/fijos/gestion/IncorporacionActivoHeader.fechaIncorporacion`
- `activos/fijos/gestion/DocumentoBajaActivo.fechaBaja`
- `inventarios/Movimiento.fechaMovimiento`
- `inventarios/TransaccionAlmacen.fechaTransaccion`
- `produccion/OrdenProduccion.fechaCreacion`
- `produccion/SeguimientoOrdenArea.fechaCreacion`
- `producto/Producto.fechaCreacion`
- `ventas/Cliente.fechaRegistro`
- `ventas/FacturaVenta.fechaFactura`
- `ventas/OrdenVenta.fechaCreacion`

## 2. Persistidos generados explicitamente en Java

Estos casos no dependian de PostgreSQL sino de `LocalDateTime.now()` o `LocalDate.now()` en backend. En esta correccion fueron migrados para usar la fuente centralizada de tiempo del backend.

### Servicios

- `service/activos/fijos/ActivoFijoService`
  - `ActivoFijo.fechaCodificacion`
- `service/contabilidad/ContabilidadService`
  - `AsientoContable.fecha`
- `service/inventarios/MovimientosService`
  - lotes automaticos `Lote.productionDate`
  - lotes automaticos `Lote.expirationDate`
  - validaciones de stock relativas a `fechaMovimiento`
  - carpetas y nombres derivados de fecha actual
- `service/inventarios/IngresoTerminadosAlmacenService`
  - `Lote.productionDate`
  - fecha por defecto de vencimiento en plantilla Excel
  - cierre de OP usando timestamp generado por backend
- `service/master/configs/SuperMasterOpsService`
  - `SuperMasterVerificationCode.expiryDate`
- `service/produccion/SeguimientoOrdenAreaService`
  - `SeguimientoOrdenArea.fechaVisible`
  - `SeguimientoOrdenArea.fechaCompletado`
- `service/produccion/ProduccionService`
  - `OrdenProduccion.fechaFinal`
  - anio usado en generacion de lotes
- `service/contabilidad/DepreciacionScheduler`
  - `YearMonth` del proceso mensual

### Modelos y metodos estaticos

- `model/produccion/ruprocatdesigner/RutaProcesoCat`
  - `fechaCreacion`
  - `fechaModificacion`
- `model/organizacion/personal/DocTranDePersonal`
  - `fechaHora`
- `model/users/auth/PasswordResetToken`
  - `expiryDate`
  - validacion `isExpired()`
- `model/master/configs/SuperMasterVerificationCode`
  - validacion `isExpired()`

## 3. Generados por SQL/Postgres

Estos puntos usaban o todavia exponen `CURRENT_TIMESTAMP` o defaults SQL. La correccion de esta fase elimina el uso de SQL para la logica de negocio activa identificada, pero no modifica migraciones historicas.

### Corregido en codigo fuente

- `repo/produccion/OrdenProduccionRepo.updateEstadoOrdenById`
  - antes: `CURRENT_TIMESTAMP`
  - ahora: timestamp calculado en backend y enviado como parametro

### Defaults SQL que permanecen en migraciones historicas

- `db/migration/V015__add_ruta_proceso_cat_tables.sql`
  - `fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `fecha_modificacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `db/migration/V020__add_seguimiento_orden_area.sql`
  - `fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

Nota: estas tablas ya reciben valores desde JPA en el flujo normal, por lo que los defaults SQL no deberian ser la fuente principal en inserciones habituales.

## 4. No persistidos pero relevantes

No siempre escriben en PostgreSQL, pero si pueden exponer inconsistencias horarias hacia clientes, logs o procesos de seguridad.

- `dto/ErrorResponse.timestamp`
- `resource/productos/exceptions/CategoriaExceptions.ErrorResponse.timestamp`
- `resource/productos/exceptions/FamiliaExceptions.ErrorResponse.timestamp`
- `security/JwtTokenProvider`
  - fecha de emision y expiracion del JWT
- `service/commons/ExportacionTerminadoService`
  - `exportedAt`
- `service/commons/ExportacionProveedorService`
  - `exportedAt`
- `service/commons/TRMService`
  - fecha del DTO de respuesta
- `resource/inventarios/IngresoTerminadosAlmacenResource`
  - nombre de archivo de plantilla con fecha

## 5. Schedulers

- `service/commons/notificaciones/PuntoReordenAlertScheduler`
  - ya operaba explicitamente con `America/Bogota`
- `service/contabilidad/DepreciacionScheduler`
  - ahora se agenda explicitamente con `zone = "America/Bogota"`

## 6. Configuracion global aplicada

- Zona por defecto de la JVM fijada al arrancar la aplicacion
- `Clock` y `ZoneId` centralizados en backend
- `AppTime` como utilidad comun para contextos no administrados por Spring
- `spring.jackson.time-zone=America/Bogota`
- `spring.jpa.properties.hibernate.jdbc.time_zone=America/Bogota`
- `spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'America/Bogota'`
- logging de arranque para:
  - zona por defecto de JVM
  - zona del `Clock`
  - timezone de la sesion PostgreSQL

## 7. Fuera de alcance de esta fase

- No se cambia el tipo de columnas de negocio existentes en PostgreSQL.
- No se corrigen automaticamente registros historicos ya guardados con desfase.
- Los historicos remotos ya afectados requieren una estrategia aparte de saneamiento de datos.
