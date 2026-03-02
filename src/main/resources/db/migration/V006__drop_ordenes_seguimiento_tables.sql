-- V006: Eliminar tablas ordenes_seguimiento y recurso_asignado_orden
-- Estas entidades nunca fueron pobladas y su funcionalidad fue reemplazada
-- por el sistema de insumos desglosados (InsumoDesglosado/InsumoRecursivo).

DROP TABLE IF EXISTS "recurso_asignado_orden" CASCADE;
DROP TABLE IF EXISTS "ordenes_seguimiento" CASCADE;
