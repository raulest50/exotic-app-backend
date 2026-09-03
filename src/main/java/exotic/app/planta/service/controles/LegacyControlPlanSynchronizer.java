package exotic.app.planta.service.controles;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.calidad.ControlProcesoCaracteristica;
import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import exotic.app.planta.model.calidad.TipoCaracteristicaControlProceso;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.controles.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Keeps a legacy area-based plan family aligned with the neutral control model.
 * It is intentionally limited to versions whose {@code legacyPlantilla} link is
 * present. Native versions remain exclusively governed by {@link ControlPlanService}.
 */
@Service
@RequiredArgsConstructor
public class LegacyControlPlanSynchronizer {

    private static final String PLAN_PREFIX = "LEGACY-PROCESO-AREA-";

    private final ControlProcesoPlantillaRepo legacyPlantillaRepo;
    private final PlanControlRepo planRepo;
    private final VersionPlanControlRepo versionRepo;
    private final ControlRequeridoRepo requeridoRepo;
    private final MagnitudControlRepo magnitudRepo;
    private final UnidadControlRepo unidadRepo;

    @Transactional(readOnly = true)
    public void requireLegacyOwnedFamily(Integer areaId) {
        planRepo.findByCodigoIgnoreCase(planCode(areaId)).ifPresent(plan -> {
            if (versionRepo.existsByPlan_IdAndLegacyPlantillaIsNull(plan.getId())) {
                throw new IllegalStateException(
                        "La familia ya inicio su transicion a versiones nativas; la escritura legada esta cerrada para esta area.");
            }
        });
    }

    /**
     * Detaches the neutral children first, so orphan removal in the V077 model
     * cannot violate the legacy-characteristic foreign key. A draft can never be
     * detached after it has materialized a requirement.
     */
    @Transactional
    public void prepareLegacyDraftReplacement(Integer areaId) {
        legacyPlantillaRepo.findFirstByAreaOperativa_AreaIdAndEstado(
                        areaId, EstadoControlProcesoPlantilla.BORRADOR)
                .flatMap(legacy -> versionRepo.findByLegacyPlantilla_Id(legacy.getId()))
                .ifPresent(version -> {
                    if (version.getEstado() != EstadoVersionPlanControl.BORRADOR) {
                        throw new IllegalStateException("La proyeccion neutral del borrador legado no esta en BORRADOR.");
                    }
                    if (requeridoRepo.existsByVersionPlan_Id(version.getId())) {
                        throw new IllegalStateException(
                                "El borrador legado ya tiene requisitos congelados y no puede reemplazarse.");
                    }
                    version.getAplicabilidades().clear();
                    version.getCaracteristicas().clear();
                    versionRepo.saveAndFlush(version);
                });
    }

    @Transactional
    public void synchronizeArea(Integer areaId, User actor) {
        synchronizeArea(areaId, actor, true);
    }

    @Transactional
    public void synchronizeRetirement(Integer areaId, User actor) {
        synchronizeArea(areaId, actor, false);
    }

    private void synchronizeArea(Integer areaId, User actor, boolean requireLegacyOwnership) {
        List<ControlProcesoPlantilla> legacyVersions = legacyPlantillaRepo.buscar(areaId, null);
        if (legacyVersions.isEmpty()) {
            return;
        }
        LocalDateTime transitionTime = AppTime.now();
        PlanControl plan = planRepo.findByCodigoIgnoreCase(planCode(areaId))
                .orElseGet(() -> createPlan(legacyVersions.getFirst(), actor, transitionTime));
        if (requireLegacyOwnership && versionRepo.existsByPlan_IdAndLegacyPlantillaIsNull(plan.getId())) {
            throw new IllegalStateException(
                    "No se puede sincronizar una escritura legada despues de crear una version nativa en la familia.");
        }

        // RETIRADA is synchronized before VIGENTE, which releases the partial
        // unique index before a newly published version is promoted.
        legacyVersions.stream()
                .sorted(Comparator.comparingInt(this::statePriority)
                        .thenComparing(ControlProcesoPlantilla::getVersion))
                .forEach(legacy -> synchronizeVersion(plan, legacy, actor, transitionTime));
    }

    private PlanControl createPlan(
            ControlProcesoPlantilla legacy, User actor, LocalDateTime transitionTime) {
        PlanControl plan = new PlanControl();
        plan.setCodigo(planCode(legacy.getAreaOperativa().getAreaId()));
        plan.setNombre("Control de proceso legado - " + legacy.getAreaOperativa().getNombre());
        plan.setAmbito(AmbitoControl.PROCESO);
        plan.setCreadoEn(transitionTime);
        plan.setCreadoPor(actor);
        return planRepo.saveAndFlush(plan);
    }

