# MPS OF: programacion, aprobacion y emision independientes

Actualizacion: 17 de noviembre de 2026. El flujo OF reproduce la dinamica de
programacion y aprobacion OP, con planes, permisos y ordenes independientes.
La base neutra y los demas semiterminados marcados para OF pueden fabricarse
para inventario sin un MPS OP asociado. Las cantidades del plan expresan
salida del producto; no se deduce la capacidad de una marmita de sus ingredientes.

## Retroactividad de dispensacion OP y OF (17 de noviembre de 2026)

La directiva `DISPENSACION_NO_BLOQUEA_INICIO_PRODUCCION` ya se copia al crear
OP y OF. Su edicion no reinterpreta ordenes emitidas. La accion explicita
de Supermaster ahora consulta y aplica retroactividad a ambos tipos mediante
los endpoints existentes `GET /api/super-master-directives/dispensacion-inicio/retroactividad-preview`
y `POST /api/super-master-directives/dispensacion-inicio/aplicar-retroactividad`.
Conserva la autorizacion de `master` y `super_master` y exige directiva activa.

Las reglas OP se conservan. Son candidatas OF las `PLANIFICADA` o `LIBERADA`
con politica `BLOQUEANTE`, sin fecha de inicio, con al menos una operacion,
todas en `COLA` o `ESPERA` sin fecha de completado y sin eventos de trabajo
operativo o paso previo por `EN_PROCESO`. Se seleccionan IDs ordenados y,
al aplicar, se bloquea y refresca cada OF y se revalida el mismo predicado.
Una OF que dejo de ser candidata se contabiliza como omitida. Repetir la
accion no vuelve a modificar una OF que ya es `NO_BLOQUEANTE`.

Solo cambian la politica y su fecha de aplicacion. `PENDIENTE` pasa a
`LIBERADA_SIN_DISPENSACION`; `PARCIAL` y `COMPLETA` se conservan. No se
alteran cantidades, movimientos, fechas de lanzamiento, estados de orden
u operaciones, precedencias, permisos ni despejes. Una OF planificada no
se libera anticipadamente. Se registra auditoria `SYSTEM/SISTEMA` con
origen igual a destino y usuario solicitante, y se pide actualizar la
proyeccion documental existente. El descubrimiento de backfill legado
ignora solo esa auditoria neutra; las inicializaciones con origen NULL,
las transiciones y los eventos humanos siguen siendo significativos.

`SuperMasterOpsService` coordina ambos servicios en una transaccion. El DTO
conserva `ordenesCandidatas`, `ordenesAplicadas`, `ordenesOmitidas` y
`ordenIdsAplicadas` exclusivamente para OP. Agrega los tres contadores
`ordenesFabricacionCandidatas/Aplicadas/Omitidas` y la lista Long
`ordenFabricacionIdsAplicadas`. No se mezclan identificadores OP y OF.
`ejecutable` contempla candidatas de cualquiera de los dos tipos. La
interfaz presenta los dos grupos y confirma su alcance antes de aplicar.
Los contratos E2E que describan estos payloads deben incorporar los campos
aditivos; no se han ejecutado sus suites.

### Diagnostico del lote BN01-0000001-26 en staging

Consulta de solo lectura del 17 de noviembre de 2026: OF-1 estaba
`LIBERADA`, sin fecha de inicio ni eventos operativos, con politica
`BLOQUEANTE` pero dispensacion `COMPLETA`; la directiva global figuraba
`false`. Este estado de materiales supera el control de dispensacion del
inicio OF incluso bajo politica bloqueante.

El area 13, FABRICACION 2, exige despeje y tiene linea exclusiva confirmada.
Las OP-893 y OP-894 figuraban en proceso en esa misma area. El frontend
prepara despeje antes de enviar el inicio; esa preparacion valida la
disponibilidad del area y rechaza otra operacion activa con HTTP 409. Es
un bloqueo concreto compatible con la captura, independiente de la
dispensacion. Los logs consultados no permitieron correlacionar el motivo
exacto de aquella solicitud. No se modificaron ordenes ni configuracion.

Se conserva el motivo de `ResponseStatusException` en la respuesta de
`DespejeLineaResource`; el panel muestra los textos `detail`, `mensaje`
o `message` cuando existen, para evitar mostrar solo "Conflict".

