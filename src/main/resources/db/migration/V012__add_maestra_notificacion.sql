-- =====================================================================
-- V012: Crear tablas de Maestra Notificaciones
-- =====================================================================
-- Fecha: 2026-03-19
-- Descripción: Crea la tabla maestra_notificacion (catálogo fijo de tipos
--              de notificación) y la tabla intermedia maestra_notificacion_users
--              que relaciona notificaciones con los usuarios que las reciben.
--
-- Los registros de catálogo son insertados por MaestraNotificacionInitializer
-- al arranque de la aplicación (Java initializer, no por este script).
-- =====================================================================

CREATE TABLE IF NOT EXISTS maestra_notificacion (
    id          INTEGER      PRIMARY KEY,
    nombre      VARCHAR(255),
    descripcion VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS maestra_notificacion_users (
    notificacion_id INTEGER NOT NULL REFERENCES maestra_notificacion(id) ON DELETE CASCADE,
    user_id         BIGINT  NOT NULL REFERENCES users(id)                ON DELETE CASCADE,
    PRIMARY KEY (notificacion_id, user_id)
);