    private void synchronizeVersion(
            PlanControl plan,
            ControlProcesoPlantilla legacy,
            User actor,
            LocalDateTime transitionTime) {
        VersionPlanControl version = versionRepo.findByLegacyPlantilla_Id(legacy.getId()).orElse(null);
        boolean isNew = version == null;
        if (isNew) {
            version = new VersionPlanControl();
            version.setPlan(plan);
            version.setNumero(legacy.getVersion());
            version.setCreadaEn(transitionTime);
            version.setCreadaPor(actor);
            version.setLegacyPlantilla(legacy);
            version.setProposito("CONTROL_PROCESO_LEGADO");
            version.setMotivoCambio("Sincronizacion desde plantilla historica " + legacy.getId());
            version.setResponsableEjecucion("DIRECCION_TECNICA_Y_PLANTA");
            version.setResponsableRevision("DIRECCION_TECNICA_Y_PLANTA");
            version.setResponsableDisposicion("DIRECCION_TECNICA_Y_PLANTA");
            plan.getVersiones().add(version);
        }

        EstadoVersionPlanControl target = EstadoVersionPlanControl.valueOf(legacy.getEstado().name());
        if (target == EstadoVersionPlanControl.VIGENTE && version.getPublicadaEn() == null) {
            version.setPublicadaEn(transitionTime);
            version.setPublicadaPor(actor);
        }
        if (target == EstadoVersionPlanControl.RETIRADA) {
            if (version.getPublicadaEn() == null) {
                version.setPublicadaEn(transitionTime);
                version.setPublicadaPor(actor);
            }
            if (version.getRetiradaEn() == null) {
                version.setRetiradaEn(transitionTime);
                version.setRetiradaPor(actor);
            }
        }
        version.setEstado(target);

        if (isNew || target == EstadoVersionPlanControl.BORRADOR) {
            rebuildContent(version, legacy);
        }
        versionRepo.saveAndFlush(version);
    }

    private void rebuildContent(VersionPlanControl version, ControlProcesoPlantilla legacy) {
        version.getAplicabilidades().clear();
        version.getCaracteristicas().clear();

        AplicabilidadPlanControl applicability = new AplicabilidadPlanControl();
        applicability.setVersion(version);
        applicability.setTipoOrden(TipoOrdenControl.AMBAS);
        applicability.setPuntoAplicacion(PuntoAplicacionControl.SALIDA_OPERACION);
        applicability.setAreaOperativa(legacy.getAreaOperativa());
        applicability.setMomento(MomentoControl.DURANTE_FABRICACION);
        applicability.setPuntoExigencia(PuntoExigenciaControl.INFORMATIVO);
        applicability.setLegadoGlobal(true);
        version.getAplicabilidades().add(applicability);

        for (ControlProcesoCaracteristica legacyCharacteristic : legacy.getCaracteristicas()) {
            version.getCaracteristicas().add(toNeutralCharacteristic(version, legacyCharacteristic));
        }
    }

    private CaracteristicaPlanControl toNeutralCharacteristic(
            VersionPlanControl version, ControlProcesoCaracteristica legacy) {
        CatalogMagnitude magnitude = magnitudeFor(legacy);
        MagnitudControl magnitudeEntity = magnitudRepo.findByCodigoIgnoreCase(magnitude.code())
                .orElseGet(() -> magnitudRepo.saveAndFlush(newMagnitude(magnitude)));
        CatalogUnit unit = legacy.getTipo() == TipoCaracteristicaControlProceso.NUMERICA
                ? unitFor(legacy) : null;
        UnidadControl unitEntity = unit == null ? null : unidadRepo.findByCodigoIgnoreCase(unit.code())
                .orElseGet(() -> unidadRepo.saveAndFlush(newUnit(unit)));

        BigDecimal lower = decimal(legacy.getLimiteInferior(), "limite inferior", legacy.getId());
        BigDecimal upper = decimal(legacy.getLimiteSuperior(), "limite superior", legacy.getId());
        boolean numeric = legacy.getTipo() == TipoCaracteristicaControlProceso.NUMERICA;
        boolean noLimits = numeric && lower == null && upper == null;
        boolean needsCleanup = !magnitude.known()
                || (numeric && (unit == null || !unit.known()
                || !magnitude.dimension().equalsIgnoreCase(unit.dimension())))
                || noLimits;

        CaracteristicaPlanControl result = new CaracteristicaPlanControl();
        result.setVersion(version);
        result.setMagnitud(magnitudeEntity);
        result.setUnidad(unitEntity);
        result.setNombre(legacy.getNombre() == null || legacy.getNombre().isBlank()
                ? magnitudeEntity.getNombre() : legacy.getNombre().trim());
        result.setTipo(TipoCaracteristicaControl.valueOf(legacy.getTipo().name()));
        result.setOrden(legacy.getOrden());
        result.setCantidadMuestras(legacy.getCantidadMuestras());
        result.setUnidadesPorMuestra(legacy.getUnidadesPorMuestra());
        result.setEscalaVisible(8);
        result.setLimiteInferior(lower);
        result.setLimiteSuperior(upper);
        result.setValorBooleanoEsperado(numeric ? null : Boolean.TRUE);
        result.setMagnitudCodigoSnapshot(magnitudeEntity.getCodigo());
        result.setMagnitudNombreSnapshot(magnitudeEntity.getNombre());
        result.setMagnitudSimboloSnapshot(magnitudeEntity.getSimbolo());
        result.setUnidadCodigoSnapshot(unitEntity == null ? null : unitEntity.getCodigo());
        result.setUnidadNombreSnapshot(unitEntity == null ? null : unitEntity.getNombre());
        result.setUnidadSimboloSnapshot(unitEntity == null ? null : unitEntity.getSimbolo());
        result.setLegadoSinLimites(noLimits);
        result.setRequiereDepuracion(needsCleanup);
        result.setLegacyCaracteristica(legacy);
        return result;
    }