### Validacion y modelo

Se anadieron diez casos en `OrdenFabricacionRetroactividadTest` y se adapto
la construccion del servicio en `MpsFabricacionWorkflowIntegrationTest`.
No se ejecutaron pruebas ni suites por las instrucciones del repositorio.
El build local usa Java 21 y un init de Gradle temporal que excluye
`application*.properties` y dirige las salidas a una carpeta temporal.
No se copiaron configuraciones privadas ni se inicio la aplicacion backend.

Comandos y resultados de esta entrega:

- `gradlew.bat --init-script <temporal> build -x test testClasses --offline
  --no-daemon`: correcto con Java 21; no ejecuta pruebas.
- `bun run vite build`: correcto, con el aviso existente de chunks grandes.
- `bun run lint`: falla por el error previo `react-hooks/exhaustive-deps`
  de `DispensacionV2Step3SeleccionOrdenes.tsx:65` y supresiones sobrantes.
  No se modifico ese componente ni la linea base.
- `bunx eslint src/pages/SuperMasterDirectives/MasterDirectivesPage.tsx
  src/pages/AreaOperativaPanel/AreaOperativaPanel.tsx --config
  eslint.gate.config.js --max-warnings 0 --pass-on-unpruned-suppressions`:
  correcto; no silencia diagnosticos de esos archivos.
- `git diff --check`: correcto en ambos repositorios.
- No habia backend local escuchando en 8080 ni historia Ladle de estos
  componentes; no se comprobo el recorrido de guardado con sesion autenticada.

La seleccion se contrasto mediante SQL equivalente de solo lectura en
staging: OF-1 y OF-2 cumplen los criterios de candidatura. Quedan pendientes
la ejecucion real de JPQL, concurrencia, backfill y el recorrido autenticado
de preview/aplicacion. No se desplego ni se aplico retroactividad remota.

No hay cambio de esquema ni migraciones nuevas. No se encontro una vista
modelada de esta accion en el Gaphor guardado. Queda pendiente de
sincronizacion manual representar, cuando se incluya este alcance, la
coordinacion de SuperMasterOpsService con seguimiento OP y servicio OF,
los contadores separados y la actualizacion de politica sin avance operativo.
No se modifico `ExoticApp.gaphor`.

## Flujo vigente

- `BORRADOR`: guardar tandas y atender observaciones. Una tanda identifica un
  semiterminado, cantidad/unidad y fechas; puede repetirse el mismo producto.
- Observaciones propias de OF: `ABIERTA -> ATENDIDA -> CERRADA`, con revision,
  respuesta, responsables y fechas. Las pendientes impiden aprobar.
- `APROBADO`: autoriza el programa y conserva usuario/fecha de aprobacion.
- Emitir crea una OF independiente por tanda y deja el programa `CERRADO`.
  Este estado significa ordenes emitidas, no fabricacion completada.
- Guardar, aprobar o emitir no crea existencias. El ingreso se produce al
  reportar la cantidad realmente obtenida mediante la ejecucion OF existente.
- Crear una OP ya no genera OF automaticamente. Se conservan las referencias
  historicas y las OF manuales con su autorizacion especifica.

Cada OF del programa conserva una referencia unica a su tanda. Una peticion
de emision repetida devuelve las ordenes existentes. Las OF historicas o
manuales no se atribuyen a un MPS por coincidencia de producto o fechas.
Las actualizaciones llevan la `version` esperada; un conflicto devuelve 409.
`revisionNumero` conserva la revision del negocio y no sustituye `@Version`.

## Cambios posteriores y permisos

Se permiten adiciones, cambios y cancelaciones controlados despues de aprobar
o emitir, siguiendo la dinamica OP. Las tandas emitidas no se borran: se
cancelan logicamente cuando su OF no tiene ejecucion ni dispensaciones.
La cantidad y el producto de una OF emitida permanecen congelados; para
cambiarlos se cancela la tanda apta y se crea una sustituta. Las fechas y
observaciones se coordinan con la OF y sus operaciones pendientes.

