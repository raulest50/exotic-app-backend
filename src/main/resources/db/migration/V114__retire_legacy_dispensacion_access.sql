-- Retira el permiso de la dispensacion antigua sin transferir ni ampliar accesos.
-- Si las asignaciones cambiaron desde la auditoria, exige revisarlas antes de continuar.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tab_accesos legacy
        JOIN modulo_accesos modulo ON modulo.id = legacy.modulo_acceso_id
        WHERE modulo.modulo = 'TRANSACCIONES_ALMACEN'
          AND legacy.tab_id = 'HACER_DISPENSACION'
          AND (
              NOT EXISTS (
                  SELECT 1 FROM tab_accesos actual
                  WHERE actual.modulo_acceso_id = legacy.modulo_acceso_id
                    AND actual.tab_id = 'DISPENSACION_V2'
              )
              OR legacy.nivel > COALESCE((
                  SELECT MAX(actual.nivel) FROM tab_accesos actual
                  WHERE actual.modulo_acceso_id = legacy.modulo_acceso_id
                    AND actual.tab_id <> 'HACER_DISPENSACION'
              ), 0)
          )
    ) THEN
        RAISE EXCEPTION 'Revisar accesos de dispensacion: retirar HACER_DISPENSACION alteraria los permisos efectivos de usuarios existentes';
    END IF;

    DELETE FROM tab_accesos legacy
    USING modulo_accesos modulo
    WHERE modulo.id = legacy.modulo_acceso_id
      AND modulo.modulo = 'TRANSACCIONES_ALMACEN'
      AND legacy.tab_id = 'HACER_DISPENSACION';
END;
$$;
