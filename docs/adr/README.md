# Architecture Decision Records (ADR)

## ¿Qué son los ADRs?

Los **Architecture Decision Records (ADRs)** son documentos que capturan decisiones arquitectónicas importantes tomadas durante el desarrollo del proyecto. Cada ADR describe:

- El contexto de la decisión
- Las alternativas consideradas
- La decisión final tomada
- Las consecuencias (positivas y negativas)
- Referencias y justificaciones

## ¿Por qué usamos ADRs?

- **Trazabilidad**: Documentar el "por qué" detrás de decisiones importantes
- **Onboarding**: Ayudar a nuevos desarrolladores a entender el diseño del sistema
- **Revisiones**: Evaluar decisiones pasadas con contexto completo
- **Evitar repetir debates**: Las decisiones ya tomadas están documentadas

## Índice de ADRs

| # | Título | Estado | Fecha |
|---|--------|--------|-------|
| [001](001-transaccion-almacen-multiples-ubicaciones.md) | TransaccionAlmacen con Movimientos a Múltiples Almacenes | ✅ Aceptado | 2026-02-27 |
| [002](002-motor-unificado-controles-proceso-calidad.md) | Motor unificado de controles de Proceso y Calidad | ✅ Aceptado | 2026-09-02 |

## Cómo crear un nuevo ADR

1. Copia el archivo `template.md`
2. Renómbralo con el siguiente número secuencial: `00X-titulo-descriptivo.md`
3. Completa todas las secciones del template
4. Actualiza este README.md agregando el nuevo ADR al índice

## Estados posibles

- ✅ **Aceptado**: Decisión aprobada e implementada
- 🔄 **Propuesto**: En discusión, no implementado aún
- ❌ **Rechazado**: Decisión descartada (se mantiene para registro histórico)
- ⚠️ **Deprecado**: Decisión reemplazada por otra (ver ADR que la reemplaza)
- 📝 **Superseded**: Obsoleto, ver ADR más reciente

## Referencias

- [ADR Guidelines by Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [ADR Tools](https://github.com/npryce/adr-tools)