`PROGRAMACION_MPS_OF` y `APROBACION_MPS_OF` son autorizaciones independientes
de OP. `CREAR_ORDEN_FABRICACION` conserva la ruta manual. El administrador
asigna los nuevos permisos; no se heredan aprobadores del MPS OP.

Las directivas `MPS_FABRICACION_DIAS_BLOQUEO_EDICION` (0 a 7, defecto 2) y
`MPS_FABRICACION_PERMITIR_AGREGAR_SEMITERMINADOS_APROBADO` (defecto false)
controlan la ventana de edicion del borrador y la incorporacion de semiterminados nuevos
a una semana aprobada. Las tandas existentes bloqueadas se conservan sin cambios;
no impiden editar otras fechas habilitadas. La segunda no prohibe por si sola mas tandas de un
producto ya programado. La directiva existente de Batch Record sigue
controlando la disponibilidad de creacion de OF; no se activa automaticamente.

## Esquema y compatibilidad

`V120__mps_of_independiente.sql` amplia cabecera y detalle, vincula OF a tanda
y crea `mps_fabricacion_observacion`. Las propuestas existentes reciben
BORRADOR/revision 1 sin inventar aprobaciones. No modifica V119 ni las
migraciones anteriores.

El perfil opcional `schema-v1` utiliza otra ubicacion. Su V001 consolidada
captura V118: antes de activar ese perfil con estos modelos deben incorporarse
y validarse los incrementales equivalentes a V119 y V120. No editar V001 para
absorberlos ni mezclar las dos ubicaciones de Flyway. La linea operativa
predeterminada sigue siendo `classpath:db/migration`.

## Alcance y verificacion

La asignacion de consumos de semiterminados a areas de Ruta Proceso Categoria
se desarrolla en la segunda entrega descrita a continuacion. El planificador
de capacidad de marmitas permanece fuera de ese alcance.
El modelo Gaphor requiere actualizacion manual: representar programacion,
observaciones, aprobacion y emision OP/OF como flujos paralelos y sus ordenes
correspondientes. El archivo Gaphor no se modifica con esta implementacion.

En esa actualizacion, incluir `MpsFabricacionObservacion`, su pertenencia a la
cabecera semanal y la relacion unica tanda-OF. Reflejar las responsabilidades
de los servicios de aprobacion, observaciones, emision y edicion, y de
`AprobacionMpsFabricacionTab` y `MpsFabricacionWeekView`. Retirar la dependencia
de creacion OP hacia el generador automatico OF eliminado. La sincronizacion
con el modelo sigue pendiente hasta revisar el diagrama actualizado.

### Segunda entrega: consumo de semiterminados por area

La ruta versionada de cada categoria asigna semiterminados fisicos a sus
nodos productivos. Cada semiterminado se incorpora en una sola etapa por
version, y Almacen General no recibe estas asignaciones. La ruta puede
contener la union de semiterminados de varios terminados de la categoria;
cada OP aplica solamente los que aparecen en sus propios requerimientos.
Las cantidades y los lotes no se configuran en la ruta. Modificar la
asignacion es un cambio funcional y crea version; mover el dibujo conserva
las asignaciones y solo actualiza su disposicion visual.

Al crear una OP se conservan los requerimientos, cantidades, unidades y
nodos/areas asignados en `OrdenProduccion.requerimientosMaterialesJson`.
Un semiterminado fisico requerido sin etapa asignada impide crear la OP con
un mensaje que identifica el producto pendiente de configurar. Las OP
historicas mantienen ese campo nulo y siguen su circuito previo; no se
recalculan sus entradas usando la receta o ruta vigente. La proyeccion
documental de una OP nueva utiliza su snapshot operativo.

El detalle operativo de la OP incorpora consulta de requerido, registrado y
pendiente, y captura de cantidades desde uno o varios lotes. El servidor
deriva OP, nodo y area a partir del seguimiento y comprueba la responsabilidad
del usuario. Los registros nuevos corresponden a etapas en ESPERA o
EN_PROCESO de una OP abierta. La politica de dispensacion congelada se
conserva: NO_BLOQUEANTE permite registrar despues de iniciar; BLOQUEANTE
comprueba los requisitos de la etapa correspondiente. Una OP sin insumos de
dispensacion general no espera una salida vacia de Almacen General.