    private MagnitudControl newMagnitude(CatalogMagnitude source) {
        MagnitudControl item = new MagnitudControl();
        item.setCodigo(source.code());
        item.setNombre(source.name());
        item.setSimbolo(source.symbol());
        item.setDimension(source.dimension());
        item.setActivo(source.known());
        item.setCreadoEn(AppTime.now());
        return item;
    }

    private UnidadControl newUnit(CatalogUnit source) {
        UnidadControl item = new UnidadControl();
        item.setCodigo(source.code());
        item.setNombre(source.name());
        item.setSimbolo(source.symbol());
        item.setDimension(source.dimension());
        item.setActivo(source.known());
        item.setCreadoEn(AppTime.now());
        return item;
    }

    private CatalogMagnitude magnitudeFor(ControlProcesoCaracteristica item) {
        String raw = item.getNombre() == null ? "" : item.getNombre().trim();
        if (raw.isEmpty()) {
            return new CatalogMagnitude("LEGACY_ID_" + item.getId(),
                    "[LEGACY SIN NOMBRE #" + item.getId() + "]", "?", "LEGACY", false);
        }
        String normalized = normalizeLabel(raw);
        return switch (normalized) {
            case "peso", "masa" -> new CatalogMagnitude("PESO", "Peso", "m", "MASA", true);
            case "ph", "p.h." -> new CatalogMagnitude("PH", "pH", "pH", "PH", true);
            case "viscosidad", "viscosidad dinamica" -> new CatalogMagnitude(
                    "VISCOSIDAD_DINAMICA", "Viscosidad dinamica", "η",
                    "VISCOSIDAD_DINAMICA", true);
            default -> new CatalogMagnitude("LEGACY_" + md5Prefix(raw), raw, "?", "LEGACY", false);
        };
    }

    private CatalogUnit unitFor(ControlProcesoCaracteristica characteristic) {
        String value = characteristic.getUnidad();
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) {
            return new CatalogUnit("LEGACY_SIN_UNIDAD_" + characteristic.getId(),
                    "[LEGACY SIN UNIDAD #" + characteristic.getId() + "]", "?", "LEGACY", false);
        }
        return switch (normalizeLabel(raw)) {
            case "g" -> new CatalogUnit("G", "Gramo", "g", "MASA", true);
            case "kg" -> new CatalogUnit("KG", "Kilogramo", "kg", "MASA", true);
            case "ph" -> new CatalogUnit("PH", "Unidad de pH", "pH", "PH", true);
            case "cp" -> new CatalogUnit("CP", "Centipoise", "cP", "VISCOSIDAD_DINAMICA", true);
            case "mpa·s", "mpa*s", "mpa s" -> new CatalogUnit(
                    "MPA_S", "Milipascal segundo", "mPa·s", "VISCOSIDAD_DINAMICA", true);
            case "adimensional", "1" -> new CatalogUnit(
                    "ADIMENSIONAL", "Adimensional", "1", "ADIMENSIONAL", true);
            default -> new CatalogUnit("LEGACY_" + md5Prefix(raw), raw, raw, "LEGACY", false);
        };
    }

    private BigDecimal decimal(Double value, String field, Long characteristicId) {
        if (value == null) return null;
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("La caracteristica legada " + characteristicId
                    + " contiene un " + field + " no finito.");
        }
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        if (decimal.scale() < 0) decimal = decimal.setScale(0);
        if (decimal.scale() > 8 || Math.max(0, decimal.precision() - decimal.scale()) > 12) {
            throw new IllegalArgumentException("La caracteristica legada " + characteristicId
                    + " contiene un " + field + " fuera de NUMERIC(20,8).");
        }
        return decimal;
    }

    private int statePriority(ControlProcesoPlantilla item) {
        return switch (item.getEstado()) {
            case RETIRADA -> 0;
            case BORRADOR -> 1;
            case VIGENTE -> 2;
        };
    }

    private String planCode(Integer areaId) {
        if (areaId == null) throw new IllegalArgumentException("El area operativa es obligatoria.");
        return PLAN_PREFIX + areaId;
    }

    private String normalizeLabel(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private String md5Prefix(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(value.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("MD5 no esta disponible en el runtime.", impossible);
        }
    }

    private record CatalogMagnitude(
            String code, String name, String symbol, String dimension, boolean known) {}
    private record CatalogUnit(String code, String name, String symbol, String dimension, boolean known) {}
}
