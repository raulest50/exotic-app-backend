# Exportacion e importacion total V2: PostgreSQL y POE

## Alcance

- La V1 conserva sus rutas y su formato `.dump`.
- La V2 exporta un ZIP con `manifest.json`, `database.dump` y `files/procesos-produccion/...`.
- Incluye todas las versiones referenciadas en `proceso_produccion_documento_version`, vigentes y retiradas.
- No incluye documentos de otros modulos. Tampoco elimina archivos ajenos o no referenciados al importar.
- Exportar esta disponible en produccion, staging y local con los permisos existentes de exportacion.
- Importar V1 y V2 requiere local o staging; el backend comprueba el ambiente al recibir y ejecutar el trabajo.
- No crea versiones documentales nuevas ni cambia IDs, claves de almacenamiento o referencias historicas.

## Rutas y frontend

- `POST /api/exportacion-datos/backup-total-v2/jobs`
- `GET /api/exportacion-datos/backup-total-v2/jobs/{jobId}`
- `GET /api/exportacion-datos/backup-total-v2/jobs/{jobId}/download`
- `DELETE /api/exportacion-datos/backup-total-v2/jobs/{jobId}`
- `POST /api/importacion-datos/backup-total-v2/jobs` (multipart `file`)
- `GET /api/importacion-datos/backup-total-v2/jobs/{jobId}`
- `DELETE /api/importacion-datos/backup-total-v2/jobs/{jobId}`

Se reutilizan los DTO, permisos y trabajos asincronos de V1. Las importaciones V1/V2 comparten
el bloqueo global y el mismo ejecutor, por lo que no se pueden restaurar simultaneamente.
El frontend agrega tarjetas independientes en Exportacion de datos y Cargas masivas.
La tarjeta de importacion V2 utiliza la misma restriccion por ambiente que V1.
Estas nuevas rutas deben incorporarse a la suite contractual de `exotic-app-e2e`; no se quitaron rutas V1.

## Consistencia y aplicacion

1. La exportacion lee POE e historial Flyway en una transaccion de solo lectura REPEATABLE READ.
   `pg_export_snapshot()` permite a `pg_dump --snapshot` observar exactamente la misma base.
2. Apache Commons Compress escribe el ZIP. Cada POE se compara contra su tamano y SHA-256 existentes
   mientras se copia. Si falta o no coincide, el trabajo falla y no entrega un respaldo incompleto.
3. La importacion valida todas las entradas antes de escribir archivos definitivos. Rechaza archivos
   faltantes, adicionales, duplicados, enlaces simbolicos, rutas no permitidas y contenido alterado.
4. V1/V2 inspeccionan el historial real del dump sin conectarlo al destino. Debe ser un prefijo completo
   de la linea Flyway del backend instalado, con los mismos scripts y checksums; se rechazan historiales
   ausentes, fallidos, futuros, divergentes o de otra linea (incluida la mezcla de legado y `schema-v1`).
   La version objetivo procede del artefacto/perfil activo y de su historial validado, sin pendientes.
   No se fija un numero de migracion en el importador ni se usa `baseline`, `repair` o `clean`.
   Se exige `public`; V2 conserva ademas la misma version mayor de PostgreSQL y compara manifiesto/dump.
5. `pg_restore` convierte el dump a un SQL temporal con salida acotada antes de modificar datos.
6. Antes de reemplazar datos o POE se cierra la admision de operaciones y se espera hasta 30 segundos
   a que finalicen las solicitudes, tareas programadas y workers de exportacion/consulta IA activos.
   Si no terminan, se cancela antes del reemplazo. Durante mantenimiento las operaciones reciben HTTP 503;
   las consultas autenticadas del resultado del job siguen disponibles sin consultar usuarios en la BD.
   Se comprueba que una clave de POE ya referenciada en destino no tenga otro SHA/tamano.
   Se copian archivos verificados mediante un archivo temporal en el directorio destino y un movimiento
   atomico. Los archivos identicos se omiten. Los reemplazados se copian temporalmente a
   `data/.backup-v2-recovery/{jobId}/`, junto con el inventario que permite identificar sus rutas.
7. `psql --single-transaction --set=ON_ERROR_STOP=1` ejecuta el vaciado del esquema, el SQL del dump
   y la comparacion del inventario/historial con las filas restauradas. Los tres pasos confirman juntos.
   Un error SQL revierte tambien el vaciado. V1 ahora utiliza el mismo principio: reset y restauracion
   mediante `psql` en una sola transaccion, sin un vaciado previo independiente.
