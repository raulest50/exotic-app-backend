-- Retira exclusivamente las plantillas de manufactura por categoria.
-- Respaldar sus datos antes del despliegue: esta migracion elimina las seis tablas.
-- Los productos creados desde plantilla tienen su propia manufactura y versiones;
-- se conservan categorias, productos, insumos reales, empaques, procesos y areas.
-- V064 permanece intacta como parte del historial de Flyway.
--
-- Primero las tablas hijas. RESTRICT impide eliminar dependencias externas
-- inesperadas; Flyway ejecuta esta migracion en una transaccion en PostgreSQL.
DROP TABLE categoria_template_proceso_edge RESTRICT;
DROP TABLE categoria_template_proceso_node RESTRICT;
DROP TABLE categoria_template_insumo_empaque RESTRICT;
DROP TABLE categoria_template_case_pack RESTRICT;
DROP TABLE categoria_template_insumo RESTRICT;
DROP TABLE categoria_manufacturing_template RESTRICT;

-- Retira solo el permiso de la funcion eliminada. Conserva MAIN y Categorias.
DELETE FROM tab_accesos permiso
USING modulo_accesos modulo
WHERE modulo.id = permiso.modulo_acceso_id
  AND modulo.modulo = 'PRODUCTOS'
  AND permiso.tab_id = 'CREAR_DESDE_PLANTILLA';