Cada confirmacion registra una sola transaccion OD con movimientos
DISPENSACION en GENERAL asociados al area. `BatchRecordConsumo` proyecta esos
mismos movimientos como evidencia; no produce otra salida de inventario.
El dispensador general excluye esas entradas para OP nuevas y el backend
rechaza su envio por esa ruta. El cierre de la OP conserva su ingreso
positivo de terminado, sin volver a descontar el semiterminado.

El registro lleva una clave UUID y el hash de su contenido. Un reintento
autorizado de la misma solicitud devuelve la transaccion confirmada; la
misma clave con otro contenido devuelve conflicto. Un fallo de red conserva
la clave y la captura hasta recuperar el resultado. El stock se valida
dentro de la transaccion y bajo bloqueos de lotes en orden estable,
compartidos con las salidas OP/OF. Las demandas repetidas de un producto/lote
se suman antes de comprobar disponibilidad. Los lotes deben corresponder al
producto y ser aptos por fecha y disposicion de calidad; no se atribuye una
liberacion retrospectiva a los historicos SIN_CLASIFICAR.
Estos bloqueos coordinan los tres caminos dispensadores de esta entrega;
no se ha redisenado la concurrencia de los demas ajustes o traslados de
inventario preexistentes.

`V121__consumo_semiterminado_por_area.sql` incorpora la tabla de union
`ruta_proceso_node_semiterminado`, el snapshot nullable de OP y los campos
de idempotencia de `transaccion_almacen`. No crea entidades de negocio ni
atribuye consumos historicos. El perfil opcional schema-v1 tambien necesita
el incremento equivalente a V121, ademas de V119/V120, antes de utilizarse.

La sincronizacion manual de Gaphor debe reflejar la asignacion en
RutaProcesoCatDesigner, su pertenencia a la version de ruta y el registro
operativo mediante el nuevo panel y servicio, conservando un unico libro de
movimientos. No se modifican los diagramas desde esta implementacion.

### Verificacion local de la segunda entrega (6 de noviembre de 2026)

| Comprobacion | Resultado |
| --- | --- |
| Backend `gradlew.bat build -x test testClasses --offline`, Java 21 | Correcto en copia temporal sin los `application*.properties` privados. Compilaron todas las clases de prueba; ninguna suite ejecutada. |
| Frontend `bun run vite build` | Correcto; aviso de tamano de chunks. |
| ESLint de los diez archivos funcionales frontend afectados | Correcto, sin nuevas supresiones. |
| Frontend lint y diagnostico TypeScript globales | Conservan errores/advertencias heredados, sin diagnosticos nuevos en los archivos funcionales de esta entrega. |
| E2E `bun run typecheck` | Correcto; contrato de consumo ampliado, sin ejecutar la suite. |
| Revision estatica de acceso | Sin hallazgos bloqueantes en responsable, pertenencia OP/seguimiento, reintentos y exclusion del dispensador general. |

Se prepararon `ConsumoSemiterminadoAreaServiceTest` e
`ConsumoSemiterminadoAreaIntegrationTest`, y se ampliaron las pruebas de
snapshot y versionado/layout. Cubren permisos, estados, cantidades,
reintentos, descuento unico, saldo compartido y persistencia. El fixture de
integracion usa exclusivamente PostgreSQL efimero con Flyway y Hibernate
en validacion. La compilacion y la revision estatica no acreditan esos
comportamientos en ejecucion.

Se actualizo el constructor de
`SeguimientoOrdenAreaServiceTableroFabricacionTest` para sus dependencias
vigentes, resolviendo el impedimento de compilacion consignado en la
verificacion historica de la primera entrega.

No se ejecutaron suites ni migraciones, ni se modificaron bases remotas.
La validacion autenticada requiere un entorno local o staging preparado:
configurar las asignaciones de la ruta, emitir una OP nueva y comparar su
snapshot con una OP historica; registrar parciales desde varios lotes;
repetir el mismo UUID tras una respuesta ambigua y comprobar un solo
descuento; contrastar BLOQUEANTE y NO_BLOQUEANTE; y ejecutar la prueba de
dos OP que compiten por el saldo de un lote. Aplicar V121 mediante el
procedimiento habitual de Flyway antes de usar el nuevo codigo.

