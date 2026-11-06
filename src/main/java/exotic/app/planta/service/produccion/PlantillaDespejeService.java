package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.despeje.*;
import exotic.app.planta.model.produccion.dto.DespejeLineaDTOs.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.PlantillaDespejeVersionRepo;
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
        boolean cambio = area.isRequiereDespeje() != request.requiereDespeje()
                || area.isRequiereRevisionDespeje() != request.requiereRevision();
        if (!cambio) return toConfig(area);
        if (area.getDespejeOcupanteSeguimiento() != null || area.getDespejeOcupanteOperacion() != null)
            throw conflicto("Termine la ocupación actual antes de cambiar la política de despeje.");
        if (request.requiereDespeje() && !area.isRequiereDespeje()) {
            if (areaId == -1) throw new IllegalArgumentException("Almacén general no admite despeje de línea.");
            if (!Boolean.TRUE.equals(request.confirmaLineaExclusiva()))
                throw new IllegalArgumentException("Confirme que el área corresponde a una única línea física exclusiva.");
            if (hayTrabajoIniciado(areaId))
                throw conflicto("Termine las operaciones iniciadas o pausadas del área antes de habilitar el despeje.");
            area.setDespejeLineaExclusivaConfirmada(true);
        }
        if (area.isRequiereDespeje() && !request.requiereDespeje()) {
            // El intervalo sin seguimiento físico impide atribuir el último lote registrado
            // como antecedente inmediato al volver a habilitar. Se conserva su referencia histórica.
            area.setDespejeAntecedenteConocido(false);
        }
        area.setRequiereDespeje(request.requiereDespeje());
        area.setRequiereRevisionDespeje(request.requiereRevision());
        area.setDespejeRevisionContexto(area.getDespejeRevisionContexto() + 1);
        return toConfig(areaRepo.saveAndFlush(area));
    }

    private boolean hayTrabajoIniciado(Integer areaId) {
        Long op = entityManager.createQuery("""
                select count(s) from SeguimientoOrdenArea s
                where s.areaOperativa.areaId = :area and s.estado in (1,4)
                and s.ordenProduccion.estadoOrden not in (-1,2,3)
                and (s.estado = 4 or exists (select e.id from SeguimientoOrdenAreaEvento e
                    where e.seguimientoOrdenArea = s and e.estadoDestino = 4))
                """, Long.class).setParameter("area", areaId).getSingleResult();
        Long of = entityManager.createQuery("""
                select count(o) from OrdenFabricacionOperacion o
                where o.areaOperativa.areaId = :area and o.estado in (1,4)
                and o.ordenFabricacion.estado in :estadosActivos
                and (o.estado = 4 or exists (select e.id from OrdenFabricacionOperacionEvento e
                    where e.operacion = o and e.estadoDestino = 4))
                """, Long.class).setParameter("area", areaId)
                .setParameter("estadosActivos", java.util.List.of(
                        exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion.LIBERADA,
                        exotic.app.planta.model.produccion.fabricacion.EstadoOrdenFabricacion.EN_EJECUCION))
                .getSingleResult();
        return op > 0 || of > 0;
    }

    public List<Plantilla> listar(Integer areaId) {
        area(areaId);
        return plantillaRepo.findByAreaOperativa_AreaIdOrderByNumeroDesc(areaId).stream()
                .map(this::toDTO).toList();
    }

    @Transactional
    public Plantilla guardar(Integer areaId, PlantillaRequest request, User actor) {
        AreaOperativa area = bloquearArea(areaId);
        if (areaId == -1) throw new IllegalArgumentException("Almacén general no admite plantillas de despeje.");
        validarRequest(request);
        PlantillaDespejeVersion plantilla = plantillaRepo
                .findByAreaOperativa_AreaIdAndEstado(areaId, PlantillaDespejeVersion.Estado.BORRADOR).orElse(null);
        if (request.id() != null && (plantilla == null || !request.id().equals(plantilla.getId())))
            throw conflicto("La plantilla indicada no es el borrador de esta área.");
        if (plantilla == null) {
            plantilla = new PlantillaDespejeVersion();
            plantilla.setAreaOperativa(area);
            plantilla.setNumero(plantillaRepo.maxNumero(areaId) + 1);
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
        plantillaRepo.findByAreaOperativa_AreaIdAndEstado(plantilla.getAreaOperativa().getAreaId(),
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
                                i.isPermiteNa(), i.getRespuestaConforme(), i.getMinimo(), i.getMaximo())).toList());
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
        if (r == null || r.nombre() == null || r.nombre().isBlank() || r.nombre().trim().length() > 200
                || r.motivoCambio() == null || r.motivoCambio().isBlank() || r.motivoCambio().length() > 4000
                || r.items() == null || r.items().isEmpty() || r.items().size() > 100)
            throw new IllegalArgumentException("Indique nombre, motivo y entre 1 y 100 ítems.");
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
