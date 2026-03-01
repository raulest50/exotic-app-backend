# Guía de Migraciones con Flyway

## 📋 Contenido
- [Introducción](#introducción)
- [Configuración Actual](#configuración-actual)
- [Nomenclatura de Migraciones](#nomenclatura-de-migraciones)
- [Cómo Crear una Nueva Migración](#cómo-crear-una-nueva-migración)
- [Mejores Prácticas](#mejores-prácticas)
- [Comandos Útiles](#comandos-útiles)
- [Troubleshooting](#troubleshooting)

---

## Introducción

Este proyecto usa **Flyway** para gestionar las migraciones de base de datos de forma automática, versionada y auditable.

### ¿Por qué Flyway?

✅ **Versionamiento automático**: Cada cambio de esquema queda registrado
✅ **Historial auditable**: Tabla `flyway_schema_history` con todas las migraciones aplicadas
✅ **Seguridad**: Migraciones idempotentes y validadas antes de aplicarse
✅ **Multi-ambiente**: Mismo código funciona en dev, staging y producción
✅ **Team-friendly**: Evita conflictos de esquema entre desarrolladores

---

## Configuración Actual

### Ubicación de Migraciones
```
src/main/resources/db/migration/
├── V001__initial_schema.sql
├── V002__schema_populated_by_hibernate.sql
└── V003__migrate_consumo_to_dispensacion.sql
```

### Configuración en `application.yml`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # ⚠️ IMPORTANTE: Solo valida, NO modifica el esquema

  flyway:
    enabled: true
    baseline-on-migrate: true    # Permite migrar DBs existentes
    baseline-version: 2          # Versión inicial para DBs existentes
    locations: classpath:db/migration
    validate-on-migrate: true    # Valida checksums antes de migrar
```

### Dependencias en `build.gradle.kts`

```kotlin
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-database-postgresql")
```

---

## Nomenclatura de Migraciones

### Formato Obligatorio
```
V{versión}__{descripción}.sql
```

### Ejemplos Válidos
- ✅ `V001__initial_schema.sql`
- ✅ `V003__migrate_consumo_to_dispensacion.sql`
- ✅ `V004__add_estado_to_orden_produccion.sql`
- ✅ `V005__create_index_on_transacciones.sql`

### Ejemplos Inválidos
- ❌ `V1_add_column.sql` (falta segundo guion bajo)
- ❌ `v003__add_field.sql` (V debe ser mayúscula)
- ❌ `V003-add-field.sql` (debe usar doble guion bajo)
- ❌ `add_field.sql` (falta versión)

### Reglas Importantes
1. **El número de versión debe ser único y secuencial**
2. **NUNCA modifiques una migración ya aplicada** (Flyway valida checksums)
3. **Usa números con ceros a la izquierda**: V003, V004, etc.
4. **Descripción en snake_case**: palabras separadas por `_`

---

## Cómo Crear una Nueva Migración

### Paso 1: Determinar el Número de Versión

```bash
# Ver la última migración aplicada
ls src/main/resources/db/migration/ | sort | tail -n 1

# O consultar la base de datos
SELECT version, description, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 1;
```

### Paso 2: Crear el Archivo SQL

Crea un nuevo archivo siguiendo la nomenclatura:

```bash
# Ejemplo: La última migración es V003, entonces crea V004
touch src/main/resources/db/migration/V004__add_fecha_vencimiento_to_lote.sql
```

### Paso 3: Escribir el SQL

```sql
-- =====================================================================
-- V004: Agregar fecha de vencimiento a lotes
-- =====================================================================
-- Fecha: 2026-03-02
-- Descripción: Agrega columna fecha_vencimiento para rastrear caducidad
--              de lotes de materiales perecederos.
-- =====================================================================

-- Agregar columna permitiendo NULL inicialmente
ALTER TABLE lote
ADD COLUMN fecha_vencimiento DATE;

-- Actualizar lotes existentes con fecha por defecto (1 año desde hoy)
UPDATE lote
SET fecha_vencimiento = CURRENT_DATE + INTERVAL '1 year'
WHERE fecha_vencimiento IS NULL;

-- Cambiar a NOT NULL si es requerido
-- ALTER TABLE lote ALTER COLUMN fecha_vencimiento SET NOT NULL;

-- Crear índice para búsquedas por fecha
CREATE INDEX idx_lote_fecha_vencimiento ON lote(fecha_vencimiento);
```

### Paso 4: Probar en Dev

```bash
# Iniciar la aplicación - Flyway ejecutará automáticamente las migraciones
./gradlew bootRun

# Verificar en logs que la migración se aplicó:
# "Migrating schema ... to version 004 - add fecha vencimiento to lote [SUCCESS]"
```

### Paso 5: Validar en Base de Datos

```sql
-- Verificar que la columna fue creada
\d lote

-- Verificar historial de Flyway
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

---

## Mejores Prácticas

### 1. Migraciones Idempotentes

**MAL:**
```sql
ALTER TABLE productos ADD COLUMN precio DECIMAL(10,2);
```
☠️ Falla si ejecutas dos veces

**BIEN:**
```sql
ALTER TABLE productos
ADD COLUMN IF NOT EXISTS precio DECIMAL(10,2);
```
✅ Puede ejecutarse múltiples veces sin error

### 2. Manejo de Datos Existentes

```sql
-- SIEMPRE manejar registros existentes
ALTER TABLE productos ADD COLUMN categoria_id INTEGER;

-- Actualizar registros existentes con valor por defecto
UPDATE productos SET categoria_id = 1 WHERE categoria_id IS NULL;

-- Luego aplicar constraint
ALTER TABLE productos ALTER COLUMN categoria_id SET NOT NULL;
```

### 3. Scripts Reversibles Manualmente

Aunque Flyway no tiene rollback automático para SQL, documenta cómo revertir:

```sql
-- =====================================================================
-- V005: Eliminar columna obsoleta
-- =====================================================================
-- Rollback manual:
-- ALTER TABLE productos ADD COLUMN campo_viejo VARCHAR(100);
-- =====================================================================

ALTER TABLE productos DROP COLUMN campo_viejo;
```

### 4. Migraciones de Datos Grandes

Para migraciones pesadas, considera:

```sql
-- Usar transacciones explícitas
BEGIN;

-- Procesar en lotes
UPDATE productos SET estado = 'ACTIVO'
WHERE estado IS NULL
AND producto_id < 10000;

COMMIT;
```

### 5. Separar Schema y Data

```
V004__add_column.sql          ← Cambios de estructura
V004.1__migrate_data.sql      ← Migración de datos
```

---

## Comandos Útiles

### Verificar Estado de Migraciones

```bash
# Iniciar aplicación y ver logs de Flyway
./gradlew bootRun

# O usar Flyway CLI directamente
./gradlew flywayInfo
```

### Validar Migraciones sin Ejecutar

```bash
./gradlew flywayValidate
```

### Limpiar Base de Datos (⚠️ SOLO EN DEV)

```bash
# CUIDADO: Borra TODAS las tablas
./gradlew flywayClean

# Luego volver a migrar
./gradlew flywayMigrate
```

### Ver Historial en Base de Datos

```sql
SELECT
    installed_rank,
    version,
    description,
    type,
    script,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

---

## Troubleshooting

### Error: "Checksum mismatch"

**Causa**: Modificaste una migración ya aplicada

**Solución**:
```sql
-- Opción 1: Reparar (solo si sabes lo que haces)
./gradlew flywayRepair

-- Opción 2: Crear nueva migración con la corrección
-- NO modifiques la migración original
```

### Error: "Found non-empty schema without schema history table"

**Causa**: Base de datos existe pero Flyway nunca se ejecutó

**Solución**: Ya configurado con `baseline-on-migrate: true` en application.yml

### Error: "Migration checksum mismatch"

**Causa**: El archivo SQL cambió después de ser aplicado

**Solución**:
```bash
# Ver el problema
./gradlew flywayInfo

# Reparar tabla de historial (elimina entradas fallidas)
./gradlew flywayRepair
```

### Error: "Validate failed: Detected applied migration not resolved locally"

**Causa**: Una migración existe en la BD pero no en tu código

**Solución**: Sincroniza con el equipo o git pull las migraciones faltantes

---

## Recursos Adicionales

- [Documentación oficial Flyway](https://flywaydb.org/documentation/)
- [Flyway con Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [Convenciones de nomenclatura](https://flywaydb.org/documentation/concepts/migrations.html#versioned-migrations)

---

## 📝 Historial de Cambios

| Versión | Descripción | Fecha |
|---------|-------------|-------|
| V001 | Initial schema baseline | 2026-03-01 |
| V002 | Schema populated by Hibernate (baseline) | 2026-03-01 |
| V003 | Migrar CONSUMO → DISPENSACION | 2026-03-01 |

---

**Última actualización**: 2026-03-01
**Mantenido por**: Equipo de desarrollo Exotic App
