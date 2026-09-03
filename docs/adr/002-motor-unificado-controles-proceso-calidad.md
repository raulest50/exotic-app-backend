# ADR 002: Motor unificado de controles de Proceso y Calidad

## Estado

✅ **Aceptado**

**Fecha**: 2026-09-02

**Autor(es)**: Equipo de Desarrollo Exotic App

---

## Contexto

El módulo histórico de Calidad modelaba plantillas y mediciones por área, pero se
utilizaba principalmente para controlar pesos de producto terminado. Esa ubicación
funcional confundía dos responsabilidades distintas:

- el control de variables para conducir o ajustar la fabricación;
- el ensayo de aceptación o liberación gobernado por Calidad.

La magnitud observada no resuelve esa distinción. Peso, pH o viscosidad pueden ser
controles de proceso o ensayos de Calidad según el momento, el punto de aplicación,
el efecto sobre el flujo y el responsable funcional.

## Decisión

Se adopta un único modelo de planes versionados, aplicabilidad, características,
requisitos congelados, ejecuciones y desviaciones. El modelo se expone mediante dos
fachadas funcionales:

- `PROCESO`, administrada y ejecutada desde Dirección Técnica y de Planta;
- `CALIDAD`, administrada y ejecutada desde Calidad.

El ámbito y los responsables se derivan de la fachada autenticada. No forman parte
de los datos confiables enviados por el cliente. Una ejecución referencia un
`ControlRequerido`; el servidor obtiene de esa ocurrencia el lote, la etapa, la
versión y el resto del contexto congelado.

Las reglas de una versión se evalúan con OR entre reglas y AND entre sus campos.
Todos los planes vigentes coincidentes se aplican. Una coincidencia repetida del
mismo plan en el mismo punto produce una sola ocurrencia.

Las versiones publicadas y las ejecuciones son inmutables. Una repetición agrega una
nueva ejecución y conserva la no conformidad anterior. Toda no conformidad abre una
desviación y una aceptación excepcional no reescribe el resultado original.

El expediente Batch Record materializa los requisitos aplicables y conserva ciclos
independientes de envío, devolución, reenvío y decisión. Las mediciones siguen
estando disponibles de manera independiente cuando la directiva del expediente está
desactivada; en ese caso no bloquean transiciones que no existen.

## Consecuencias

### Positivas

- El modelo representa el propósito y la responsabilidad del control sin inferirlos
  de la magnitud.
- Producción y Calidad comparten reglas de cálculo y trazabilidad sin compartir
  permisos de escritura.
- Los lotes activos conservan el plan y la versión con los que fueron creados.
- Las devoluciones y reaperturas no sobrescriben evidencia ni ciclos anteriores.
- El histórico legado puede migrarse de manera aditiva como `PROCESO`.

### Costos y límites

- La autorización debe verificarse nuevamente en cada fachada, aunque el servicio de
  dominio sea común.
- La migración mantiene temporalmente tablas y columnas `calidad_*` para no romper
  consumidores existentes.
- Durante esa convivencia, las mutaciones históricas operan mediante un adaptador
  transaccional: una versión enlazada no puede modificarse desde la fachada nueva,
  y una familia no puede publicar su primera versión nativa hasta retirar la
  vigente legada. El modo `RETIRED` se habilita únicamente después de conciliar y
  migrar los consumidores.
- Una ejecución sobre una versión migrada se refleja en ambos modelos. Si un valor
  decimal no puede representarse reversiblemente en el `Double` histórico, la
  operación se rechaza; el sistema nunca redondea evidencia de forma silenciosa.
- Esta entrega no incorpora funciones propias de un LIMS, como instrumentos,
  calibraciones, reactivos, estabilidad o integración automática con equipos.
- No se realizan conversiones automáticas entre unidades.

## Invariantes de implementación

1. Como máximo existe un borrador y una versión vigente por plan.
2. Publicar una versión afecta únicamente requisitos materializados en el futuro.
3. Los límites numéricos son inclusivos y se persisten como `NUMERIC(20,8)`.
4. Un booleano compara contra el valor esperado configurado; `true` no implica por
   sí mismo conformidad.
5. Producción no ejecuta requisitos `CALIDAD` y Calidad no ejecuta requisitos
   `PROCESO`.
6. Un lote no se libera con requisitos exigibles insatisfechos, resultados pendientes
   de revalidación o desviaciones abiertas.
7. Reabrir un rechazo exige solicitante y aprobador distintos y conserva el rechazo
   y sus firmas.
8. Los PDF y hashes históricos de `batch-record-v2` no se recalculan; las nuevas
   revisiones usan `batch-record-v3`.

## Componentes afectados

- Migraciones Flyway posteriores a V100.
- Dominio, repositorios, servicios y fachadas HTTP de controles.
- Orquestación y documento canónico de Batch Record.
- Matriz de permisos de Producción y Calidad.
- Navegación y formularios compartidos de ambos módulos.

## Validación

La decisión se valida mediante pruebas del dominio, migraciones desde esquemas vacío
y poblado, separación de permisos, concurrencia de transiciones, preservación de
revisiones v2 y ciclos completos OP/OF.

---

## Historial de cambios

| Fecha | Autor | Cambio |
|---|---|---|
| 2026-09-02 | Equipo Exotic App | Registro de la decisión e invariantes del motor unificado |
