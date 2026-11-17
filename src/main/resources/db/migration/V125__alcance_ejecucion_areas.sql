-- Alcance operativo independiente de la visibilidad y del alcance de consulta MPS.
-- AMBOS conserva el comportamiento actual de las areas existentes.
ALTER TABLE area_operativa
    ADD COLUMN alcance_ejecucion VARCHAR(20) NOT NULL DEFAULT 'AMBOS',
    ADD CONSTRAINT chk_area_alcance_ejecucion
        CHECK (alcance_ejecucion IN ('SOLO_OP', 'SOLO_OF', 'AMBOS')),
    ADD CONSTRAINT chk_area_almacen_alcance_ejecucion
        CHECK (area_id <> -1 OR alcance_ejecucion = 'AMBOS');

-- La aplicabilidad se captura al emitir cada nueva OP. Sin default ni backfill:
-- NULL identifica seguimientos historicos y conserva su ejecucion original.
ALTER TABLE seguimiento_orden_area
    ADD COLUMN aplica_ejecucion_op BOOLEAN;

COMMENT ON COLUMN area_operativa.alcance_ejecucion IS
    'Tipos de orden que ejecuta el area. Independiente de visibilidad_mps y alcance_mps. Almacen General (-1) conserva AMBOS.';
COMMENT ON COLUMN seguimiento_orden_area.aplica_ejecucion_op IS
    'Aplicabilidad OP congelada al emitir la orden. NULL conserva el comportamiento historico; FALSE permite omision automatica respetando dependencias.';
