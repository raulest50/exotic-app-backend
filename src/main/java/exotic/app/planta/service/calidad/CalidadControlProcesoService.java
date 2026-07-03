package exotic.app.planta.service.calidad;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.calidad.ControlProcesoCaracteristica;
import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.calidad.ControlProcesoLectura;
import exotic.app.planta.model.calidad.ControlProcesoMuestra;
import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import exotic.app.planta.model.calidad.TipoCaracteristicaControlProceso;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.AreaOperativaResumen;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.CaracteristicaRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.CaracteristicaResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.EjecucionDetalleResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.EjecucionListItemResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.EjecucionRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.LecturaRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.LecturaResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.LoteProduccionResumen;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.MuestraRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.MuestraResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PlantillaRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PlantillaResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PrepararEjecucionResponse;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.ProductoResumen;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CalidadControlProcesoService {

    private final ControlProcesoPlantillaRepo plantillaRepo;
    private final ControlProcesoEjecucionRepo ejecucionRepo;
    private final AreaProduccionRepo areaProduccionRepo;
    private final LoteRepo loteRepo;

    @Transactional(readOnly = true)
    public List<PlantillaResponse> listarPlantillas(Integer areaId, EstadoControlProcesoPlantilla estado) {
        return plantillaRepo.buscar(areaId, estado).stream()
                .map(this::toPlantillaResponse)
                .toList();
    }

    @Transactional
    public PlantillaResponse guardarBorrador(PlantillaRequest request) {
        if (request == null || request.getAreaOperativaId() == null) {
            throw new IllegalArgumentException("Debe seleccionar un area operativa.");
        }

        AreaOperativa area = areaProduccionRepo.findById(request.getAreaOperativaId())
                .orElseThrow(() -> new NoSuchElementException("Area operativa no encontrada."));

        ControlProcesoPlantilla plantilla = plantillaRepo
                .findFirstByAreaOperativa_AreaIdAndEstado(area.getAreaId(), EstadoControlProcesoPlantilla.BORRADOR)
                .orElseGet(() -> {
                    ControlProcesoPlantilla nueva = new ControlProcesoPlantilla();
                    nueva.setAreaOperativa(area);
                    nueva.setVersion(plantillaRepo.maxVersionByAreaId(area.getAreaId()) + 1);
                    nueva.setEstado(EstadoControlProcesoPlantilla.BORRADOR);
                    return nueva;
                });

        reemplazarCaracteristicas(plantilla, request.getCaracteristicas());
        return toPlantillaResponse(plantillaRepo.saveAndFlush(plantilla));
    }

    @Transactional
    public PlantillaResponse publicarPlantilla(Long plantillaId) {
        ControlProcesoPlantilla plantilla = plantillaRepo.findById(plantillaId)
                .orElseThrow(() -> new NoSuchElementException("Plantilla no encontrada."));

        if (plantilla.getEstado() != EstadoControlProcesoPlantilla.BORRADOR) {
            throw new IllegalArgumentException("Solo se puede publicar una plantilla en borrador.");
        }
        if (plantilla.getCaracteristicas().isEmpty()) {
            throw new IllegalArgumentException("La plantilla debe tener al menos una caracteristica.");
        }

        plantillaRepo.findFirstByAreaOperativa_AreaIdAndEstado(
                        plantilla.getAreaOperativa().getAreaId(),
                        EstadoControlProcesoPlantilla.VIGENTE
                )
                .ifPresent(vigente -> {
                    vigente.setEstado(EstadoControlProcesoPlantilla.RETIRADA);
                    plantillaRepo.flush();
                });

        plantilla.setEstado(EstadoControlProcesoPlantilla.VIGENTE);
        return toPlantillaResponse(plantillaRepo.saveAndFlush(plantilla));
    }

    @Transactional
    public PlantillaResponse retirarPlantilla(Long plantillaId) {
        ControlProcesoPlantilla plantilla = plantillaRepo.findById(plantillaId)
                .orElseThrow(() -> new NoSuchElementException("Plantilla no encontrada."));
        plantilla.setEstado(EstadoControlProcesoPlantilla.RETIRADA);
        return toPlantillaResponse(plantillaRepo.saveAndFlush(plantilla));
    }

    @Transactional(readOnly = true)
    public PlantillaResponse plantillaVigente(Integer areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("Debe seleccionar un area operativa.");
        }
        return plantillaRepo.findFirstByAreaOperativa_AreaIdAndEstado(areaId, EstadoControlProcesoPlantilla.VIGENTE)
                .map(this::toPlantillaResponse)
                .orElseThrow(() -> new NoSuchElementException("No hay plantilla vigente para el area operativa."));
    }

    @Transactional(readOnly = true)
    public List<LoteProduccionResumen> buscarLotesProduccion(String search, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(0, safeSize);
        return loteRepo.searchProduccionLotes(normalizarBusqueda(search), pageable).stream()
                .filter(this::esLoteProduccionTerminado)
                .map(this::toLoteProduccionResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public PrepararEjecucionResponse prepararEjecucion(Integer areaId, Long loteId) {
        if (areaId == null) {
            throw new IllegalArgumentException("Debe seleccionar un area operativa.");
        }
        if (loteId == null) {
            throw new IllegalArgumentException("Debe seleccionar un lote.");
        }
        ControlProcesoPlantilla plantilla = plantillaRepo
                .findFirstByAreaOperativa_AreaIdAndEstado(areaId, EstadoControlProcesoPlantilla.VIGENTE)
                .orElseThrow(() -> new NoSuchElementException("No hay plantilla vigente para el area operativa."));
        Lote lote = loteRepo.findById(loteId)
                .orElseThrow(() -> new NoSuchElementException("Lote no encontrado."));
        validarLoteProduccionTerminado(lote);

        return PrepararEjecucionResponse.builder()
                .plantilla(toPlantillaResponse(plantilla))
                .lote(toLoteProduccionResumen(lote))
                .build();
    }

    @Transactional
    public EjecucionDetalleResponse guardarEjecucion(User usuario, EjecucionRequest request) {
        if (usuario == null) {
            throw new IllegalArgumentException("Usuario no encontrado.");
        }
        if (request == null || request.getPlantillaId() == null || request.getLoteId() == null) {
            throw new IllegalArgumentException("Debe seleccionar plantilla y lote.");
        }

        ControlProcesoPlantilla plantilla = plantillaRepo.findById(request.getPlantillaId())
                .orElseThrow(() -> new NoSuchElementException("Plantilla no encontrada."));
        if (plantilla.getEstado() != EstadoControlProcesoPlantilla.VIGENTE) {
            throw new IllegalArgumentException("Solo se pueden diligenciar plantillas vigentes.");
        }

        Lote lote = loteRepo.findById(request.getLoteId())
                .orElseThrow(() -> new NoSuchElementException("Lote no encontrado."));
        validarLoteProduccionTerminado(lote);

        ControlProcesoEjecucion ejecucion = new ControlProcesoEjecucion();
        ejecucion.setPlantilla(plantilla);
        ejecucion.setLote(lote);
        ejecucion.setUsuario(usuario);
        ejecucion.setFechaRegistro(AppTime.now());
        ejecucion.setObservaciones(trimToNull(request.getObservaciones()));

        List<ControlProcesoMuestra> muestras = construirMuestrasValidadas(plantilla, ejecucion, request.getMuestras());
        ejecucion.getMuestras().addAll(muestras);

        return toEjecucionDetalle(ejecucionRepo.saveAndFlush(ejecucion));
    }

    @Transactional(readOnly = true)
    public Page<EjecucionListItemResponse> buscarEjecuciones(
            Integer areaId,
            Long loteId,
            String producto,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            int page,
            int size
    ) {
        LocalDateTime desde = fechaDesde == null ? null : fechaDesde.atStartOfDay();
        LocalDateTime hasta = fechaHasta == null ? null : fechaHasta.atTime(LocalTime.MAX);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "fechaRegistro"));

        return ejecucionRepo.buscar(areaId, loteId, normalizarBusqueda(producto), desde, hasta, pageable)
                .map(this::toEjecucionListItem);
    }

    @Transactional(readOnly = true)
    public EjecucionDetalleResponse detalleEjecucion(Long ejecucionId) {
        ControlProcesoEjecucion ejecucion = ejecucionRepo.findById(ejecucionId)
                .orElseThrow(() -> new NoSuchElementException("Control de proceso no encontrado."));
        return toEjecucionDetalle(ejecucion);
    }

    private void reemplazarCaracteristicas(ControlProcesoPlantilla plantilla, List<CaracteristicaRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("La plantilla debe tener al menos una caracteristica.");
        }

        boolean existente = plantilla.getId() != null;
        plantilla.getCaracteristicas().clear();
        if (existente) {
            plantillaRepo.flush();
        }

        Set<Integer> ordenes = new HashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            CaracteristicaRequest request = requests.get(i);
            ControlProcesoCaracteristica caracteristica = construirCaracteristica(plantilla, request, i + 1, ordenes);
            plantilla.getCaracteristicas().add(caracteristica);
        }
    }

    private ControlProcesoCaracteristica construirCaracteristica(
            ControlProcesoPlantilla plantilla,
            CaracteristicaRequest request,
            int ordenPorDefecto,
            Set<Integer> ordenes
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Caracteristica invalida.");
        }

        String nombre = trimToNull(request.getNombre());
        if (nombre == null) {
            throw new IllegalArgumentException("Cada caracteristica debe tener nombre.");
        }
        TipoCaracteristicaControlProceso tipo = request.getTipo();
        if (tipo == null) {
            throw new IllegalArgumentException("Cada caracteristica debe tener tipo de medicion.");
        }
        int orden = request.getOrden() == null ? ordenPorDefecto : request.getOrden();
        if (orden <= 0 || !ordenes.add(orden)) {
            throw new IllegalArgumentException("El orden de las caracteristicas debe ser positivo y unico.");
        }
        int cantidadMuestras = validarEnteroPositivo(request.getCantidadMuestras(), "cantidad de muestras");
        int unidadesPorMuestra = validarEnteroPositivo(request.getUnidadesPorMuestra(), "unidades por muestra");

        ControlProcesoCaracteristica caracteristica = new ControlProcesoCaracteristica();
        caracteristica.setPlantilla(plantilla);
        caracteristica.setNombre(nombre);
        caracteristica.setTipo(tipo);
        caracteristica.setOrden(orden);
        caracteristica.setCantidadMuestras(cantidadMuestras);
        caracteristica.setUnidadesPorMuestra(unidadesPorMuestra);

        if (tipo == TipoCaracteristicaControlProceso.NUMERICA) {
            Double limiteInferior = validarDoubleFinito(request.getLimiteInferior(), "limite inferior");
            Double limiteSuperior = validarDoubleFinito(request.getLimiteSuperior(), "limite superior");
            if (limiteInferior != null && limiteSuperior != null && limiteInferior > limiteSuperior) {
                throw new IllegalArgumentException("El limite inferior no puede ser mayor que el limite superior.");
            }
            caracteristica.setUnidad(trimToNull(request.getUnidad()));
            caracteristica.setLimiteInferior(limiteInferior);
            caracteristica.setLimiteSuperior(limiteSuperior);
        }

        return caracteristica;
    }

    private List<ControlProcesoMuestra> construirMuestrasValidadas(
            ControlProcesoPlantilla plantilla,
            ControlProcesoEjecucion ejecucion,
            List<MuestraRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Debe diligenciar las muestras del control de proceso.");
        }

        Map<Long, ControlProcesoCaracteristica> caracteristicas = new HashMap<>();
        for (ControlProcesoCaracteristica caracteristica : plantilla.getCaracteristicas()) {
            caracteristicas.put(caracteristica.getId(), caracteristica);
        }

        Map<String, MuestraRequest> muestrasPorClave = new LinkedHashMap<>();
        for (MuestraRequest request : requests) {
            if (request == null || request.getCaracteristicaId() == null || request.getNumeroMuestra() == null) {
                throw new IllegalArgumentException("Todas las muestras deben indicar caracteristica y numero.");
            }
            ControlProcesoCaracteristica caracteristica = caracteristicas.get(request.getCaracteristicaId());
            if (caracteristica == null) {
                throw new IllegalArgumentException("La muestra referencia una caracteristica que no pertenece a la plantilla.");
            }
            int numeroMuestra = request.getNumeroMuestra();
            if (numeroMuestra < 1 || numeroMuestra > caracteristica.getCantidadMuestras()) {
                throw new IllegalArgumentException("Numero de muestra fuera del rango definido para " + caracteristica.getNombre() + ".");
            }
            String clave = claveMuestra(caracteristica.getId(), numeroMuestra);
            if (muestrasPorClave.put(clave, request) != null) {
                throw new IllegalArgumentException("Hay muestras duplicadas en el control de proceso.");
            }
        }

        List<ControlProcesoMuestra> muestras = new ArrayList<>();
        for (ControlProcesoCaracteristica caracteristica : plantilla.getCaracteristicas()) {
            for (int numeroMuestra = 1; numeroMuestra <= caracteristica.getCantidadMuestras(); numeroMuestra++) {
                MuestraRequest request = muestrasPorClave.get(claveMuestra(caracteristica.getId(), numeroMuestra));
                if (request == null) {
                    throw new IllegalArgumentException("Falta la muestra " + numeroMuestra + " de " + caracteristica.getNombre() + ".");
                }
                ControlProcesoMuestra muestra = new ControlProcesoMuestra();
                muestra.setEjecucion(ejecucion);
                muestra.setCaracteristica(caracteristica);
                muestra.setNumeroMuestra(numeroMuestra);
                muestra.getLecturas().addAll(construirLecturasValidadas(caracteristica, muestra, request.getLecturas()));
                muestras.add(muestra);
            }
        }

        return muestras;
    }

    private List<ControlProcesoLectura> construirLecturasValidadas(
            ControlProcesoCaracteristica caracteristica,
            ControlProcesoMuestra muestra,
            List<LecturaRequest> requests
    ) {
        if (requests == null || requests.size() != caracteristica.getUnidadesPorMuestra()) {
            throw new IllegalArgumentException("La muestra de " + caracteristica.getNombre() + " debe tener "
                    + caracteristica.getUnidadesPorMuestra() + " lecturas.");
        }

        Map<Integer, LecturaRequest> lecturasPorIndice = new HashMap<>();
        for (LecturaRequest request : requests) {
            if (request == null || request.getIndiceUnidad() == null) {
                throw new IllegalArgumentException("Todas las lecturas deben indicar indice de unidad.");
            }
            int indiceUnidad = request.getIndiceUnidad();
            if (indiceUnidad < 1 || indiceUnidad > caracteristica.getUnidadesPorMuestra()) {
                throw new IllegalArgumentException("Indice de unidad fuera del rango definido para " + caracteristica.getNombre() + ".");
            }
            if (lecturasPorIndice.put(indiceUnidad, request) != null) {
                throw new IllegalArgumentException("Hay lecturas duplicadas en una muestra.");
            }
        }

        List<ControlProcesoLectura> lecturas = new ArrayList<>();
        for (int indiceUnidad = 1; indiceUnidad <= caracteristica.getUnidadesPorMuestra(); indiceUnidad++) {
            LecturaRequest request = lecturasPorIndice.get(indiceUnidad);
            if (request == null) {
                throw new IllegalArgumentException("Falta una lectura de " + caracteristica.getNombre() + ".");
            }
            ControlProcesoLectura lectura = new ControlProcesoLectura();
            lectura.setMuestra(muestra);
            lectura.setIndiceUnidad(indiceUnidad);

            if (caracteristica.getTipo() == TipoCaracteristicaControlProceso.NUMERICA) {
                if (request.getValorNumerico() == null || request.getValorBooleano() != null) {
                    throw new IllegalArgumentException("La caracteristica " + caracteristica.getNombre() + " requiere valores numericos.");
                }
                lectura.setValorNumerico(validarDoubleFinito(request.getValorNumerico(), "valor numerico"));
            } else if (caracteristica.getTipo() == TipoCaracteristicaControlProceso.BOOLEANA) {
                if (request.getValorBooleano() == null || request.getValorNumerico() != null) {
                    throw new IllegalArgumentException("La caracteristica " + caracteristica.getNombre() + " requiere cumple/no cumple.");
                }
                lectura.setValorBooleano(request.getValorBooleano());
            }
            lecturas.add(lectura);
        }

        return lecturas;
    }

    private PlantillaResponse toPlantillaResponse(ControlProcesoPlantilla plantilla) {
        return PlantillaResponse.builder()
                .id(plantilla.getId())
                .areaOperativa(toAreaResumen(plantilla.getAreaOperativa()))
                .version(plantilla.getVersion())
                .estado(plantilla.getEstado())
                .caracteristicas(plantilla.getCaracteristicas().stream()
                        .sorted(Comparator.comparing(ControlProcesoCaracteristica::getOrden))
                        .map(this::toCaracteristicaResponse)
                        .toList())
                .build();
    }

    private CaracteristicaResponse toCaracteristicaResponse(ControlProcesoCaracteristica caracteristica) {
        return CaracteristicaResponse.builder()
                .id(caracteristica.getId())
                .nombre(caracteristica.getNombre())
                .tipo(caracteristica.getTipo())
                .unidad(caracteristica.getUnidad())
                .orden(caracteristica.getOrden())
                .cantidadMuestras(caracteristica.getCantidadMuestras())
                .unidadesPorMuestra(caracteristica.getUnidadesPorMuestra())
                .limiteInferior(caracteristica.getLimiteInferior())
                .limiteSuperior(caracteristica.getLimiteSuperior())
                .build();
    }

    private EjecucionListItemResponse toEjecucionListItem(ControlProcesoEjecucion ejecucion) {
        return EjecucionListItemResponse.builder()
                .id(ejecucion.getId())
                .plantillaId(ejecucion.getPlantilla().getId())
                .plantillaVersion(ejecucion.getPlantilla().getVersion())
                .areaOperativa(toAreaResumen(ejecucion.getPlantilla().getAreaOperativa()))
                .lote(toLoteProduccionResumen(ejecucion.getLote()))
                .usuarioUsername(ejecucion.getUsuario().getUsername())
                .usuarioNombreCompleto(ejecucion.getUsuario().getNombreCompleto())
                .fechaRegistro(ejecucion.getFechaRegistro())
                .observaciones(ejecucion.getObservaciones())
                .build();
    }

    private EjecucionDetalleResponse toEjecucionDetalle(ControlProcesoEjecucion ejecucion) {
        List<MuestraResponse> muestras = ejecucion.getMuestras().stream()
                .sorted(Comparator
                        .comparing((ControlProcesoMuestra muestra) -> muestra.getCaracteristica().getOrden())
                        .thenComparing(ControlProcesoMuestra::getNumeroMuestra))
                .map(this::toMuestraResponse)
                .toList();

        return EjecucionDetalleResponse.builder()
                .id(ejecucion.getId())
                .plantillaId(ejecucion.getPlantilla().getId())
                .plantillaVersion(ejecucion.getPlantilla().getVersion())
                .areaOperativa(toAreaResumen(ejecucion.getPlantilla().getAreaOperativa()))
                .lote(toLoteProduccionResumen(ejecucion.getLote()))
                .usuarioUsername(ejecucion.getUsuario().getUsername())
                .usuarioNombreCompleto(ejecucion.getUsuario().getNombreCompleto())
                .fechaRegistro(ejecucion.getFechaRegistro())
                .observaciones(ejecucion.getObservaciones())
                .muestras(muestras)
                .build();
    }

    private MuestraResponse toMuestraResponse(ControlProcesoMuestra muestra) {
        ControlProcesoCaracteristica caracteristica = muestra.getCaracteristica();
        return MuestraResponse.builder()
                .id(muestra.getId())
                .caracteristicaId(caracteristica.getId())
                .caracteristicaNombre(caracteristica.getNombre())
                .tipo(caracteristica.getTipo())
                .unidad(caracteristica.getUnidad())
                .numeroMuestra(muestra.getNumeroMuestra())
                .lecturas(muestra.getLecturas().stream()
                        .sorted(Comparator.comparing(ControlProcesoLectura::getIndiceUnidad))
                        .map(this::toLecturaResponse)
                        .toList())
                .build();
    }

    private LecturaResponse toLecturaResponse(ControlProcesoLectura lectura) {
        return LecturaResponse.builder()
                .id(lectura.getId())
                .indiceUnidad(lectura.getIndiceUnidad())
                .valorNumerico(lectura.getValorNumerico())
                .valorBooleano(lectura.getValorBooleano())
                .build();
    }

    private AreaOperativaResumen toAreaResumen(AreaOperativa area) {
        return AreaOperativaResumen.builder()
                .areaId(area.getAreaId())
                .nombre(area.getNombre())
                .build();
    }

    private LoteProduccionResumen toLoteProduccionResumen(Lote lote) {
        OrdenProduccion orden = lote.getOrdenProduccion();
        Producto producto = orden == null ? null : orden.getProducto();
        return LoteProduccionResumen.builder()
                .id(lote.getId())
                .batchNumber(lote.getBatchNumber())
                .productionDate(lote.getProductionDate())
                .expirationDate(lote.getExpirationDate())
                .ordenProduccionId(orden == null ? null : orden.getOrdenId())
                .producto(producto == null ? null : ProductoResumen.builder()
                        .productoId(producto.getProductoId())
                        .nombre(producto.getNombre())
                        .build())
                .build();
    }

    private void validarLoteProduccionTerminado(Lote lote) {
        if (!esLoteProduccionTerminado(lote)) {
            throw new IllegalArgumentException("Debe seleccionar un lote de produccion de producto terminado.");
        }
    }

    private boolean esLoteProduccionTerminado(Lote lote) {
        if (lote == null || lote.getOrdenProduccion() == null || lote.getOrdenProduccion().getProducto() == null) {
            return false;
        }
        Object producto = Hibernate.unproxy(lote.getOrdenProduccion().getProducto());
        return producto instanceof Terminado;
    }

    private int validarEnteroPositivo(Integer value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("El campo " + label + " debe ser mayor que cero.");
        }
        return value;
    }

    private Double validarDoubleFinito(Double value, String label) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException("El campo " + label + " debe ser finito.");
        }
        return value;
    }

    private String normalizarBusqueda(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String claveMuestra(Long caracteristicaId, Integer numeroMuestra) {
        return Objects.toString(caracteristicaId) + ":" + Objects.toString(numeroMuestra);
    }
}
