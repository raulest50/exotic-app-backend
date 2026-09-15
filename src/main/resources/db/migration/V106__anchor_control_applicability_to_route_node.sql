ALTER TABLE control_plan_aplicabilidad
    ADD COLUMN frontend_node_id VARCHAR(255);

CREATE INDEX idx_control_plan_aplicabilidad_frontend_node
    ON control_plan_aplicabilidad (frontend_node_id);