8. Tras restaurar se contrasta de nuevo el historial, se ejecutan las migraciones pendientes del backend
   instalado (con su estrategia Flyway activa) y se validan Flyway y el esquema de las entidades Hibernate.
   Se invalidan las regiones de cache y se conserva el saneamiento de contrasenas no productivas.
   Solo entonces se libera mantenimiento y se publica `LISTO`. La migracion no forma una unica transaccion
   con la restauracion: un fallo posterior conserva el entorno bloqueado para recuperacion manual.

El recibo distingue `MIGRANDO`, `VERIFICANDO` y `SANEANDO`, ademas de los estados existentes.
Expone `sourceSchemaVersion`, `targetSchemaVersion`, `finalSchemaVersion`, `migrationsApplied`
y `requiresRecovery`. El frontend conserva el resultado hasta pulsar **Volver a iniciar sesion**;
una consulta de estado fallida no repite el POST ni se interpreta como una restauracion finalizada.
Una respuesta incierta al POST sin identificador exige revisar el destino antes de repetir la operacion.

Los archivos y PostgreSQL no comparten una transaccion. Copiar primero es seguro para las referencias
anteriores porque las claves documentales son inmutables y se rechazan conflictos de contenido.
Si la restauracion SQL falla, pueden quedar POE nuevos sin referencias o archivos previamente ausentes
ya recuperados. No se eliminan automaticamente. Las copias de los reemplazados se conservan en caso de
error y se eliminan al confirmar la restauracion. Una perdida de conexion al confirmar requiere verificar
el destino: un error de comunicacion no demuestra por si solo que PostgreSQL haya revertido.

## Requisitos y limites

- Apache Commons Compress 1.28.0 como dependencia directa; sin cambios de infraestructura.
  Se alinean Commons Lang 3.18.0 y Codec 1.19.0 con sus dependencias declaradas, porque el BOM
  de Spring Boot 3.2 seleccionaba versiones anteriores. Commons IO 2.20.0 llega transitivamente.
  Referencia: https://commons.apache.org/proper/commons-compress/dependencies.html
- Exportar necesita `pg_dump`; importar V1/V2 necesita `pg_restore` y `psql`. El cliente PostgreSQL del Dockerfile existente incluye
  estos ejecutables. En local se busca `psql` junto al `pg_dump` configurado.
- `pg_restore` y `psql` deben tener la misma version mayor que el servidor destino. Este control se
  realiza antes del reemplazo; no exige que un dump V1 antiguo tenga el esquema actual.
- ZIP/subtotal descomprimido: hasta 8 GiB; inventario: 16 MiB; POE individual: 32 MiB;
  hasta 100000 documentos; SQL generado: hasta 8 GiB, limitado ademas por el espacio libre.
  Los POE creados normalmente por la aplicacion tienen un limite menor de carga.
- El limite HTTP multipart del despliegue puede ser menor; debe verificarse en staging antes de
  transferir respaldos grandes. No se modifico esa configuracion en esta iteracion.
- Los trabajos y resultados siguen siendo temporales, con la caducidad existente de 15 minutos;
  no se implementa reanudacion automatica tras reiniciar el backend.
- Realizar la importacion cuando el destino no este en uso. El mantenimiento coordina un unico proceso
  backend por entorno, como en la topologia actual; no es un bloqueo distribuido para varias replicas
  ni controla clientes SQL externos. Los planificadores omiten ejecuciones durante ese periodo y retoman
  su cadencia posterior. Un respaldo sigue siendo codigo SQL privilegiado: usar solo fuentes confiables.
- La descarga del frontend conserva el mecanismo de blob existente; validar el uso de memoria antes
  de operar con paquetes de varios GiB.

## Recuperacion si la importacion falla

Antes del reemplazo, un fallo normal reabre el entorno sin modificar la base. Una vez intentado el SQL,
un fallo (incluida una confirmacion de resultado desconocido) devuelve `ERROR` con `requiresRecovery=true`.
Se conserva `data/.database-import-maintenance`, con identificador y fase, incluso tras reiniciar Spring.
No hay un boton que permita omitir este bloqueo ni recuperacion automatica de toda la base.

