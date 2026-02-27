# ADR 001: TransaccionAlmacen con Movimientos a Múltiples Almacenes

## Estado
✅ **Aceptado**

**Fecha**: 2026-02-27

**Autor(es)**: Equipo de Desarrollo Exotic App

---

## Contexto

Exotic App es un sistema ERP para la gestión de procesos internos de una planta manufacturera de productos capilares en Barranquilla, Colombia. El sistema maneja inventarios de materias primas, semi-terminados y productos terminados a través de diferentes almacenes.

### Antecedentes

El modelo de inventarios se basa en dos entidades principales:
- **`TransaccionAlmacen`**: Representa una operación de almacén (encabezado)
- **`Movimiento`**: Representa el movimiento individual de un producto en un almacén específico (línea de detalle)

Cada `TransaccionAlmacen` está causada por una entidad de negocio (Orden de Compra, Orden de Producción, etc.) y se vincula contablemente mediante un `AsientoContable`.

### Problema

Se requiere implementar soporte para un **almacén de averías** donde se registren productos dañados, defectuosos o con problemas de calidad. Esto plantea la pregunta arquitectónica:

**¿Debe una `TransaccionAlmacen` contener movimientos SOLO a un almacén o permitir movimientos a MÚLTIPLES almacenes diferentes?**

Esta decisión impacta:
- Transferencias entre almacenes
- Manejo de scrap en producción
- Ajustes con reclasificación de productos
- Atomicidad de operaciones
- Consistencia contable

---

## Alternativas Consideradas

### Opción 1: Un Solo Almacén por TransaccionAlmacen ❌

**Descripción**: Cada `TransaccionAlmacen` solo puede contener movimientos que afecten a un único almacén. Para transferencias entre almacenes, se crearían dos transacciones separadas vinculadas.

**Pros**:
- ✅ Modelo conceptualmente más simple
- ✅ Queries de kardex más directas (sin filtros de almacén)
- ✅ Fácil de entender para principiantes

**Contras**:
- ❌ **Rompe atomicidad**: Transferencia entre almacenes no sería una operación atómica ACID
- ❌ **Pérdida de trazabilidad**: No hay forma directa de vincular dos transacciones como "la misma operación"
- ❌ **Complejidad contable**: ¿Un asiento contable por transacción o por par de transacciones?
- ❌ **No sigue estándares ERP**: SAP (MB1B), Oracle WMS, Odoo usan una sola transacción
- ❌ **Scrap en producción**: El scrap de una OP iría a AVERIAS, pero sería una transacción separada perdiendo contexto
- ❌ **Riesgo de inconsistencias**: Una transacción podría fallar y la otra no, dejando el inventario en estado inconsistente

### Opción 2: Múltiples Almacenes por TransaccionAlmacen ✅ (ELEGIDA)

**Descripción**: Una `TransaccionAlmacen` puede contener múltiples `Movimiento`s, cada uno con su propio `Almacen`. Esto permite operaciones como transferencias entre almacenes en una sola transacción atómica.

**Pros**:
- ✅ **Atomicidad ACID garantizada**: Todas las operaciones se confirman o revierten juntas
- ✅ **Sigue estándares de industria**: SAP (transacción MB1B), Oracle WMS, Odoo
- ✅ **Trazabilidad completa**: Todos los movimientos relacionados están en la misma transacción
- ✅ **Consistencia contable**: Un solo `AsientoContable` por transacción, sin importar cuántos almacenes
- ✅ **Scrap en producción**: El backflush y el scrap quedan registrados en la misma OP
- ✅ **Transferencias simples**: GENERAL → AVERIAS en una sola operación
- ✅ **Patrón Aggregate Root (DDD)**: `TransaccionAlmacen` es el agregado raíz que garantiza consistencia

**Contras**:
- ⚠️ **Queries de kardex más complejas**: Necesitan filtrar por almacén en el repositorio
- ⚠️ **Validaciones por tipo**: Cada `TipoEntidadCausante` tiene reglas específicas sobre almacenes permitidos

---

## Decisión

**Decidimos permitir que una `TransaccionAlmacen` contenga movimientos a MÚLTIPLES almacenes diferentes.**

### Justificación

1. **Estándar de Industria**: SAP, Oracle y Odoo (líderes en ERP) implementan transferencias entre almacenes como una sola transacción con múltiples líneas de detalle.

2. **Atomicidad ACID**: Una transferencia entre almacenes es conceptualmente UNA operación que debe ser atómica: o se completa toda o no se completa nada.

