# MPS OF: primera etapa

Fecha: 1 de octubre de 2026. Implementación en código fuente; pendiente de
compilación y validación funcional en el PC de pruebas y staging.

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
