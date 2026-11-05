# Consolidacion de Flyway: linea V1

La linea consolidada permite crear una base nueva con
`src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql`.
Se selecciona mediante el perfil explicito `schema-v1`; no cambia la base de datos
de Render ni su historial.

## Seleccion de linea y entorno

Sin `schema-v1`, `application.yml` conserva la configuracion historica y la
ubicacion `classpath:db/migration`, con sus 116 migraciones versionadas intactas.
Con `schema-v1`, Flyway utiliza exclusivamente `classpath:db/migration-v1`.
No combinar ambas ubicaciones: sus versiones pertenecen a lineas distintas.
Ambas carpetas pueden estar en el mismo artefacto porque la seleccion se hace
mediante `spring.flyway.locations`.

Activar `schema-v1` junto al perfil del entorno; por ejemplo,
`SPRING_PROFILES_ACTIVE=staging,schema-v1` y
`APP_RUNTIME_ENVIRONMENT=staging` para el destino de pruebas. No activarlo en
Render ni en una base con historial legado. El perfil selecciona la linea de
migraciones, no la conexion. Seleccionar por separado la configuracion privada del entorno previsto
(`production`, `staging` o una base local aislada), incluidas `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USER` y `DB_PASSWORD`. Comprobar el destino antes del primer arranque
y no establecer overrides que mezclen las dos lineas.

El perfil mantiene Flyway habilitado y Hibernate en `validate`. Desactiva el
baseline automatico, fija la version de baseline en 1 y mantiene `clean`
deshabilitado. `FlywayConsolidatedConfig` elimina los patrones que ignoran
migraciones durante la validacion, incluido `*:future`. Esto se configura por
codigo porque Spring Boot 3.2.5 no transmite a Flyway una lista YAML vacia.
La linea historica conserva su comportamiento anterior.

La estrategia exclusiva del perfil consulta el historial antes de migrar: si
existen registros, exige la raiz SQL V1 consolidada y rechaza cualquier marcador
`BASELINE` heredado. Esto tambien impide aceptar un historial que solo contenga
el baseline antiguo en version 2. La validacion posterior comprueba los checksums;
las futuras V2 y siguientes de esta linea no quedan prohibidas.

## Procedencia y contenido

El esquema capturado procede de un contenedor Docker **local** con PostgreSQL
18.1 e historial hasta V118. Esta procedencia no demuestra que coincida con
Render. Antes de usar la V1 para un entorno real, contrastar el esquema y el
historial de su origen con la captura local. El contraste con Render permanece
pendiente.

El manifiesto `docs/flyway/consolidacion-v1.manifest.json` registra la procedencia,
las huellas de los archivos, la extraccion DDL normalizada, las exclusiones y las
validaciones realizadas o pendientes. Incluye tambien la huella del bootstrap
tecnico. Conservar esa evidencia junto con la linea historica. El checksum de
Flyway y una huella SHA-256 son comprobaciones distintas.

La V1 contiene unicamente la estructura fiel del esquema capturado, sin semillas
ni transformaciones historicas de datos. Incluye los objetos persistentes que no
describe JPA, como funciones, triggers, indices parciales, restricciones y
secuencias. Los usuarios, contactos, productos, configuraciones particulares de
la empresa, saldos, ordenes y expedientes pertenecen a la restauracion de datos y
no deben incorporarse al SQL consolidado.

El bootstrap tecnico para una instalacion sin datos es explicito y esta en
`src/main/resources/db/bootstrap-v1/instalacion_nueva.sql`, fuera de las ubicaciones
de Flyway. No agregar esa carpeta a `spring.flyway.locations` ni convertir el
bootstrap automaticamente en una V2. No incorpora datos particulares de la
empresa.

## Primer uso y restauracion de datos

Las dos rutas parten de una base vacia y ejecutan la nueva V1 antes de arrancar
la aplicacion:

1. **Instalacion sin datos anteriores:** Flyway `migrate` -> bootstrap tecnico
   `src/main/resources/db/bootstrap-v1/instalacion_nueva.sql` -> arranque.
2. **Restauracion de datos anteriores:** Flyway `migrate` -> restauracion
   `data-only`, excluyendo el historial nuevo de Flyway -> arranque. No ejecutar
   el bootstrap antes de restaurar; comprobar al finalizar la restauracion que
   existen las semillas tecnicas necesarias antes de arrancar.

La ejecucion previa de `migrate` requiere un proceso dedicado de Flyway antes
de iniciar los inicializadores de Spring. El perfil `schema-v1` selecciona y
protege la linea; no es un ejecutable separado ni agrega el plugin Gradle de
Flyway. No asumir que existe una tarea `gradlew flywayMigrate`. La integracion
operativa de ese paso y la restauracion se preparan antes del despliegue.
Para el bootstrap, con la conexion privada del destino ya configurada por el
operador, el comando SQL es:

```bash
psql -v ON_ERROR_STOP=1 --single-transaction -f src/main/resources/db/bootstrap-v1/instalacion_nueva.sql
```

Los procedimientos de recuperacion de arquitectura que conservan el historial
completo corresponden a la linea historica. No mezclarlos con la ruta nueva
sin revisar el plan de restauracion y los artefactos del entorno.

