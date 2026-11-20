package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.activos.fijos.ActivoFijo;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.repo.activos.fijos.ActivoFijoRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Equipo real y codigo del lote OF: conserva su identidad y no reserva capacidad. */
@Service
@RequiredArgsConstructor
public class LoteFabricacionEquipoService {
    private final ActivoFijoRepo activoRepo;
    private final LoteRepo loteRepo;
    private final MasterDirectiveRepo directivaRepo;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    public record Equipo(String id, String nombre, String codigoLoteFabricacion,
                         Long tipoRecursoId, String tipoRecursoNombre, String lotePrevisto) {}
    public record Contexto(List<Equipo> items, boolean requiereEquipoLoteOf,
                           boolean esOperacionSalida, LocalDate fechaLote,
                           boolean loteProvisional, String loteActual) {}

    public String politicaFechaActual() {
        String valor = directivaRepo.findByNombre(MasterDirectiveKeys.OF_LOTE_FECHA_FABRICACION)
                .map(d -> d.getValor()).orElse("TERMINACION");
        if (!Set.of("INICIO", "TERMINACION").contains(valor))
            throw new IllegalStateException("Configure la fecha del lote OF como INICIO o TERMINACION.");
        return valor;
    }

    private JsonNode grafo(OrdenFabricacion orden) {
        try {
            return objectMapper.readTree(orden.getManufacturingVersion().getProcesoProduccionJson());
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo interpretar el proceso congelado de la OF.", exception);
        }
    }

    /** Determina terminales por precedencia, no por el orden en que se reportan. */
    public Set<String> operacionesSalida(OrdenFabricacion orden) {
        JsonNode grafo = grafo(orden);
        Set<String> procesos = new LinkedHashSet<>();
        Map<String, Set<String>> siguientes = new HashMap<>();
        grafo.path("nodes").forEach(n -> {
            String id = n.path("frontendId").asText();
            if ("PROCESO".equalsIgnoreCase(n.path("nodeType").asText())) procesos.add(id);
            siguientes.put(id, new HashSet<>());
        });
        grafo.path("edges").forEach(e -> siguientes.computeIfAbsent(
                e.path("sourceFrontendId").asText(), ignored -> new HashSet<>())
                .add(e.path("targetFrontendId").asText()));
        Set<String> terminales = new LinkedHashSet<>();
        for (String origen : procesos) {
            Deque<String> pendientes = new ArrayDeque<>(siguientes.getOrDefault(origen, Set.of()));
            Set<String> visitados = new HashSet<>();
            boolean tieneProcesoPosterior = false;
            while (!pendientes.isEmpty()) {
                String id = pendientes.removeFirst();
                if (!visitados.add(id)) continue;
                if (procesos.contains(id)) { tieneProcesoPosterior = true; break; }
                pendientes.addAll(siguientes.getOrDefault(id, Set.of()));
            }
            if (!tieneProcesoPosterior) terminales.add(origen);
        }
        return terminales;
    }

    public boolean esSalida(OrdenFabricacionOperacion operacion) {
        Set<String> salidas = operacionesSalida(operacion.getOrdenFabricacion());
        return salidas.size() == 1 && salidas.contains(operacion.getFrontendNodeId());
    }

    private Set<Long> recursosPermitidos(OrdenFabricacionOperacion operacion) {
        Set<Long> permitidos = new LinkedHashSet<>();
        operacion.getAreaOperativa().getRecursosProduccion().forEach(r -> permitidos.add(r.getId()));
        Set<Long> previstos = new LinkedHashSet<>();
        for (JsonNode nodo : grafo(operacion.getOrdenFabricacion()).path("nodes")) {
            if (operacion.getFrontendNodeId().equals(nodo.path("frontendId").asText()))
                nodo.path("recursosProduccionIds").forEach(id -> previstos.add(id.asLong()));
        }
        if (!previstos.isEmpty()) permitidos.retainAll(previstos);
        return permitidos;
    }

    private LocalDate fecha(OrdenFabricacion orden, LocalDateTime terminacion) {
        if ("INICIO".equals(orden.getFechaLoteOrigen())) {
            if (orden.getFechaInicio() == null)
                throw new IllegalStateException("La OF no tiene inicio real para formar la fecha de fabricacion.");
            return orden.getFechaInicio().toLocalDate();
        }
        return terminacion.toLocalDate();
    }

