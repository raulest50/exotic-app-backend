# Despliegue del motor unificado de controles

Este documento define las comprobaciones operativas para V101 y V102. Las
migraciones no copian permisos de los tabs históricos a los tabs nuevos.

## 1. Evidencia previa al corte

Ejecutar sobre una copia anonimizada de la base objetivo y conservar los resultados
con la evidencia del despliegue.

### Inventario de accesos existentes

```sql
SELECT
    u.id AS usuario_id,
    u.username,
    ma.modulo,
    ta.tab_id,
    ta.nivel
FROM users u
LEFT JOIN modulo_accesos ma ON ma.user_id = u.id
LEFT JOIN tab_accesos ta ON ta.modulo_acceso_id = ma.id
WHERE ma.modulo IN ('PRODUCCION', 'CALIDAD')
ORDER BY u.username, ma.modulo, ta.tab_id;
```

El cliente debe asignar de forma explícita la nueva matriz. Los tabs incorporados
son:

| Módulo | Tab |
|---|---|
| PRODUCCION | `PLANES_CONTROL_PROCESO` |
| PRODUCCION | `REGISTRAR_CONTROL_PROCESO` |
| PRODUCCION | `DESVIACIONES_CONTROL_PROCESO` |
| PRODUCCION | `HISTORIAL_CONTROL_PROCESO` |
| CALIDAD | `PLANES_CONTROL_CALIDAD` |
| CALIDAD | `REGISTRAR_CONTROL_CALIDAD` |
| CALIDAD | `DESVIACIONES_CONTROL_CALIDAD` |
| CALIDAD | `HISTORIAL_CONTROL_CALIDAD` |

`REVISION_LIBERACION_LOTES` y `CONSULTAR_BATCH_RECORD` conservan sus asignaciones.
Las decisiones reguladas no se autorizan por el nombre reservado de un usuario: el
nivel debe estar asignado explícitamente.

### Línea base de revisiones históricas

```sql
SELECT
    id,
    batch_record_id,
    numero,
    esquema_version,
    plantilla_pdf_version,
    contenido_sha256
FROM batch_record_revision
ORDER BY batch_record_id, numero;
```

Esta salida permite demostrar que una revisión v2 no cambió de contenido, versión ni
hash después de desplegar el lector v3.

### Línea base de controles históricos

```sql
SELECT 'plantillas' AS entidad, COUNT(*) AS total
FROM calidad_control_proceso_plantilla
UNION ALL
SELECT 'caracteristicas', COUNT(*)
FROM calidad_control_proceso_caracteristica
UNION ALL
SELECT 'ejecuciones', COUNT(*)
FROM calidad_control_proceso_ejecucion
UNION ALL
SELECT 'muestras', COUNT(*)
FROM calidad_control_proceso_muestra
UNION ALL
SELECT 'lecturas', COUNT(*)
FROM calidad_control_proceso_lectura;
```

## 2. Validación posterior a Flyway

```sql
SELECT ambito, COUNT(*) FROM control_plan GROUP BY ambito;
SELECT estado, COUNT(*) FROM control_plan_version GROUP BY estado;
SELECT ambito_snapshot, estado, COUNT(*)
FROM control_requerido
GROUP BY ambito_snapshot, estado;
SELECT resultado, COUNT(*) FROM control_ejecucion GROUP BY resultado;
```

Toda plantilla histórica debe tener una versión neutral:

```sql
SELECT lp.id
FROM calidad_control_proceso_plantilla lp
LEFT JOIN control_plan_version v ON v.legacy_plantilla_id = lp.id
WHERE v.id IS NULL;
```

Toda ejecución histórica inequívoca debe mantener su enlace:

```sql
SELECT le.id
FROM calidad_control_proceso_ejecucion le
LEFT JOIN control_ejecucion ne ON ne.legacy_ejecucion_id = le.id
WHERE ne.id IS NULL;
```

Los catálogos ambiguos no deben corregirse mediante equivalencias inferidas. Se
entregan para depuración humana:

```sql
SELECT
    c.id,
    c.nombre,
    c.magnitud_nombre_snapshot,
    c.unidad_nombre_snapshot,
    c.legado_sin_limites,
    c.requiere_depuracion
FROM control_plan_caracteristica c
WHERE c.requiere_depuracion
ORDER BY c.id;
```