### Verificacion local de la primera entrega MPS OF (6 de noviembre de 2026)

| Comprobacion | Resultado |
| --- | --- |
| Backend `gradlew.bat build -x test`, Java 21 | Correcto en copia temporal de las fuentes, excluyendo los `application*.properties` privados. |
| Backend `testClasses`, seleccion de esta implementacion mediante init script temporal | Correcto: cinco pruebas nuevas y cuatro existentes modificadas compiladas; ninguna ejecutada. |
| Backend `testClasses`, conjunto completo | Bloqueado por un constructor desactualizado en `SeguimientoOrdenAreaServiceTableroFabricacionTest`, por la dependencia de despeje de linea preexistente. No se modifico ese test. |
| Frontend `bun run vite build` | Correcto; aviso de tamano de chunks. |
| Frontend `bun run lint` global | No pasa: advertencia heredada de dependencias de `useMemo` en `AsistenteDispensacion/DispensacionV2Step3SeleccionOrdenes.tsx` y supresiones obsoletas ajenas. |
| ESLint en los diez archivos frontend de esta implementacion | Correcto, sin nuevas supresiones. Se permitieron las entradas obsoletas ajenas sin modificarlas. |
| E2E `bun run typecheck` | Correcto. Contrato MPS OF actualizado; suite sin ejecutar. |
| Diagnostico global TypeScript frontend | Mantiene errores heredados; sin diagnosticos en los archivos de esta implementacion. |

Las cinco pruebas nuevas incluyen observaciones, aprobacion, emision, edicion y
un fixture PostgreSQL/Flyway/Hibernate de integracion con rollback, idempotencia,
restricciones y bloqueo de cabecera. Su compilacion no acredita su ejecucion.
No se ejecutaron suites ni migraciones, ni se accedio a bases remotas. La prueba
manual autenticada del flujo queda pendiente por ausencia de backend, datos y
credenciales locales verificados. Para habilitarlo en un entorno se necesita
aplicar las migraciones pendientes por el procedimiento habitual de Flyway y
asignar los permisos OF a los usuarios correspondientes.

## Antecedente: alcance del 1 de octubre de 2026

Las secciones siguientes conservan el alcance y las verificaciones pendientes
de la primera etapa. Las restricciones de esa etapa relativas a aprobacion,
emision y generacion OP -> OF quedan sustituidas por el flujo anterior.

## Alcance

- La programación semanal existente se presenta como **MPS OP** y su revisión
  como **Aprobación MPS OP**. Conserva sus claves de permisos y API.
- **MPS OF** consulta las OF existentes y permite guardar propuestas semanales
  independientes. Las propuestas no emiten OF, lotes, movimientos ni stock.
- La generación automática actual OP → OF y su cancelación se conservan.
  El desacople, el cálculo de necesidades, la creación automática de propuestas
  y la emisión desde MPS OF corresponden a otra etapa.

## Modelo y contrato

Migración aditiva `V112__add_mps_fabricacion_and_area_visibility.sql`:

- `MpsFabricacionSemanal`: una cabecera por lunes ISO, versión optimista y
  auditoría del último guardado. Consultar una semana vacía no la crea.
- `MpsFabricacionDetalle`: semiterminado marcado para OF, cantidad decimal
  positiva (14 enteros / 4 decimales), unidad copiada del producto, inicio,
  final, observaciones y posición. Inicio dentro de lunes–domingo; final
  puede cruzar a otra semana. Un cambio de unidad requiere reemplazar la línea.
  La consulta de borrabilidad informa si el producto está referenciado por
  propuestas; hay que retirarlas antes de eliminarlo, también en borrado forzado.
- Guardado completo de las propuestas de la semana mediante PUT con la versión
  leída (null únicamente para semana nueva). Las líneas omitidas se retiran.
  Los IDs deben pertenecer al mismo programa. Conflictos devuelven 409.
  El frontend conserva la captura y pide recargar antes de volver a guardar.
- Las OF se agrupan por `fechaLanzamiento`, o por `fechaCreacion` cuando falta
  la primera, con una etiqueta visible. Intervalo [lunes, lunes siguiente),
  orden estable y paginación en servidor. Incluye estados históricos/cancelados.
  No se suman OF emitidas y propuestas como si fueran un mismo suministro.

