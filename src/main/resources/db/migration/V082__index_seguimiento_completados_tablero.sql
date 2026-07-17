CREATE INDEX IF NOT EXISTS idx_soa_area_estado_completado
    ON seguimiento_orden_area (
        area_operativa_id,
        estado,
        fecha_completado DESC NULLS LAST,
        id DESC
    );
