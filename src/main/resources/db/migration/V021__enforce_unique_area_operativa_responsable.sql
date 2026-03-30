UPDATE area_operativa
SET responsable_id = NULL
WHERE responsable_id IS NOT NULL;

ALTER TABLE area_operativa
    ADD CONSTRAINT uq_area_operativa_responsable UNIQUE (responsable_id);
