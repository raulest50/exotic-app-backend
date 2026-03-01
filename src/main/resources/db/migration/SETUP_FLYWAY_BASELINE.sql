-- =====================================================================
-- SETUP INICIAL DE FLYWAY PARA BASE DE DATOS EXISTENTE
-- =====================================================================
-- IMPORTANTE: Este script se ejecuta MANUALMENTE una sola vez
-- NO es una migración automática de Flyway
--
-- Ejecutar con:
-- psql -U spring -d lacmdb -f src/main/resources/db/migration/SETUP_FLYWAY_BASELINE.sql
-- =====================================================================

-- Crear tabla de historial de Flyway
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INT NOT NULL PRIMARY KEY,
    version VARCHAR(50),
    description VARCHAR(200),
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT NOW(),
    execution_time INT NOT NULL,
    success BOOLEAN NOT NULL
);

-- Insertar baseline (versión 2)
-- Esto le dice a Flyway que el esquema ya existe en versión 2
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES (1, '2', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'spring', NOW(), 0, true)
ON CONFLICT (installed_rank) DO NOTHING;

-- Verificar
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
