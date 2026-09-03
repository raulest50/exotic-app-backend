package exotic.app.planta.service.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ControlPlanService {
    private final PlanControlRepo planRepo;
    private final VersionPlanControlRepo versionRepo;
    private final MagnitudControlRepo magnitudRepo;
    private final UnidadControlRepo unidadRepo;
    private final ProductoRepo productoRepo;
    private final CategoriaRepo categoriaRepo;
    private final AreaProduccionRepo areaRepo;
    private final ProcesoProduccionRepo procesoRepo;

    @Transactional(readOnly = true)
    public List<PlanResponse> listar(AmbitoControl ambito) {
        return listar(ambito, null);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listar(AmbitoControl ambito, String search) {
        String filtro = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return planRepo.findByAmbitoOrderByCodigoAsc(ambito).stream()
                .filter(p -> filtro.isEmpty()
                        || p.getCodigo().toLowerCase(Locale.ROOT).contains(filtro)
                        || p.getNombre().toLowerCase(Locale.ROOT).contains(filtro))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse detalle(AmbitoControl ambito, Long planId) {
        return toResponse(requirePlan(ambito, planId));
    }

    @Transactional
    public PlanResponse crear(AmbitoControl ambito, User actor, PlanWriteRequest request) {
        validarRequest(ambito, request);
        String codigo = normalizarCodigo(request.codigo());
        if (planRepo.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Ya existe un plan con ese codigo.");
        }
        PlanControl plan = new PlanControl();
        plan.setCodigo(codigo);
        plan.setNombre(limpiar(request.nombre()));
        plan.setAmbito(ambito);
        plan.setCreadoEn(AppTime.now());
        plan.setCreadoPor(actor);
        planRepo.saveAndFlush(plan);

        VersionPlanControl version = nuevaVersion(plan, actor, 1);
        reemplazarContenido(version, ambito, request);
        versionRepo.saveAndFlush(version);
        return toResponse(planRepo.findByIdAndAmbito(plan.getId(), ambito).orElseThrow());
    }

    @Transactional
    public PlanResponse guardarBorrador(AmbitoControl ambito, User actor, Long planId, PlanWriteRequest request) {
        validarRequest(ambito, request);
        PlanControl plan = requirePlanForUpdate(ambito, planId);
        if (!plan.getCodigo().equalsIgnoreCase(normalizarCodigo(request.codigo()))) {
            throw new IllegalArgumentException("El codigo y el ambito de un plan son inmutables.");
        }
        if (!plan.getNombre().equals(limpiar(request.nombre()))) {
            throw new IllegalArgumentException("El nombre de un plan es inmutable; cree otro plan si cambia su identidad.");
        }
        VersionPlanControl version = versionRepo
                .findFirstByPlan_IdAndEstado(planId, EstadoVersionPlanControl.BORRADOR)
                .orElseGet(() -> nuevaVersion(plan, actor, versionRepo.maxNumero(planId) + 1));
        if (version.getLegacyPlantilla() != null) {
            throw new IllegalStateException(
                    "Una version vinculada al modelo legado solo puede modificarse mediante el adaptador temporal.");
        }
        reemplazarContenido(version, ambito, request);
        versionRepo.saveAndFlush(version);
        return toResponse(plan);
    }

    @Transactional
    public PlanResponse publicar(AmbitoControl ambito, User actor, Long planId, Long versionId) {
        PlanControl plan = requirePlanForUpdate(ambito, planId);
        VersionPlanControl version = requireVersion(ambito, planId, versionId);
        if (version.getEstado() == EstadoVersionPlanControl.VIGENTE) {
            return toResponse(plan);
        }
        if (version.getEstado() != EstadoVersionPlanControl.BORRADOR) {
            throw new IllegalStateException("Solo puede publicarse una version en borrador.");
        }
        if (version.getLegacyPlantilla() != null) {
            throw new IllegalStateException(
                    "Una version legada debe publicarse mediante el adaptador temporal.");
        }
        versionRepo.findFirstByPlan_IdAndEstado(planId, EstadoVersionPlanControl.VIGENTE)
                .filter(vigente -> vigente.getLegacyPlantilla() != null)
                .ifPresent(vigente -> {
                    throw new IllegalStateException(
                            "Retire primero la version legada mediante su adaptador antes de publicar la version nativa.");
                });
        validarVersionPublicable(ambito, version);
        var instanteVigencia = AppTime.now();
        versionRepo.findFirstByPlan_IdAndEstado(planId, EstadoVersionPlanControl.VIGENTE)
                .ifPresent(vigente -> {
                    vigente.setEstado(EstadoVersionPlanControl.RETIRADA);
                    vigente.setRetiradaEn(instanteVigencia);
                    vigente.setRetiradaPor(actor);
                    versionRepo.save(vigente);
                });
        version.setEstado(EstadoVersionPlanControl.VIGENTE);
        version.setPublicadaEn(instanteVigencia);
        version.setPublicadaPor(actor);
        versionRepo.saveAndFlush(version);
        return toResponse(plan);
    }

    @Transactional
    public PlanResponse retirar(AmbitoControl ambito, User actor, Long planId, Long versionId) {
        PlanControl plan = requirePlanForUpdate(ambito, planId);
        VersionPlanControl version = requireVersion(ambito, planId, versionId);
        if (version.getEstado() == EstadoVersionPlanControl.RETIRADA) {
            return toResponse(plan);
        }
        if (version.getEstado() != EstadoVersionPlanControl.VIGENTE) {
            throw new IllegalStateException(
                    "Solo puede retirarse una version vigente; un borrador no es una version publicada.");
        }
        if (version.getLegacyPlantilla() != null) {
            throw new IllegalStateException(
                    "Una version legada debe retirarse mediante el adaptador temporal.");
        }
        version.setEstado(EstadoVersionPlanControl.RETIRADA);
        version.setRetiradaEn(AppTime.now());
        version.setRetiradaPor(actor);
        versionRepo.saveAndFlush(version);
        return toResponse(plan);
    }

    private VersionPlanControl nuevaVersion(PlanControl plan, User actor, int numero) {
        VersionPlanControl version = new VersionPlanControl();
        version.setPlan(plan);
        version.setNumero(numero);
        version.setEstado(EstadoVersionPlanControl.BORRADOR);
        version.setCreadaEn(AppTime.now());
        version.setCreadaPor(actor);
        plan.getVersiones().add(version);
        return version;
    }

    private void reemplazarContenido(VersionPlanControl version, AmbitoControl ambito, PlanWriteRequest request) {
        if (version.getEstado() != EstadoVersionPlanControl.BORRADOR) {
            throw new IllegalStateException("Una version publicada o retirada es inmutable.");
        }
        version.setProposito(limpiar(request.proposito()));
        String motivoCambio = limpiarNullable(request.motivoCambio());
        if (version.getNumero() > 1 && motivoCambio == null) {
            throw new IllegalArgumentException(
                    "El motivo del cambio es obligatorio a partir de la segunda version.");
        }
        version.setMotivoCambio(motivoCambio);
        aplicarResponsables(version, ambito);
        version.getAplicabilidades().clear();
        for (AplicabilidadWriteRequest item : request.aplicabilidades()) {
            version.getAplicabilidades().add(crearAplicabilidad(version, ambito, item));
        }
        version.getCaracteristicas().clear();
        Set<Integer> ordenes = new HashSet<>();
        for (CaracteristicaWriteRequest item : request.caracteristicas()) {
            if (!ordenes.add(item.orden())) {
                throw new IllegalArgumentException("El orden de las caracteristicas no puede repetirse.");
            }
            version.getCaracteristicas().add(crearCaracteristica(version, item));
        }
    }

    private AplicabilidadPlanControl crearAplicabilidad(
            VersionPlanControl version, AmbitoControl ambito, AplicabilidadWriteRequest request) {
        boolean tieneProducto = request.productoId() != null && !request.productoId().isBlank();
        boolean tieneCategoria = request.categoriaId() != null;
        if (tieneProducto == tieneCategoria) {
            throw new IllegalArgumentException("La aplicabilidad debe seleccionar un producto o una categoria, no ambos.");
        }
        if (ambito == AmbitoControl.PROCESO && request.momento() != MomentoControl.DURANTE_FABRICACION) {
            throw new IllegalArgumentException("Los controles de proceso se ejecutan durante la fabricacion.");
        }
        if (request.puntoExigencia() == PuntoExigenciaControl.CIERRE_ETAPA
                && (request.puntoAplicacion() != PuntoAplicacionControl.SALIDA_OPERACION
                || request.momento() != MomentoControl.DURANTE_FABRICACION)) {
            throw new IllegalArgumentException("CIERRE_ETAPA requiere salida de operacion durante fabricacion.");
        }
        if (request.momento() == MomentoControl.REVISION_FINAL
                && (request.puntoExigencia() == PuntoExigenciaControl.CIERRE_ETAPA
                || request.puntoExigencia() == PuntoExigenciaControl.ENVIO_CALIDAD)) {
            throw new IllegalArgumentException("Un ensayo de revision final no puede bloquear la etapa ni su envio.");
        }
        if (request.puntoAplicacion() == PuntoAplicacionControl.SALIDA_OPERACION
                && (request.areaOperativaId() == null || request.procesoId() == null)) {
            throw new IllegalArgumentException("Una salida de operacion requiere area y proceso maestro.");
        }
        if (request.puntoAplicacion() == PuntoAplicacionControl.LOTE_FINAL
                && (request.areaOperativaId() != null || request.procesoId() != null)) {
            throw new IllegalArgumentException("Un control de lote final no referencia area ni operacion.");
        }
        AplicabilidadPlanControl entity = new AplicabilidadPlanControl();
        entity.setVersion(version);
        if (tieneProducto) {
            Producto producto = productoRepo.findById(request.productoId().trim())
                    .orElseThrow(() -> new NoSuchElementException("Producto no encontrado."));
            if (!(producto instanceof Terminado) && !(producto instanceof SemiTerminado)) {
                throw new IllegalArgumentException(
                        "Los controles de esta entrega solo aplican a productos terminados o salidas intermedias.");
            }
            entity.setProducto(producto);
        } else {
            entity.setCategoria(categoriaRepo.findById(request.categoriaId())
                    .orElseThrow(() -> new NoSuchElementException("Categoria no encontrada.")));
        }
        entity.setTipoOrden(request.tipoOrden());
        entity.setPuntoAplicacion(request.puntoAplicacion());
        if (request.areaOperativaId() != null) {
            entity.setAreaOperativa(areaRepo.findById(request.areaOperativaId())
                    .orElseThrow(() -> new NoSuchElementException("Area operativa no encontrada.")));
        }
        if (request.procesoId() != null) {
            entity.setProceso(procesoRepo.findById(request.procesoId())
                    .orElseThrow(() -> new NoSuchElementException("Proceso no encontrado.")));
        }
        entity.setMomento(request.momento());
        entity.setPuntoExigencia(request.puntoExigencia());
        entity.setLegadoGlobal(false);
        List<String> exclusiones = request.productosExcluidosIds() == null
                ? List.of() : request.productosExcluidosIds();
        if (!exclusiones.isEmpty() && !tieneCategoria) {
            throw new IllegalArgumentException("Las exclusiones solo aplican a reglas por categoria.");
        }
        for (String id : new LinkedHashSet<>(exclusiones)) {
            entity.getProductosExcluidos().add(productoRepo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Producto excluido no encontrado: " + id)));
        }
        return entity;
    }

    private CaracteristicaPlanControl crearCaracteristica(
            VersionPlanControl version, CaracteristicaWriteRequest request) {
        MagnitudControl magnitud = magnitudRepo.findById(request.magnitudId())
                .orElseThrow(() -> new NoSuchElementException("Magnitud no encontrada."));
        if (!magnitud.isActivo()) {
            throw new IllegalArgumentException("La magnitud seleccionada esta inactiva.");
        }
        CaracteristicaPlanControl entity = new CaracteristicaPlanControl();
        entity.setVersion(version);
        entity.setMagnitud(magnitud);
        entity.setNombre(limpiar(request.nombre()));
        entity.setTipo(request.tipo());
        entity.setOrden(request.orden());
        entity.setCantidadMuestras(request.cantidadMuestras());
        entity.setUnidadesPorMuestra(request.unidadesPorMuestra());
        entity.setEscalaVisible(request.escalaVisible());
        entity.setMagnitudCodigoSnapshot(magnitud.getCodigo());
        entity.setMagnitudNombreSnapshot(magnitud.getNombre());
        entity.setMagnitudSimboloSnapshot(magnitud.getSimbolo());
        entity.setLegadoSinLimites(false);
        entity.setRequiereDepuracion(false);

        if (request.tipo() == TipoCaracteristicaControl.NUMERICA) {
            if (request.unidadId() == null) {
                throw new IllegalArgumentException("Una caracteristica numerica requiere unidad.");
            }
            UnidadControl unidad = unidadRepo.findById(request.unidadId())
                    .orElseThrow(() -> new NoSuchElementException("Unidad no encontrada."));
            if (!unidad.isActivo()) {
                throw new IllegalArgumentException("La unidad seleccionada esta inactiva.");
            }
            if (!magnitud.getDimension().equalsIgnoreCase(unidad.getDimension())) {
                throw new IllegalArgumentException("La unidad no pertenece a la dimension de la magnitud.");
            }
            validarLimites(request.objetivo(), request.limiteInferior(), request.limiteSuperior());
            entity.setUnidad(unidad);
            entity.setObjetivo(request.objetivo());
            entity.setLimiteInferior(request.limiteInferior());
            entity.setLimiteSuperior(request.limiteSuperior());
            entity.setUnidadCodigoSnapshot(unidad.getCodigo());
            entity.setUnidadNombreSnapshot(unidad.getNombre());
            entity.setUnidadSimboloSnapshot(unidad.getSimbolo());
        } else {
            if (request.unidadId() != null || request.objetivo() != null
                    || request.limiteInferior() != null || request.limiteSuperior() != null
                    || request.valorBooleanoEsperado() == null) {
                throw new IllegalArgumentException("Una caracteristica booleana solo define su valor esperado.");
            }
            entity.setValorBooleanoEsperado(request.valorBooleanoEsperado());
        }
        return entity;
    }

    private void validarLimites(BigDecimal objetivo, BigDecimal inferior, BigDecimal superior) {
        validarDecimal(objetivo, "objetivo");
        validarDecimal(inferior, "limiteInferior");
        validarDecimal(superior, "limiteSuperior");
        if (inferior == null && superior == null) {
            throw new IllegalArgumentException("Una caracteristica numerica requiere al menos un limite.");
        }
        if (inferior != null && superior != null && inferior.compareTo(superior) > 0) {
            throw new IllegalArgumentException("El limite inferior no puede superar el superior.");
        }
        if (objetivo != null && ((inferior != null && objetivo.compareTo(inferior) < 0)
                || (superior != null && objetivo.compareTo(superior) > 0))) {
            throw new IllegalArgumentException("El objetivo debe estar dentro de los limites.");
        }
    }

    private void validarDecimal(BigDecimal value, String campo) {
        if (value == null) return;
        int enteros = Math.max(0, value.precision() - value.scale());
        if (value.scale() < 0 || value.scale() > 8 || enteros > 12) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " excede NUMERIC(20,8).");
        }
    }

    private void validarRequest(AmbitoControl ambito, PlanWriteRequest request) {
        Objects.requireNonNull(ambito, "El ambito de la fachada es obligatorio.");
        if (request == null || request.aplicabilidades() == null || request.aplicabilidades().isEmpty()
                || request.caracteristicas() == null || request.caracteristicas().isEmpty()) {
            throw new IllegalArgumentException("El plan requiere aplicabilidad y caracteristicas.");
        }
    }

    private void validarVersionPublicable(AmbitoControl ambito, VersionPlanControl version) {
        if (version.getAplicabilidades().isEmpty() || version.getCaracteristicas().isEmpty()) {
            throw new IllegalStateException("La version no tiene una configuracion completa.");
        }
        if (version.getCaracteristicas().stream().anyMatch(CaracteristicaPlanControl::isRequiereDepuracion)) {
            throw new IllegalStateException("La version contiene datos historicos pendientes de depuracion.");
        }
        version.getAplicabilidades().forEach(a -> {
            if (a.isLegadoGlobal() && version.getLegacyPlantilla() == null) {
                throw new IllegalStateException("Una nueva version no puede tener aplicabilidad global legada.");
            }
            if (ambito == AmbitoControl.PROCESO && a.getMomento() != MomentoControl.DURANTE_FABRICACION) {
                throw new IllegalStateException("Momento incompatible con el ambito de proceso.");
            }
        });
        validarSolapamientos(version);
    }

    private void validarSolapamientos(VersionPlanControl version) {
        List<AplicabilidadPlanControl> reglas = version.getAplicabilidades();
        for (int i = 0; i < reglas.size(); i++) {
            for (int j = i + 1; j < reglas.size(); j++) {
                AplicabilidadPlanControl a = reglas.get(i);
                AplicabilidadPlanControl b = reglas.get(j);
                if (seSuperponen(a, b)
                        && (a.getMomento() != b.getMomento()
                        || a.getPuntoExigencia() != b.getPuntoExigencia())) {
                    throw new IllegalStateException(
                            "Dos reglas superpuestas del mismo plan y punto tienen politicas incompatibles.");
                }
            }
        }
    }

    private boolean seSuperponen(AplicabilidadPlanControl a, AplicabilidadPlanControl b) {
        if (a.getPuntoAplicacion() != b.getPuntoAplicacion()) return false;
        if (a.getPuntoAplicacion() == PuntoAplicacionControl.SALIDA_OPERACION
                && (!Objects.equals(a.getAreaOperativa().getAreaId(), b.getAreaOperativa().getAreaId())
                || !Objects.equals(a.getProceso().getProcesoId(), b.getProceso().getProcesoId()))) {
            return false;
        }
        if (a.getTipoOrden() != TipoOrdenControl.AMBAS
                && b.getTipoOrden() != TipoOrdenControl.AMBAS
                && a.getTipoOrden() != b.getTipoOrden()) return false;
        if (a.isLegadoGlobal() || b.isLegadoGlobal()) return true;
        if (a.getProducto() != null && b.getProducto() != null) {
            return Objects.equals(a.getProducto().getProductoId(), b.getProducto().getProductoId());
        }
        if (a.getCategoria() != null && b.getCategoria() != null) {
            return Objects.equals(a.getCategoria().getCategoriaId(), b.getCategoria().getCategoriaId());
        }
        AplicabilidadPlanControl porProducto = a.getProducto() == null ? b : a;
        AplicabilidadPlanControl porCategoria = a.getCategoria() == null ? b : a;
        Producto producto = porProducto.getProducto();
        if (!(producto instanceof Terminado terminado) || terminado.getCategoria() == null
                || !Objects.equals(terminado.getCategoria().getCategoriaId(),
                porCategoria.getCategoria().getCategoriaId())) return false;
        return porCategoria.getProductosExcluidos().stream()
                .noneMatch(p -> Objects.equals(p.getProductoId(), producto.getProductoId()));
    }

    private void aplicarResponsables(VersionPlanControl version, AmbitoControl ambito) {
        if (ambito == AmbitoControl.PROCESO) {
            version.setResponsableEjecucion("DIRECCION_TECNICA_Y_PLANTA");
            version.setResponsableRevision("DIRECCION_TECNICA_Y_PLANTA");
            version.setResponsableDisposicion("DIRECCION_TECNICA_Y_PLANTA");
        } else {
            version.setResponsableEjecucion("CALIDAD");
            version.setResponsableRevision("CALIDAD");
            version.setResponsableDisposicion("CALIDAD");
        }
    }

    private PlanControl requirePlan(AmbitoControl ambito, Long id) {
        return planRepo.findByIdAndAmbito(id, ambito)
                .orElseThrow(() -> new NoSuchElementException("Plan no encontrado en este ambito."));
    }

    private PlanControl requirePlanForUpdate(AmbitoControl ambito, Long id) {
        return planRepo.findByIdAndAmbitoForUpdate(id, ambito)
                .orElseThrow(() -> new NoSuchElementException("Plan no encontrado en este ambito."));
    }

    private VersionPlanControl requireVersion(AmbitoControl ambito, Long planId, Long versionId) {
        VersionPlanControl version = versionRepo.findByIdAndPlan_Ambito(versionId, ambito)
                .orElseThrow(() -> new NoSuchElementException("Version no encontrada en este ambito."));
        if (!version.getPlan().getId().equals(planId)) {
            throw new NoSuchElementException("La version no pertenece al plan indicado.");
        }
        return version;
    }

    private PlanResponse toResponse(PlanControl plan) {
        return new PlanResponse(plan.getId(), plan.getCodigo(), plan.getNombre(), plan.getAmbito(),
                plan.getCreadoEn(), plan.getVersiones().stream().map(this::toResponse).toList());
    }

    private VersionResponse toResponse(VersionPlanControl version) {
        return new VersionResponse(version.getId(), version.getNumero(), version.getEstado(),
                version.getProposito(), version.getMotivoCambio(), version.getResponsableEjecucion(),
                version.getResponsableRevision(), version.getResponsableDisposicion(), version.getCreadaEn(),
                version.getPublicadaEn(), version.getRetiradaEn(),
                version.getAplicabilidades().stream().map(this::toResponse).toList(),
                version.getCaracteristicas().stream().map(this::toResponse).toList());
    }

    public AplicabilidadResponse toResponse(AplicabilidadPlanControl item) {
        return new AplicabilidadResponse(item.getId(),
                item.getProducto() == null ? null : item.getProducto().getProductoId(),
                item.getCategoria() == null ? null : item.getCategoria().getCategoriaId(),
                item.getTipoOrden(), item.getPuntoAplicacion(),
                item.getAreaOperativa() == null ? null : item.getAreaOperativa().getAreaId(),
                item.getAreaOperativa() == null ? null : item.getAreaOperativa().getNombre(),
                item.getProceso() == null ? null : item.getProceso().getProcesoId(),
                item.getProceso() == null ? null : item.getProceso().getNombre(),
                item.getMomento(), item.getPuntoExigencia(),
                item.getProductosExcluidos().stream().map(Producto::getProductoId).sorted().toList(),
                item.isLegadoGlobal());
    }

    public CaracteristicaResponse toResponse(CaracteristicaPlanControl item) {
        return new CaracteristicaResponse(item.getId(), item.getNombre(), item.getTipo(), item.getOrden(),
                item.getCantidadMuestras(), item.getUnidadesPorMuestra(), item.getEscalaVisible(),
                item.getObjetivo(), item.getLimiteInferior(), item.getLimiteSuperior(),
                item.getValorBooleanoEsperado(), catalogoMagnitudSnapshot(item),
                catalogoUnidadSnapshot(item),
                item.isRequiereDepuracion());
    }

    private CatalogoResponse catalogoMagnitudSnapshot(CaracteristicaPlanControl item) {
        MagnitudControl catalogo = item.getMagnitud();
        return new CatalogoResponse(catalogo.getId(), item.getMagnitudCodigoSnapshot(),
                item.getMagnitudNombreSnapshot(), catalogo.getDimension(),
                item.getMagnitudSimboloSnapshot(), catalogo.isActivo(), true);
    }

    private CatalogoResponse catalogoUnidadSnapshot(CaracteristicaPlanControl item) {
        UnidadControl catalogo = item.getUnidad();
        return catalogo == null ? null : new CatalogoResponse(catalogo.getId(),
                item.getUnidadCodigoSnapshot(), item.getUnidadNombreSnapshot(),
                catalogo.getDimension(), item.getUnidadSimboloSnapshot(), catalogo.isActivo(), true);
    }

    private String normalizarCodigo(String value) {
        String codigo = limpiar(value).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        if (codigo.isBlank() || codigo.length() > 60) {
            throw new IllegalArgumentException("El codigo del plan no es valido.");
        }
        return codigo;
    }

    private String limpiar(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Hay un campo obligatorio vacio.");
        }
        return value.trim();
    }

    private String limpiarNullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