    private String codigo(OrdenFabricacion orden, String equipo, LocalDate fecha, String actual) {
        // Las referencias manuales e historicas nunca se reinterpretan como reservas automaticas.
        if (orden.getConsecutivoLoteReservado() == null) return actual;
        return orden.getPrefijoLoteReservado() + "-"
                + String.format(Locale.ROOT, "%05d", orden.getConsecutivoLoteReservado())
                + "-" + equipo + "-" + fecha.format(DateTimeFormatter.ofPattern("ddMMyy"));
    }

    @Transactional(readOnly = true)
    public Contexto contexto(OrdenFabricacionOperacion operacion) {
        OrdenFabricacion orden = operacion.getOrdenFabricacion();
        Lote lote = loteRepo.findByOrdenFabricacion_OrdenFabricacionId(orden.getOrdenFabricacionId())
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("La OF no tiene lote."));
        boolean salida = esSalida(operacion);
        LocalDate fecha = "INICIO".equals(orden.getFechaLoteOrigen()) && orden.getFechaInicio() == null
                ? null : fecha(orden, orden.getFechaFinal() == null
                ? LocalDateTime.now(applicationClock) : orden.getFechaFinal());
        Set<Long> recursos = salida ? recursosPermitidos(operacion) : Set.of();
        List<Equipo> items = recursos.isEmpty() ? List.of() : activoRepo
                .equiposDeRecursos(ActivoFijo.TipoActivo.PRODUCCION, recursos).stream()
                .map(a -> new Equipo(a.getId(), a.getNombre(), a.getCodigoLoteFabricacion(),
                        a.getTipoRecurso().getId(), a.getTipoRecurso().getNombre(),
                        fecha == null || a.getCodigoLoteFabricacion() == null
                                || !a.getCodigoLoteFabricacion().matches("[0-9]{2}") ? null
                                : codigo(orden, a.getCodigoLoteFabricacion(), fecha, lote.getBatchNumber())))
                .toList();
        return new Contexto(items, salida && operacion.isRequiereEquipoLoteOf(), salida, fecha,
                orden.getConsecutivoLoteReservado() != null && orden.getFechaFinal() == null,
                lote.getBatchNumber());
    }

    public void completar(OrdenFabricacionOperacion operacion, Lote lote, String activoId, LocalDateTime ahora) {
        OrdenFabricacion orden = operacion.getOrdenFabricacion();
        String id = activoId == null || activoId.isBlank() ? null : activoId.strip();
        boolean salida = esSalida(operacion);
        if (!salida && (id != null || operacion.isRequiereEquipoLoteOf()))
            throw new IllegalArgumentException("El equipo del lote requiere una unica operacion de salida en el proceso.");
        if (salida && operacion.isRequiereEquipoLoteOf() && id == null)
            throw new IllegalArgumentException("Seleccione el equipo utilizado para completar esta OF.");
        LocalDate fecha = fecha(orden, ahora);
        if (id != null) {
            ActivoFijo activo = activoRepo.findByIdForLote(id)
                    .orElseThrow(() -> new IllegalArgumentException("El equipo seleccionado no existe."));
            if (activo.getEstado() != 0 || activo.getTipoActivo() != ActivoFijo.TipoActivo.PRODUCCION
                    || activo.getTipoRecurso() == null
                    || !recursosPermitidos(operacion).contains(activo.getTipoRecurso().getId()))
                throw new IllegalArgumentException("El equipo no esta activo o no pertenece a los recursos permitidos de esta operacion.");
            String identificador = activo.getCodigoLoteFabricacion();
            if (identificador == null || !identificador.matches("[0-9]{2}"))
                throw new IllegalArgumentException("Configure en el activo un codigo de lote de fabricacion de dos digitos.");
            String definitivo = codigo(orden, identificador, fecha, lote.getBatchNumber());
            Lote existente = loteRepo.findByBatchNumber(definitivo);
            if (existente != null && !Objects.equals(existente.getId(), lote.getId()))
                throw new IllegalArgumentException("El codigo definitivo del lote ya existe; revise la numeracion.");
            orden.setActivoLote(activo);
            orden.setCodigoEquipoLote(identificador);
            lote.setBatchNumber(definitivo);
        }
        lote.setProductionDate(fecha);
    }
}