Flyway permanece habilitado durante el arranque; entonces vuelve a validar su
historial y solo ejecuta migraciones pendientes de la linea seleccionada.
No aplicar esta linea sobre una base que conserve el historial legado, no borrar
`flyway_schema_history` para forzarla y no ejecutar `baseline`, `repair` o `clean`
como parte de este procedimiento. Un baseline solo registra una version; no crea
el esquema ni verifica su equivalencia con la V1.

Si se conservan datos de una base anterior, su restauracion es una operacion
separada y requiere un plan revisado. Una restauracion `data-only` debe excluir
`flyway_schema_history`: no sobrescribir ni mezclar el historial generado por la
nueva V1. No restaurar encima el DDL antiguo. Revisar las semillas restauradas,
las referencias por ID y los estados de secuencias; no omitir errores de claves
duplicadas ni sustituir filas sin evaluar su significado. La comprobacion final
de semillas no autoriza ejecutar el bootstrap completo sobre datos restaurados.
Los datos restaurados ya contienen los efectos de las migraciones historicas y
no deben someterse nuevamente a sus correcciones.

Antes del cambio de entorno, comprobar en una base aislada: ejecucion de V1 sobre
vacio, validacion Flyway y JPA, segunda ejecucion sin nuevas migraciones,
equivalencia de objetos con la captura y restauracion de los datos previstos.
Registrar los resultados en el manifiesto y mantener disponibles la linea y el
respaldo anteriores hasta verificar la recuperacion. Crear esta configuracion no
equivale a autorizar un despliegue ni una operacion sobre Render o la VM.

## Verificacion realizada el 4 de noviembre de 2026

- `gradlew.bat build -x test --console=plain --no-daemon`: correcto con Java
  21.0.9 y Gradle 8.7. Se compilo una copia temporal de los archivos versionados
  actuales mas los de consolidacion, excluyendo `application*.properties`.
  La suite de pruebas de la aplicacion no se ejecuto.
- Captura mediante `docker exec ... pg_dump --schema-only --no-owner
  --no-privileges --exclude-table=public.flyway_schema_history`; consultas de
  inventario e historial mediante `psql` en transacciones de solo lectura.
  Coinciden los checksums de los 114 SQL aplicados con sus archivos actuales;
  los 116 archivos historicos se conservan sin cambios.
- Un verificador Java temporal uso la configuracion automatica de Spring Boot
  3.2.5 y los beans reales de `schema-v1`, con Flyway 9.22.3, contra PostgreSQL
  18.6 desechable: V001 aplicada, `validate` correcto y segunda migracion sin
  cambios. Tambien rechazo historiales legados con baseline 2 y con V118.
- Hibernate 6.4.4 valido las 136 entidades con `ddl-auto=validate`, sin iniciar
  el servidor de la aplicacion ni sus inicializadores.
- Bootstrap: 17 filas tecnicas, repeticion sin alterar datos, rechazo ante
  datos de negocio, ausencia de historial o marcador BASELINE heredado.
- Se restauro tambien el DDL original en otra base desechable con PostgreSQL
  18.6 y se compararon sus exportaciones normalizadas: coinciden. La comparacion
  directa entre los textos emitidos por 18.1 y 18.6 presenta diferencias de
  representacion de casts; no se confundieron con diferencias de estructura.

Los recursos temporales de base de datos se retiran al finalizar. No se ha
restaurado contenido empresarial, documentos ni estados de secuencias, ni se
han validado flujos de negocio completos o permisos del futuro rol operativo.
El bootstrap no incorpora documentos ni grandes objetos PostgreSQL.

**Compatibilidad pendiente antes de produccion:** Flyway 9.22.3 emitio una
advertencia porque PostgreSQL 18.6 supera su version maxima declarada como
probada, PostgreSQL 15. El resultado positivo de estas comprobaciones no
establece soporte oficial. Revisar la actualizacion de Flyway y sus dependencias
antes del corte productivo; esta entrega no cambia esas versiones. La referencia
del motor actual esta en el
[codigo oficial de PostgreSQLDatabase para Flyway 9.22.3](https://github.com/flyway/flyway/blob/flyway-9.22.3/flyway-core/src/main/java/org/flywaydb/core/internal/database/postgresql/PostgreSQLDatabase.java).

La topologia de despliegue modelada en Gaphor no cambia. No se encontraron
decisiones modeladas sobre las ubicaciones concretas de Flyway o estos archivos
de bootstrap; el modelo no fue editado.

## Referencias de implementacion

- [Configuracion automatica de Flyway en Spring Boot 3.2.5](https://github.com/spring-projects/spring-boot/blob/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/flyway/FlywayAutoConfiguration.java#L307): omite la lista de patrones si esta vacia.
- [Validacion de migraciones en Flyway 9.22.3](https://github.com/flyway/flyway/blob/flyway-9.22.3/flyway-core/src/main/java/org/flywaydb/core/internal/info/MigrationInfoImpl.java): compara las migraciones disponibles con las aplicadas y considera los patrones de exclusion.
- [Comando baseline en Flyway 9.22.3](https://github.com/flyway/flyway/blob/flyway-9.22.3/flyway-core/src/main/java/org/flywaydb/core/internal/command/DbBaseline.java): registra el punto de partida en el historial.
