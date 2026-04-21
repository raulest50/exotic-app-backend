-- Reemplaza las tres tabs historicas de carga masiva por una sola tab CARGAS_MASIVAS.
-- Migra permisos existentes preservando el mayor nivel asignado y elimina las tabs viejas.

INSERT INTO tab_accesos (modulo_acceso_id, tab_id, nivel)
SELECT
    ta.modulo_acceso_id,
    'CARGAS_MASIVAS',
    MAX(ta.nivel) AS nivel
FROM tab_accesos ta
JOIN modulo_accesos ma ON ma.id = ta.modulo_acceso_id
WHERE ma.modulo = 'OPERACIONES_CRITICAS_BD'
  AND ta.tab_id IN ('CARGA_MASIVA_ALMACEN', 'CARGA_MASIVA_MATERIALES', 'CARGA_MASIVA_TERMINADOS')
GROUP BY ta.modulo_acceso_id
ON CONFLICT (modulo_acceso_id, tab_id) DO UPDATE
SET nivel = GREATEST(tab_accesos.nivel, EXCLUDED.nivel);

DELETE FROM tab_accesos ta
USING modulo_accesos ma
WHERE ta.modulo_acceso_id = ma.id
  AND ma.modulo = 'OPERACIONES_CRITICAS_BD'
  AND ta.tab_id IN ('CARGA_MASIVA_ALMACEN', 'CARGA_MASIVA_MATERIALES', 'CARGA_MASIVA_TERMINADOS');
