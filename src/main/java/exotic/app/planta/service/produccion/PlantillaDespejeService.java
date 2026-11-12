package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.despeje.*;
import exotic.app.planta.model.produccion.dto.DespejeLineaDTOs.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.PlantillaDespejeVersionRepo;
import exotic.app.planta.repo.produccion.PlantillaDespejeRepo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantillaDespejeService {
    private final PlantillaDespejeVersionRepo plantillaRepo;
    private final PlantillaDespejeRepo familiaRepo;
    private final AreaProduccionRepo areaRepo;
    private final EntityManager entityManager;
    private final Clock applicationClock;

    public Config configuracion(Integer areaId) { return toConfig(area(areaId)); }

    @Transactional
    public Config configurar(Integer areaId, ConfigRequest request) {
        AreaOperativa area = bloquearArea(areaId);
        if (request == null || request.requiereDespeje() == null || request.requiereRevision() == null)
            throw new IllegalArgumentException("Indique la exigencia de despeje y de revisión.");
        exigirVersion(request.version(), area.getDespejeVersion());
        boolean cambioRevision = area.isRequiereRevisionDespeje() != request.requiereRevision();
        boolean cambio = area.isRequiereDespeje() != request.requiereDespeje() || cambioRevision;
        if (!cambio) return toConfig(area);
        if (cambioRevision && (area.getDespejeOcupanteSeguimiento() != null
                || area.getDespejeOcupanteOperacion() != null))
            throw conflicto("Termine la ocupación actual antes de cambiar la política de revisión.");
        if (request.requiereDespeje() && !area.isRequiereDespeje()) {
            if (areaId == -1) throw new IllegalArgumentException("Almacén general no admite despeje de línea.");
            if (!Boolean.TRUE.equals(request.confirmaLineaExclusiva()))
                throw new IllegalArgumentException("Confirme que el área corresponde a una única línea física exclusiva.");
            area.setDespejeLineaExclusivaConfirmada(true);
        }
        // El requisito de inicio es inmediato; no modifica el contexto físico ni las evidencias.
        area.setRequiereDespeje(request.requiereDespeje());
        area.setRequiereRevisionDespeje(request.requiereRevision());
        if (cambioRevision) area.setDespejeRevisionContexto(area.getDespejeRevisionContexto() + 1);
        return toConfig(areaRepo.saveAndFlush(area));
    }

    public List<Plantilla> listar(Integer areaId) {
        area(areaId);
        return plantillaRepo.findByAreaOperativa_AreaIdOrderByNumeroDesc(areaId).stream()
                .map(this::toDTO).toList();
    }

    public List<Familia> listarFamilias(Integer areaId) {
        area(areaId);
        return familiaRepo.findByAreaOperativa_AreaIdOrderByIdAsc(areaId).stream()
                .map(this::toFamiliaDTO).toList();
    }

    @Transactional
    public Familia crearFamilia(Integer areaId, FamiliaRequest request) {
        AreaOperativa area = bloquearArea(areaId);
        if (areaId == -1) throw new IllegalArgumentException("Almacén general no admite plantillas de despeje.");
        if (request == null || request.nombre() == null || request.nombre().isBlank()
                || request.nombre().trim().length() > 200)
            throw new IllegalArgumentException("La familia requiere un nombre de hasta 200 caracteres.");
        PlantillaDespeje familia = new PlantillaDespeje();
        familia.setAreaOperativa(area);
        familia.setNombre(request.nombre().trim());
        return toFamiliaDTO(familiaRepo.saveAndFlush(familia));
    }

    @Transactional
    public Plantilla guardar(Integer areaId, PlantillaRequest request, User actor) {
        AreaOperativa area = bloquearArea(areaId);
        if (areaId == -1) throw new IllegalArgumentException("Almacén general no admite plantillas de despeje.");
        validarRequest(request);
        PlantillaDespeje familia = familiaRepo.findById(request.familiaId())
                .orElseThrow(() -> new NoSuchElementException("Familia de despeje no encontrada."));
        if (familia.getAreaOperativa().getAreaId() != areaId)
            throw new IllegalArgumentException("La familia no pertenece al área indicada.");
        PlantillaDespejeVersion plantilla = plantillaRepo
                .findByFamilia_IdAndEstado(familia.getId(), PlantillaDespejeVersion.Estado.BORRADOR).orElse(null);
        if (request.id() != null && (plantilla == null || !request.id().equals(plantilla.getId())))
            throw conflicto("La versión indicada no es el borrador de esta familia.");
        if (plantilla == null) {
            plantilla = new PlantillaDespejeVersion();
            plantilla.setAreaOperativa(area);
            plantilla.setFamilia(familia);
            plantilla.setNumero(plantillaRepo.maxNumero(familia.getId()) + 1);
            plantilla.setCreadaPor(actor);
            plantilla.setCreadaEn(LocalDateTime.now(applicationClock));
        } else {
            exigirVersion(request.version(), plantilla.getVersion());
            plantilla.getItems().clear();
            plantillaRepo.flush();
        }
        plantilla.setNombre(request.nombre().trim());
        plantilla.setMotivoCambio(request.motivoCambio().trim());
        for (ItemRequest item : request.items()) {
            ItemPlantillaDespeje nuevo = new ItemPlantillaDespeje();
            nuevo.setPlantilla(plantilla);
            nuevo.setOrden(item.orden());
            nuevo.setTexto(item.texto().trim());
            nuevo.setTipo(item.tipo());
            nuevo.setObligatorio(!Boolean.FALSE.equals(item.obligatorio()));
            nuevo.setPermiteNa(Boolean.TRUE.equals(item.permiteNa()));
            nuevo.setRespuestaConforme(item.tipo() == ItemPlantillaDespeje.Tipo.SI_NO_NA
                    ? item.respuestaConforme() : null);
            nuevo.setMinimo(item.minimo());
            nuevo.setMaximo(item.maximo());
            plantilla.getItems().add(nuevo);
        }
        return toDTO(plantillaRepo.saveAndFlush(plantilla));
    }

    @Transactional
    public Plantilla publicar(Long id) {
        PlantillaDespejeVersion plantilla = plantilla(id);
        bloquearArea(plantilla.getAreaOperativa().getAreaId());
        entityManager.refresh(plantilla);
        if (plantilla.getEstado() == PlantillaDespejeVersion.Estado.VIGENTE) return toDTO(plantilla);
        if (plantilla.getEstado() != PlantillaDespejeVersion.Estado.BORRADOR || plantilla.getItems().isEmpty())
            throw conflicto("Solo puede publicar un borrador con ítems.");
        LocalDateTime ahora = LocalDateTime.now(applicationClock);
        plantillaRepo.findByFamilia_IdAndEstado(plantilla.getFamilia().getId(),
                PlantillaDespejeVersion.Estado.VIGENTE).ifPresent(vigente -> {
                    vigente.setEstado(PlantillaDespejeVersion.Estado.RETIRADA);
                    vigente.setVigenteHasta(ahora);
                    plantillaRepo.saveAndFlush(vigente);
                });
        plantilla.setEstado(PlantillaDespejeVersion.Estado.VIGENTE);
        plantilla.setVigenteDesde(ahora);
        return toDTO(plantillaRepo.saveAndFlush(plantilla));
    }

    @Transactional
    public Plantilla retirar(Long id) {
        PlantillaDespejeVersion plantilla = plantilla(id);
        bloquearArea(plantilla.getAreaOperativa().getAreaId());
        entityManager.refresh(plantilla);
        if (plantilla.getEstado() == PlantillaDespejeVersion.Estado.BORRADOR)
            throw conflicto("El borrador aún no está publicado.");
        if (plantilla.getEstado() != PlantillaDespejeVersion.Estado.RETIRADA) {
            plantilla.setEstado(PlantillaDespejeVersion.Estado.RETIRADA);
            plantilla.setVigenteHasta(LocalDateTime.now(applicationClock));
        }
        return toDTO(plantillaRepo.saveAndFlush(plantilla));
    }

    public PlantillaDespejeVersion plantilla(Long id) {
        return plantillaRepo.findById(id).orElseThrow(() -> new NoSuchElementException("Plantilla de despeje no encontrada."));
    }

    public AreaOperativa bloquearArea(Integer id) {
        AreaOperativa area = areaRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Área operativa no encontrada."));
        entityManager.refresh(area, LockModeType.PESSIMISTIC_WRITE);
        return area;
    }
    private AreaOperativa area(Integer id) {
        return areaRepo.findById(id).orElseThrow(() -> new NoSuchElementException("Área operativa no encontrada."));
    }
    public Plantilla toDTO(PlantillaDespejeVersion p) {
        return new Plantilla(p.getId(), p.getAreaOperativa().getAreaId(), p.getNumero(), p.getNombre(),
                p.getEstado().name(), p.getMotivoCambio(), p.getVigenteDesde(), p.getVigenteHasta(), p.getVersion(),
                p.getItems().stream().sorted(Comparator.comparingInt(ItemPlantillaDespeje::getOrden))
                        .map(i -> new Item(i.getId(), i.getOrden(), i.getTexto(), i.getTipo(), i.isObligatorio(),
                                i.isPermiteNa(), i.getRespuestaConforme(), i.getMinimo(), i.getMaximo())).toList(),
                p.getFamilia().getId(), p.getFamilia().getNombre());
    }
    private Familia toFamiliaDTO(PlantillaDespeje p) {
        return new Familia(p.getId(), p.getAreaOperativa().getAreaId(), p.getNombre(), p.getVersion());
    }
    private Config toConfig(AreaOperativa a) {
        return new Config(a.getAreaId(), a.isRequiereDespeje(), a.isRequiereRevisionDespeje(),
                a.getDespejeRevisionContexto(), a.isDespejeAntecedenteConocido(),
                a.getDespejeUltimoLote() == null ? null : a.getDespejeUltimoLote().getId(),
                a.getDespejeUltimoLote() == null ? null : a.getDespejeUltimoLote().getBatchNumber(),
                a.getDespejeOcupanteSeguimiento() == null ? null : a.getDespejeOcupanteSeguimiento().getId(),
                a.getDespejeOcupanteOperacion() == null ? null : a.getDespejeOcupanteOperacion().getId(), a.getDespejeVersion());
    }
    private void validarRequest(PlantillaRequest r) {
        if (r == null || r.familiaId() == null || r.nombre() == null || r.nombre().isBlank() || r.nombre().trim().length() > 200
                || r.motivoCambio() == null || r.motivoCambio().isBlank() || r.motivoCambio().length() > 4000
                || r.items() == null || r.items().isEmpty() || r.items().size() > 100)
            throw new IllegalArgumentException("Indique familia, nombre, motivo y entre 1 y 100 ítems.");
        Set<Integer> ordenes = new HashSet<>();
        for (ItemRequest i : r.items()) {
            if (i == null || i.orden() == null || i.orden() <= 0 || !ordenes.add(i.orden())
                    || i.texto() == null || i.texto().isBlank() || i.texto().trim().length() > 1000 || i.tipo() == null)
                throw new IllegalArgumentException("Cada ítem requiere orden positivo único, texto y tipo.");
            if (i.tipo() == ItemPlantillaDespeje.Tipo.SI_NO_NA) {
                if (i.respuestaConforme() == null || i.respuestaConforme() == ItemPlantillaDespeje.Valor.NA
                        || i.minimo() != null || i.maximo() != null)
                    throw new IllegalArgumentException("La verificación requiere respuesta conforme SI o NO, sin límites numéricos.");
            } else if (Boolean.TRUE.equals(i.permiteNa()) || i.respuestaConforme() != null
                    || (i.minimo() != null && i.maximo() != null && i.minimo().compareTo(i.maximo()) > 0)) {
                throw new IllegalArgumentException("La pregunta numérica no admite SI/NO/NA y sus límites deben estar ordenados.");
            }
            validarNumero(i.minimo()); validarNumero(i.maximo());
        }
    }
    static void validarNumero(java.math.BigDecimal n) {
        if (n != null && (n.scale() > 8 || n.precision() - n.scale() > 16))
            throw new IllegalArgumentException("El número admite hasta 16 enteros y 8 decimales.");
    }
    static void exigirVersion(Long esperada, long actual) {
        if (esperada == null || esperada != actual)
            throw conflicto("El registro cambió. Actualice los datos antes de guardar.");
    }
    static ResponseStatusException conflicto(String mensaje) { return new ResponseStatusException(CONFLICT, mensaje); }
}
