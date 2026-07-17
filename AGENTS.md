# Database Migration Safety

- Flyway must remain enabled in every application environment, including local development, automated tests, staging, and production.
- Never set `spring.flyway.enabled=false`, `SPRING_FLYWAY_ENABLED=false`, or an equivalent override.
- Keep Hibernate schema management in validation mode with `spring.jpa.hibernate.ddl-auto=validate` (or `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`). Do not use `none`, `update`, `create`, or `create-drop` to bypass Flyway.
- If a migration prevents startup, diagnose and correct the migration or the affected schema. Do not disable Flyway or Hibernate validation as a workaround.
- Treat changes to migration settings, Render environment variables, and Flyway history as database-sensitive operations. Require explicit user authorization before modifying them.
