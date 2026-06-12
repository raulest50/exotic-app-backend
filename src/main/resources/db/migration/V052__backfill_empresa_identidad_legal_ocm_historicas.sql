UPDATE orden_compra oc
SET empresa_identidad_legal_version_id = eilv.id
FROM empresa_identidad_legal_version eilv
WHERE eilv.version = 1
  AND oc.empresa_identidad_legal_version_id IS NULL
  AND (
      oc.estado IN (2, 3)
      OR oc.fecha_envio_proveedor IS NOT NULL
  );