Verificar que no existan dos versiones vigentes ni dos borradores del mismo plan:

```sql
SELECT plan_id, estado, COUNT(*)
FROM control_plan_version
WHERE estado IN ('BORRADOR', 'VIGENTE')
GROUP BY plan_id, estado
HAVING COUNT(*) > 1;
```

Durante la ventana de convivencia, cada escritura V077 debe tener contraparte
neutral y cada ejecución neutral de una versión migrada debe conservar su espejo:

```sql
SELECT le.id AS ejecucion_legacy_sin_neutral
FROM calidad_control_proceso_ejecucion le
LEFT JOIN control_ejecucion ne ON ne.legacy_ejecucion_id = le.id
WHERE ne.id IS NULL;

SELECT ne.id AS ejecucion_neutral_migrada_sin_legacy
FROM control_ejecucion ne
JOIN control_requerido r ON r.id = ne.control_requerido_id
JOIN control_plan_version v ON v.id = r.version_id
WHERE v.legacy_plantilla_id IS NOT NULL
  AND ne.legacy_ejecucion_id IS NULL;
```

Ambas consultas deben devolver cero filas mientras la compatibilidad se encuentre
en `BRIDGE`.

## 3. Ventana de compatibilidad de escritura

El backend inicia con:

```text
CONTROL_ENGINE_LEGACY_WRITE_MODE=BRIDGE
```

En este modo, `POST /api/calidad/plantillas*` y
`POST /api/calidad/ejecuciones` permanecen disponibles como adaptadores
temporales. Cada operación escribe las tablas `calidad_*` y el motor neutral en
una sola transacción. Un error de validación, precisión o integridad revierte
ambas escrituras.

El corte se realiza por familia de área:

1. Mantener la versión legada vigente mientras se prepara el borrador nativo.
2. Desde ese momento, no crear ni publicar nuevas plantillas mediante V077.
3. Retirar la plantilla vigente con el adaptador legado; esta operación continúa
   permitida para cerrar coordinadamente la familia.
4. Publicar la versión nativa. El motor impide publicarla mientras siga vigente la
   versión enlazada a V077.
5. Verificar las consultas de conciliación y que ningún consumidor escriba V077.

Las ejecuciones de una versión migrada se reflejan en ambos modelos incluso si ya
existe un borrador nativo. Los valores `NUMERIC(20,8)` que no admitan una
representación `Double` reversible se rechazan mientras la versión migrada esté
vigente; no se redondean de forma silenciosa. Tras publicar una versión nativa deja
de existir esa limitación.

Solo después del inventario y la conciliación global se configura:

```text
CONTROL_ENGINE_LEGACY_WRITE_MODE=RETIRED
```

En `RETIRED`, las mutaciones V077 responden HTTP 410. Sus lecturas históricas se
mantienen hasta retirar formalmente todos los consumidores.

Las consultas V077 solo representan familias y ejecuciones enlazadas al esquema
histórico. No constituyen una vista de los planes nativos: todo consumidor que
necesite observar versiones publicadas después del corte debe migrar también sus
lecturas a `/api/produccion/controles-proceso`.

## 4. Secuencia de despliegue

1. Tomar respaldo y preparar la copia anonimizada.
2. Desplegar backend aditivo con `CONTROL_ENGINE_LEGACY_WRITE_MODE=BRIDGE` y
   ejecutar Flyway sin desactivar su validación.
3. Ejecutar las consultas de conciliación anteriores.
4. Entregar el inventario de accesos y asignar la nueva matriz acordada.
5. Desplegar el frontend y comprobar que los tabs históricos de controles no se
   ofrecen para nuevas operaciones; los integradores aún no migrados continúan por
   el adaptador transaccional.
6. Probar un ciclo OP y uno OF, incluida devolución, reenvío y liberación.
7. Regenerar una muestra de PDF v2 y v3; comparar los hashes v2 contra la línea base.
8. Verificar el archivo de liberación con `BATCH_RECORD_WORKFLOW_ENABLED` activa e
   inactiva.
9. Conciliar las escrituras de la ventana, migrar consumidores externos y cambiar
   a `RETIRED` únicamente en un despliegue posterior.

Las tablas y columnas `calidad_*` no se eliminan en este corte. Su retiro requiere
confirmar primero que no existen consumidores externos ni escrituras legacy.