3. **Casos de Uso Reales**:
   - **OTA (Transferencia)**: Mover productos de GENERAL a AVERIAS
   - **OP (Producción)**: Consumos en GENERAL + backflush a GENERAL + scrap a AVERIAS, todo en una OP
   - **OAA (Ajuste)**: Reclasificar productos entre almacenes por inventario físico

4. **Consistencia Contable**: Un solo `AsientoContable` vinculado a la `TransaccionAlmacen`, independiente de cuántos almacenes afecte.

### Validaciones por TipoEntidadCausante

Aunque el modelo permite múltiples almacenes, cada tipo de transacción tiene reglas específicas:

| TipoEntidadCausante | Almacenes Permitidos | Justificación |
|---------------------|---------------------|---------------|
| **OCM** (Orden Compra) | Solo `GENERAL` | Las compras se reciben en un único punto |
| **OD** (Dispensación) | Solo `GENERAL` | Se dispensa desde un almacén específico |
| **OTA** (Transferencia) | Exactamente 2 diferentes | Origen (salida) → Destino (entrada) |
| **OP** (Producción) | 1 o más | Consumos y backflush en GENERAL, scrap opcional a AVERIAS |
| **OAA** (Ajuste) | 1 o más | Puede ser ajuste simple o reclasificación entre almacenes |
| **CM** (Carga Masiva) | Típicamente `GENERAL` | Carga inicial de inventarios |

Estas validaciones se implementan en el **Service Layer**, no en el modelo de datos.

---

## Consecuencias

### Positivas ✅

- **Transferencias atómicas**: GENERAL → AVERIAS se ejecuta completamente o se revierte completamente
- **Scrap en producción**: Una OP puede generar producto bueno (GENERAL) y scrap (AVERIAS) en la misma transacción
- **Consistencia contable**: Un solo asiento contable por transacción, sin complejidad adicional
- **Trazabilidad mejorada**: Todos los movimientos relacionados están vinculados a la misma `TransaccionAlmacen`
- **Cumplimiento de estándares**: Arquitectura alineada con SAP, Oracle y Odoo
- **Auditabilidad**: El `TipoEntidadCausante` + `idEntidadCausante` vincula todos los movimientos a su origen

### Negativas ⚠️

- **Kardex requiere filtros**: El kardex de un producto debe filtrar por almacén específico en las queries del repositorio
- **Validaciones en Service Layer**: Necesitamos validar que cada tipo de transacción respete las reglas de almacenes permitidos
- **Complejidad conceptual**: Los desarrolladores nuevos deben entender que una transacción puede afectar múltiples almacenes

### Neutras 📌

- El modelo de datos no cambia significativamente; solo se agregan validaciones de negocio
- El enum `Movimiento.Almacen` ya existía con `GENERAL`, `AVERIAS`, `CALIDAD`, `DEVOLUCIONES`

---

## Detalles de Implementación

### Componentes Afectados

1. **`TransaccionAlmacen.java`**:
   - No requiere cambios estructurales
   - Javadoc actualizado para documentar esta decisión

2. **`Movimiento.java`**:
   - Campo `almacen` es individual por movimiento
   - Javadoc actualizado

3. **Services** (validaciones necesarias):
   - `MovimientosService.java`: Validar reglas por `TipoEntidadCausante`
   - `SalidaAlmacenService.java`: Asegurar dispensaciones solo desde GENERAL
   - `ProduccionService.java`: Permitir scrap a AVERIAS en misma transacción

4. **Repositorios** (para kardex):
   - `TransaccionAlmacenRepo.java`: Agregar queries con filtro de almacén
   - `InventarioService.java`: Implementar filtro de almacén en kardex

### Ejemplo de Uso

#### Caso 1: Transferencia entre Almacenes (OTA)
```java
TransaccionAlmacen transaccion = new TransaccionAlmacen();
transaccion.setTipoEntidadCausante(TipoEntidadCausante.OTA);
transaccion.setIdEntidadCausante(ordenTransferenciaId);

List<Movimiento> movimientos = new ArrayList<>();

// Salida de GENERAL
Movimiento salida = new Movimiento();
salida.setProducto(producto);
salida.setCantidad(-10);
salida.setAlmacen(Movimiento.Almacen.GENERAL);
salida.setTipoMovimiento(Movimiento.TipoMovimiento.BAJA);
movimientos.add(salida);

// Entrada a AVERIAS
Movimiento entrada = new Movimiento();
entrada.setProducto(producto);
entrada.setCantidad(+10);
entrada.setAlmacen(Movimiento.Almacen.AVERIAS);
entrada.setTipoMovimiento(Movimiento.TipoMovimiento.PERDIDA);
movimientos.add(entrada);

transaccion.setMovimientosTransaccion(movimientos);
// Ambos movimientos se guardan atómicamente
```

