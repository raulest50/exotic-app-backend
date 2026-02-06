-- Eliminar enlace directo OrdenProduccion -> ProcesoProduccionCompleto.
-- El proceso se obtiene vía Producto (Terminado/SemiTerminado).
ALTER TABLE ordenes_produccion DROP COLUMN IF EXISTS proceso_completo_id;

-- Eliminar enlace OrdenProduccion -> PlanificacionProduccion (entidad eliminada del modelo).
ALTER TABLE ordenes_produccion DROP COLUMN IF EXISTS planificacion_id;

-- Eliminar tabla de planificación (entidad PlanificacionProduccion eliminada).
-- Primero la tabla de unión ManyToMany, luego la entidad.
DROP TABLE IF EXISTS planificacion_recurso;
DROP TABLE IF EXISTS planificacion_produccion;
