package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.despeje.ItemPlantillaDespeje.Tipo;
import exotic.app.planta.model.produccion.despeje.ItemPlantillaDespeje.Valor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Contrato explícito: las identidades y el contexto se resuelven en el servidor. */
public final class DespejeLineaDTOs {
    private DespejeLineaDTOs() {}
    public record Actor(Long id, String username, String nombre) {}
    public record Config(Integer areaId, boolean requiereDespeje, boolean requiereRevision,
                         long revisionContexto, boolean antecedenteConocido,
                         Long ultimoLoteId, String ultimoLoteNumero, Long ocupanteSeguimientoId,
                         Long ocupanteOperacionId, long version) {}
    public record ConfigRequest(Boolean requiereDespeje, Boolean requiereRevision,
                                Boolean confirmaLineaExclusiva, Long version) {}
    public record ItemRequest(Integer orden, String texto, Tipo tipo, Boolean obligatorio,
                              Boolean permiteNa, Valor respuestaConforme, BigDecimal minimo, BigDecimal maximo) {}
    public record Item(Long id, int orden, String texto, Tipo tipo, boolean obligatorio,
                       boolean permiteNa, Valor respuestaConforme, BigDecimal minimo, BigDecimal maximo) {}
    public record PlantillaRequest(Long id, Long version, String nombre, String motivoCambio,
                                   List<ItemRequest> items) {}
    public record Plantilla(Long id, Integer areaId, int numero, String nombre, String estado,
                            String motivoCambio, LocalDateTime vigenteDesde, LocalDateTime vigenteHasta,
                            long version, List<Item> items) {}
    public record PrepararRequest(Long seguimientoId, Long operacionId, Boolean nuevoIntento) {}
    public record Preparacion(boolean requiereDespeje, boolean requiereRevision,
                              boolean puedeIniciar, String motivo, Ejecucion ejecucion) {}
    public record Respuesta(Long itemId, Valor valor, BigDecimal numero, String observacion) {}
    public record GuardarRequest(Long version, Boolean confirmaAntecedenteDesconocido,
                                 String observacionAntecedente, String observaciones,
                                 List<Respuesta> respuestas) {}
    public record EnviarRequest(Long version) {}
    public record RevisarRequest(Long version, Boolean aprobar, String motivo) {}
    public record Ejecucion(Long id, Integer areaId, String areaNombre, Long seguimientoId,
                            Long operacionId, Integer ordenProduccionId, Long ordenFabricacionId,
                            int intento, long revisionContexto, String estado, Plantilla plantilla,
                            boolean requiereRevision, Long loteActualId, String loteActualNumero,
                            String productoActualId, String productoActualNombre,
                            Long loteAnteriorId, String loteAnteriorNumero,
                            String productoAnteriorId, String productoAnteriorNombre, boolean antecedenteConocido,
                            boolean confirmaAntecedenteDesconocido, String observacionAntecedente,
                            String observaciones, String motivoRevision, String motivoInvalidacion,
                            LocalDateTime creadaEn, LocalDateTime enviadaEn, LocalDateTime revisadaEn,
                            LocalDateTime utilizadaEn, Actor creadaPor, Actor enviadaPor, Actor revisadaPor,
                            String contenidoSha256, long version, List<Respuesta> respuestas) {}
}
