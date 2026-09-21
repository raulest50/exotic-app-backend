# Consulta paginada de planes de control

Las rutas se exponen bajo ambos prefijos:

- `/api/produccion/controles-proceso`
- `/api/calidad/controles-calidad`

## Contrato de lectura

`GET /planes/resumen?search=...&estado=VIGENTE&page=0&size=10`

- `search`: opcional; coincidencia literal parcial en código o nombre, sin distinguir mayúsculas.
- `estado`: opcional; `BORRADOR`, `VIGENTE` o `RETIRADA`. Omitir para consultar todas.
- `page`: índice desde cero. `size`: de 1 a 50, predeterminado 10.
- Devuelve `content`, `number`, `size`, `totalElements` y `totalPages` usando el formato paginado existente.
- La unidad de paginación y de conteo es el **plan**, ordenado por código e ID.
- Cada plan incluye resúmenes de las versiones que coinciden con el filtro, ordenadas por número descendente. Incluyen fechas y cantidades de aplicaciones/mediciones, sin su contenido.
- `borrador`, `vigente` y `ultimaRetirada` contienen ID, número y estado, o `null`. Estas referencias consideran todas las versiones del plan, incluso las ocultas por el filtro.

`GET /planes/{planId}/versiones/{versionId}`

- Devuelve `{ plan, version }`: identidad y referencias del plan, más la configuración completa de la versión solicitada.
- `plan.versiones` contiene únicamente el resumen de esa versión; las referencias siguen siendo globales al plan.
- Valida que la versión pertenezca al plan y al ámbito solicitado.
- Ambas rutas requieren nivel 1 de la pestaña de planes correspondiente. Las operaciones de escritura conservan sus permisos existentes.

La consulta paginada usa proyecciones escalares y `EXISTS`, sin paginar una carga de colecciones. Después obtiene resúmenes y referencias únicamente para los IDs de la página. Los filtros y el límite de planes se ejecutan en la base de datos.

## Compatibilidad y despliegue

Se conservan `GET /planes`, `GET /planes/{planId}` y todos los contratos de guardar, publicar y retirar. No se requieren migraciones ni cambios de configuración.

Desplegar **primero el backend y después el frontend**. El frontend anterior funciona con el backend actualizado; el frontend nuevo requiere las dos rutas nuevas. Revisar también la suite contractual del repositorio `exotic-app-e2e` al validar el despliegue.

## Validación pendiente en staging

Las pruebas añadidas cubren coordinación de consultas, permisos, referencias ocultas y navegación. No sustituyen la ejecución de las consultas JPA contra PostgreSQL ni la prueba de navegador.

1. En ambos módulos, comprobar los cuatro estados y la búsqueda combinada; los totales deben contar planes, sin duplicados por sus versiones.
2. Con más de diez planes, navegar y cambiar tamaño entre 10, 20 y 50. Cambiar búsqueda o estado debe volver a la primera página.
3. Verificar en la red del navegador que navegar solo consulta resúmenes y que abrir detalle/editor consulta únicamente la versión seleccionada.
4. Con un vigente y un borrador del mismo plan, filtrar Vigentes: debe mostrarse el aviso del borrador y no debe ofrecer otra nueva versión. “Ver borrador” abre su detalle sin cambiar el filtro.
5. Comprobar detalles de versiones retiradas, exclusiones, valores numéricos/booleanos y catálogos históricos.
6. Publicar o retirar el último plan coincidente de la última página: debe conservar filtros y volver a una página válida. El reemplazo de vigente debe seguir retirando la anterior.
7. Validar niveles 1, 2 y 3; denegar las rutas nuevas a usuarios sin permiso. Revisar también la pertenencia de versión a plan y ámbito.
8. Confirmar que los diagramas siguen actualizando sus indicadores tras publicar o retirar.