| API | Uso |
| --- | --- |
| GET `/api/produccion/mps-of?weekStartDate=AAAA-MM-DD` | Propuestas de la semana |
| PUT `/api/produccion/mps-of/{weekStartDate}` | Guardar propuestas y revisión |
| GET `/api/produccion/mps-of/ordenes?weekStartDate=AAAA-MM-DD&page=0&size=20` | OF emitidas de la semana |
| GET `/api/produccion/mps-of/ordenes/{id}` | Detalle de OF de solo lectura |

Permiso existente `PRODUCCION / CREAR_ORDEN_FABRICACION`: nivel 1 consulta,
nivel 2 edición. No hereda `MAIN`. Se conserva el acceso de master/super_master.

## Consulta por área

### Ejecucion OP/OF por area (13 de noviembre de 2026)

Gestion de Areas incorpora `alcanceEjecucion`: `SOLO_OP`, `SOLO_OF` o
`AMBOS`. Es independiente de `visibilidadMps` y `alcanceMps`; configurar
que un area ejecute OF no cambia por si solo los MPS que consulta.
El valor inicial es `AMBOS`; Almacen General (-1) conserva ese valor.
Clientes anteriores que omitan el campo al actualizar conservan lo configurado.

Al emitir una nueva OP, cada seguimiento congela `aplicaEjecucionOp`.
Las etapas de areas `SOLO_OF` permanecen en la ruta como antecedente, pero
no aparecen en el tablero, carga o analitica operativa de OP. Cuando todas
sus predecesoras se resuelven, pasan automaticamente a `OMITIDO` con evento
de sistema y habilitan los siguientes pasos; una union sigue esperando las
demas ramas. No requieren POE, controles de etapa, consumos ni despejes y
aportan cero minutos a la estimacion. No se simulan cantidades ni firmas.

La etapa final de una OP debe ejecutar OP. Si un consumo fisico de
semiterminado esta asignado a un area `SOLO_OF`, la emision se rechaza con
una indicacion para reasignarlo a la etapa OP consumidora; no se descarta
el material. Las nuevas OF rechazan operaciones en areas `SOLO_OP`.

Una edicion posterior del area no reinterpreta ordenes emitidas. Los
seguimientos historicos con snapshot NULL mantienen su ejecucion anterior;
las OF ya inicializadas y el backfill legado conservan sus operaciones.
Revertir administrativamente un predecesor puede devolver a `COLA` sus
omisiones automaticas y sucesores habilitados, conservando el enlace de
auditoria; sigue prohibido si un sucesor tiene trabajo operativo registrado.

Migraciones aditivas equivalentes:
`db/migration/V125__alcance_ejecucion_areas.sql` y
`db/migration-v1/V004__alcance_ejecucion_areas.sql`. No actualizan los
seguimientos historicos ni modifican migraciones aplicadas. La linea
consolidada `schema-v1` tiene una limitacion previa respecto de campos de
despeje; no se declara validado su arranque con este cambio.

Se agregaron cinco casos en `SeguimientoOrdenAreaServiceTableroFabricacionTest`:
cadena de omisiones, union pendiente, snapshot historico NULL, cancelacion
y reversion administrativa. Su ejecucion y la validacion real de Flyway,
JPQL y guardado/tablero con PostgreSQL quedan pendientes; no se autorizo
ejecutar suites. El reporte de cantidades por etapas corresponde a otra entrega.

Verificacion local de esta entrega:

- Backend: `gradlew.bat --init-script <temporal> build -x test testClasses
  --offline --no-daemon`, con Java 21: correcto. El init temporal dirige las
  salidas a una carpeta temporal y excluye `application*.properties` para no
  copiar configuracion privada. Compila los tests, no los ejecuta.
- Frontend: `bun run vite build`: correcto, con aviso de tamano de chunks.
- `bun run lint`: falla por `react-hooks/exhaustive-deps` en
  `DispensacionV2Step3SeleccionOrdenes.tsx:65`, archivo sin cambios en esta
  entrega, y supresiones sobrantes. No se altero esa linea base.
