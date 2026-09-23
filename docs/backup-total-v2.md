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
4. Exige el esquema `public`, la misma version mayor de PostgreSQL y el mismo conjunto de migraciones
   Flyway aplicadas (version/checksum) entre origen y destino. No modifica ni repara el historial Flyway.
5. `pg_restore` convierte el dump a un SQL temporal con salida acotada antes de modificar datos.
6. Se comprueba que una clave de POE ya referenciada en destino no tenga otro SHA/tamano.
   Se copian archivos verificados mediante un archivo temporal en el directorio destino y un movimiento
   atomico. Los archivos identicos se omiten. Los reemplazados se copian temporalmente a
   `data/.backup-v2-recovery/{jobId}/`, junto con el inventario que permite identificar sus rutas.
7. `psql --single-transaction --set=ON_ERROR_STOP=1` ejecuta el vaciado del esquema, el SQL del dump
   y la comparacion del inventario/historial con las filas restauradas. Los tres pasos confirman juntos.
   Un error SQL revierte tambien el vaciado. La V1 conserva su procedimiento de restauracion anterior.
8. Se conserva el saneamiento de contrasenas no productivas que ya ejecutaba V1. Un fallo en ese paso
   informa ERROR y no presenta la operacion como completada, aunque la restauracion ya haya confirmado.

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
- V2 necesita `pg_dump`, `pg_restore` y `psql`. El cliente PostgreSQL del Dockerfile existente incluye
  estos ejecutables. En local se busca `psql` junto al `pg_dump` configurado.
- ZIP/subtotal descomprimido: hasta 8 GiB; inventario: 16 MiB; POE individual: 32 MiB;
  hasta 100000 documentos; SQL generado: hasta 8 GiB, limitado ademas por el espacio libre.
  Los POE creados normalmente por la aplicacion tienen un limite menor de carga.
- El limite HTTP multipart del despliegue puede ser menor; debe verificarse en staging antes de
  transferir respaldos grandes. No se modifico esa configuracion en esta iteracion.
- Los trabajos y resultados siguen siendo temporales, con la caducidad existente de 15 minutos;
  no se implementa reanudacion automatica tras reiniciar el backend.
- Realizar la importacion cuando el destino no este en uso. No se incorporo un modo de mantenimiento
  global ni se modificaron los planificadores de negocio. La restauracion adquiere los bloqueos de
  PostgreSQL y puede competir con operaciones activas; un fallo no debe confundirse con exito.
- La descarga del frontend conserva el mecanismo de blob existente; validar el uso de memoria antes
  de operar con paquetes de varios GiB.

## Validacion pendiente en el entorno de pruebas

No se ejecutan builds, servidores ni pruebas automaticamente en el equipo de edicion.
En el equipo preparado para desarrollo, ejecutar los gates habituales y, cuando se autorice:

```powershell
.\gradlew.bat test --tests '*BackupV2ArchiveServiceTest' --tests '*BackupV2ImportServiceTest'
```

Verificar en staging:

1. Exportar/importar V1 y comprobar que mantiene su comportamiento y formato.
2. Exportar V2 con POE vigentes/retirados y recuperar los documentos de los batch records.
3. Importar dos veces el mismo ZIP: mismos IDs/versiones y documentos, sin duplicados.
4. Un POE ausente o alterado en origen impide ofrecer una descarga V2 completa.
5. ZIP corrupto, entrada faltante, ruta ajena o hash distinto: ERROR antes de reemplazar la base.
6. Dump e inventario inconsistentes: ERROR y rollback de esquema/datos anteriores.
7. Migraciones o version mayor de PostgreSQL diferentes: rechazo antes de copiar POE.
8. Interrumpir una consulta de estado y usar Consultar estado sin crear una segunda importacion.
9. Mantener los archivos ajenos a POE y rechazar operaciones concurrentes V1/V2 de importacion.
10. Verificar el bloqueo de produccion mediante pruebas del guard; no ensayar una restauracion real en produccion.
11. Comprobar exportacion/carga de Excel y lectura de DOCX, que comparten Apache POI y Commons Compress.
