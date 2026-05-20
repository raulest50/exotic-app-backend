-- Permite clasificar directivas maestras operativas de compras/almacen.
-- En bases existentes, Hibernate ya habia creado un CHECK con los valores
-- antiguos del enum MasterDirective.GRUPO. En bases nuevas, la tabla puede ser
-- creada despues por ddl-auto, por eso se usa IF EXISTS.

ALTER TABLE IF EXISTS master_directive
DROP CONSTRAINT IF EXISTS master_directive_grupo_check;

ALTER TABLE IF EXISTS master_directive
ADD CONSTRAINT master_directive_grupo_check
CHECK (grupo IN ('FLEXIBILIDAD_CONTROL', 'COMPRAS_ALMACEN'));