- ESLint sobre los siete archivos frontend de esta entrega, con la misma
  configuracion gate, `--max-warnings 0 --pass-on-unpruned-suppressions`:
  correcto. La opcion final solo permite entradas de la linea base ajenas
  a los archivos seleccionados; no silencia diagnosticos de estos archivos.
- `git diff --check`: correcto en ambos repositorios.
- `bun run dev --host 127.0.0.1` y navegador local: login visible y sin errores
  de consola observados. Sin backend local escuchando en 8080 ni sesion
  autenticada, no se verifico guardar configuracion ni ejecutar OP/OF.
  El servidor y la pestana temporales quedaron cerrados.

Pendiente de sincronizacion manual con Gaphor: representar, cuando se modele
este alcance, la separacion entre ejecucion y consulta MPS, el snapshot de
seguimiento OP y la omision por sistema que conserva las dependencias.
No se modifico `ExoticApp.gaphor`.

### Consulta de planificacion

Gestión de Áreas permite combinar:

| Campo | Valores | Predeterminado |
| --- | --- | --- |
| `visibilidadMps` | SOLO_OP, SOLO_OF, AMBOS | SOLO_OP |
| `alcanceMps` | TODOS, SOLO_RUTA | TODOS |

Las áreas existentes reciben los valores predeterminados. Una actualización
legacy que omita estos campos conserva lo configurado. La edición de estos
campos exige el permiso correspondiente de Gestión de Áreas.

El panel refresca `/api/auth/me` al abrirse y al pulsar Actualizar. Las tareas
del tablero no dependen de esta configuración. Los MPS del panel son de solo
lectura, utilizando los GET equivalentes bajo `/api/area-operativa-panel/mps-of`.
El backend relee la configuración en cada consulta y rechaza MPS no habilitados.

Con SOLO_RUTA:

- OP emitidas: ruta congelada de la OP; si no existe, sus seguimientos históricos.
- Lotes MPS OP sin emitir: ruta vigente de su categoría.
- OF emitidas: operaciones congeladas de la OF.
- Propuestas OF: última versión de manufactura del semiterminado. Sin ruta
  interpretable no aparecen en esta vista; siguen visibles con alcance TODOS.
- Los filtros se aplican también a detalles, lotes y totales. El filtro de OF
  precede a la paginación. No se filtra por categorías habilitadas del área.

## Validación pendiente

Se añadieron pruebas unitarias `MpsFabricacionServiceTest`,
`AreaMpsConsultaServiceTest` y `MpsFabricacionResourceTest`, y un contrato de
navegador en `exotic-app-e2e/contracts/mps-fabricacion.contract.spec.ts`.
`SemiTerMpsFabricacionTest` cubre la protección del producto referenciado.
No se han ejecutado en este equipo. Las pruebas con mocks no demuestran el
comportamiento real de Flyway, JPQL ni de la concurrencia en PostgreSQL.

En el PC autorizado: compilar backend, ejecutar las tres clases de prueba,
lint/build de frontend, typecheck E2E y suite contractual MPS. No usar hosts
de producción. Tras desplegar **ambos repositorios en staging**, verificar:

1. Flyway V112 y validación de Hibernate sin modificar configuración.
2. MPS OP conserva aprobación, emisión de OP y generación actual de OF.
3. Una semana OF vacía no crea datos al abrir; guardar/recargar conserva
   cantidades, fechas y observaciones. Quitar propuesta no modifica una OF.
4. Dos sesiones guardan la misma revisión: una recibe 409. Repetir desde una
   semana nueva para comprobar la unicidad sin sobrescrituras.
5. Semanas que cruzan año, domingo incluido y lunes siguiente excluido;
   OF sin fecha de lanzamiento, paginación y detalle.
6. Las seis combinaciones por área; en SOLO_RUTA comprobar rutas históricas
   distintas para un mismo producto, totales y acceso directo a un detalle ajeno.
7. Cambiar la configuración mientras el responsable tiene abierta su sesión;
   Actualizar recoge el cambio y el tablero mantiene sus tareas.
8. Usuario nivel 1 no guarda; nivel 2 sí; responsable de área no edita;
   usuario sin permiso no accede invocando directamente la API.

No se realizó acceso a servicios remotos ni cambios de infraestructura.