En local/staging, el operador debe detener exclusivamente el backend afectado, recuperar una copia
conocida o reconstruir la base ficticia, aplicar/validar las migraciones del mismo artefacto y comprobar
el saneamiento de credenciales. Con el backend aun detenido y el destino ya verificado, retirar el marcador
de ese entorno y arrancar de nuevo. Reiniciar sin recuperar o borrar el marcador por si solo no repara la BD.
Conservar el respaldo de entrada y la copia anterior fuera del area temporal del job para esta operacion.
La limpieza del recibo temporal no elimina el marcador de mantenimiento.

## Sincronizacion manual del modelo

No se modifica `ExoticApp.gaphor`. Si se incorpora este modulo al alcance modelado, representar
`BackupSchemaCompatibilityService` (Flyway/estrategia activa, herramientas PostgreSQL y Hibernate),
`DatabaseImportMaintenanceService` y `DatabaseImportMaintenanceFilter`. El coordinador V1/V2 depende
de compatibilidad y mantenimiento; el importador V2 depende tambien de compatibilidad. Seguridad,
planificacion y los workers de exportacion/consulta IA usan el mismo mantenimiento. El contrato del
frontend incorpora fases y metadatos del esquema. Esta sincronizacion queda pendiente del usuario.

## Validacion local y comprobaciones pendientes de despliegue

El 25 de noviembre de 2026 se ensayaron las restauraciones con PostgreSQL 18.6 y clientes 18.6
en un contenedor local desechable: respaldo V1 del mismo esquema y antiguo, V2 antiguo con POE,
rechazo por checksum y fallos de restauracion, migracion, validacion JPA y saneamiento. No se
restauro ninguna base de la VM. Flyway 9.22.3 conserva su aviso de soporte oficial hasta PostgreSQL 15;
el resultado de estos fixtures no sustituye la actualizacion de esa dependencia ni el ensayo del
artefacto completo en staging. Esa actualizacion de dependencias queda fuera de este cambio.
Resultado local: 61 casos unitarios y 8 de integracion correctos; build backend, lint y build Vite
correctos. La interfaz se comprobo con respuestas simuladas (exito, consulta fallida, recuperacion
y POST incierto), sin errores de consola. Estos resultados no acreditan un despliegue remoto.

Las pruebas usan fixtures ficticios y configuracion programatica, sin cargar propiedades privadas.
En el equipo preparado para desarrollo, ejecutar los gates habituales y las pruebas autorizadas:

```powershell
.\gradlew.bat test --tests '*BackupImportCompatibilityTest' --tests '*BackupV2ArchiveServiceTest' --tests '*BackupV2ImportServiceTest' --tests '*DatabaseImportMaintenanceTest' --tests '*AgentAssistantServiceTest' --tests '*BackupTotalImportPostgresIntegrationTest'
```

Verificar en staging:

1. Exportar/importar V1 del mismo esquema y de uno antiguo compatible: debe migrar, validar y conservar su formato.
2. Exportar V2 con POE vigentes/retirados y recuperar los documentos de los batch records.
3. Importar dos veces el mismo ZIP: mismos IDs/versiones y documentos, sin duplicados.
4. Un POE ausente o alterado en origen impide ofrecer una descarga V2 completa.
5. ZIP corrupto, entrada faltante, ruta ajena o hash distinto: ERROR antes de reemplazar la base.
6. Dump e inventario inconsistentes: ERROR y rollback de esquema/datos anteriores.
7. Historia futura, checksum divergente, linea diferente o version mayor PostgreSQL incompatible en V2:
   rechazo antes de copiar POE. Un historial antiguo compatible se migra hasta el objetivo activo.
8. Interrumpir una consulta de estado y usar Consultar estado sin crear una segunda importacion.
9. Mantener los archivos ajenos a POE y rechazar operaciones concurrentes V1/V2 de importacion.
10. Verificar el bloqueo de produccion mediante pruebas del guard; no ensayar una restauracion real en produccion.
11. Comprobar exportacion/carga de Excel y lectura de DOCX, que comparten Apache POI y Commons Compress.
12. Provocar en fixtures un fallo SQL, uno de migracion, uno de validacion JPA y uno de saneamiento:
    ninguno debe publicar `LISTO`; tras el intento de reemplazo deben conservar el bloqueo y pedir recuperacion.
13. Durante una importacion, comprobar 503 en operaciones, lectura del recibo, drenaje de workers y
    omision de tareas programadas; reiniciar con marcador no debe reabrir la aplicacion.