#### Caso 2: Producción con Scrap (OP)
```java
TransaccionAlmacen transaccion = new TransaccionAlmacen();
transaccion.setTipoEntidadCausante(TipoEntidadCausante.OP);
transaccion.setIdEntidadCausante(ordenProduccionId);

List<Movimiento> movimientos = new ArrayList<>();

// Consumo de materias primas (GENERAL)
Movimiento consumo1 = new Movimiento();
consumo1.setProducto(materiaPrima);
consumo1.setCantidad(-50);
consumo1.setAlmacen(Movimiento.Almacen.GENERAL);
consumo1.setTipoMovimiento(Movimiento.TipoMovimiento.CONSUMO);
movimientos.add(consumo1);

// Backflush de producto terminado (GENERAL)
Movimiento backflush = new Movimiento();
backflush.setProducto(productoTerminado);
backflush.setCantidad(+100);
backflush.setAlmacen(Movimiento.Almacen.GENERAL);
backflush.setTipoMovimiento(Movimiento.TipoMovimiento.BACKFLUSH);
movimientos.add(backflush);

// Scrap a AVERIAS
Movimiento scrap = new Movimiento();
scrap.setProducto(productoDefectuoso);
scrap.setCantidad(+2);
scrap.setAlmacen(Movimiento.Almacen.AVERIAS);
scrap.setTipoMovimiento(Movimiento.TipoMovimiento.PERDIDA);
movimientos.add(scrap);

transaccion.setMovimientosTransaccion(movimientos);
// Todo en una transacción atómica
```

### Validaciones Recomendadas (Service Layer)

```java
public void validarTransaccion(TransaccionAlmacen transaccion) {
    Set<Movimiento.Almacen> almacenes = transaccion.getMovimientosTransaccion()
        .stream()
        .map(Movimiento::getAlmacen)
        .collect(Collectors.toSet());

    switch (transaccion.getTipoEntidadCausante()) {
        case OCM:
        case OD:
            if (almacenes.size() > 1 || !almacenes.contains(Movimiento.Almacen.GENERAL)) {
                throw new BusinessException(
                    transaccion.getTipoEntidadCausante() + " solo puede afectar almacén GENERAL"
                );
            }
            break;

        case OTA:
            if (almacenes.size() != 2) {
                throw new BusinessException("OTA debe transferir entre exactamente 2 almacenes");
            }
            // Validar que haya movimientos positivos y negativos
            break;

        case OP:
        case OAA:
            // Permitir 1 o más almacenes
            break;
    }
}
```

---

## Referencias

### Estándares de Industria

- **SAP ERP**: Transacción MB1B (Transfer Posting) - permite movimientos entre almacenes en una sola transacción
- **Oracle WMS**: "Transferencias internas entre almacenes con total trazabilidad" en un mismo documento
- **Odoo ERP**: "Movimientos de stock entre almacenes" como una operación atómica

### Patrones de Diseño

- **Aggregate Root (Domain-Driven Design)**: `TransaccionAlmacen` es el agregado raíz que garantiza la consistencia de todos sus `Movimiento`s hijos
- **ACID Transactions**: Atomicidad, Consistencia, Aislamiento, Durabilidad

### Artículos y Documentación

- [Oracle Warehouse Management - Inventory Transactions](https://docs.oracle.com/en/cloud/saas/warehouse-management/)
- [SAP Extended Warehouse Management](https://www.sap.com/products/scm/extended-warehouse-management.html)
- [Domain-Driven Design: Aggregates](https://martinfowler.com/bliki/DDD_Aggregate.html)

---

## Relacionado

- **Clases Java**:
  - `src/main/java/exotic/app/planta/model/inventarios/TransaccionAlmacen.java`
  - `src/main/java/exotic/app/planta/model/inventarios/Movimiento.java`

- **ADRs Futuros**:
  - ADR 002: Kardex filtrado por almacén (planeado)
  - ADR 003: Validaciones de negocio por TipoEntidadCausante (planeado)

---

## Historial de Cambios

| Fecha | Autor | Cambio |
|-------|-------|--------|
| 2026-02-27 | Equipo Exotic App | Creación inicial del ADR basado en investigación de estándares ERP |
