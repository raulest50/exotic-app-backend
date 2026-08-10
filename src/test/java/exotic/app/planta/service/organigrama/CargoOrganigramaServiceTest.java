package exotic.app.planta.service.organigrama;

import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.model.organigrama.Cargo;
import exotic.app.planta.model.organigrama.OrganigramaEstado;
import exotic.app.planta.model.organigrama.dto.CargoOrganigramaRequest;
import exotic.app.planta.model.organigrama.dto.GuardarOrganigramaRequest;
import exotic.app.planta.model.organigrama.dto.OrganigramaSnapshotResponse;
import exotic.app.planta.model.organigrama.dto.RelacionOrganigramaRequest;
import exotic.app.planta.repo.organigrama.CargoOrganigramaRepo;
import exotic.app.planta.repo.organigrama.OrganigramaEstadoRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CargoOrganigramaServiceTest {

    private CargoOrganigramaRepo cargoRepo;
    private OrganigramaEstadoRepo estadoRepo;
    private CargoOrganigramaService service;

    @BeforeEach
    void setUp() {
        cargoRepo = mock(CargoOrganigramaRepo.class);
        estadoRepo = mock(OrganigramaEstadoRepo.class);
        service = new CargoOrganigramaService(
                cargoRepo,
                estadoRepo,
                mock(UserRepository.class),
                new StorageProperties()
        );
    }

    @Test
    void saveSnapshot_usesNodeCoordinatesAndPreservesManualWithoutDeletingEverything() {
        Cargo existing = cargo("cargo-1", 800, 600);
        existing.setUrlDocManualFunciones("/app/data/manual.pdf");
        OrganigramaEstado estado = estado(7);
        GuardarOrganigramaRequest request = request(7, cargoRequest("cargo-1", 0, 0));

        when(estadoRepo.findByIdForUpdate(OrganigramaEstado.SINGLETON_ID))
                .thenReturn(Optional.of(estado));
        when(cargoRepo.findAll()).thenReturn(List.of(existing));
        when(cargoRepo.findAllByOrderByIdCargoAsc()).thenReturn(List.of(existing));
        when(cargoRepo.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(estadoRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrganigramaSnapshotResponse response = service.saveSnapshot(request, "editor");

        assertEquals(0, existing.getPosicionX());
        assertEquals(0, existing.getPosicionY());
        assertEquals("/app/data/manual.pdf", existing.getUrlDocManualFunciones());
        assertEquals(8, response.revision());
        assertEquals(0, response.cargos().getFirst().posicionX());
        verify(cargoRepo, never()).deleteAll();
    }

    @Test
    void saveSnapshot_deletesOnlyCargosMissingFromTheRequestedSnapshot() {
        Cargo kept = cargo("kept", 10, 20);
        Cargo removed = cargo("removed", 30, 40);
        GuardarOrganigramaRequest request = request(2, cargoRequest("kept", 11, 22));

        when(estadoRepo.findByIdForUpdate(OrganigramaEstado.SINGLETON_ID))
                .thenReturn(Optional.of(estado(2)));
        when(cargoRepo.findAll()).thenReturn(List.of(kept, removed));
        when(cargoRepo.findAllByOrderByIdCargoAsc()).thenReturn(List.of(kept));

        service.saveSnapshot(request, "editor");

        verify(cargoRepo).deleteAllByIdInBatch(List.of("removed"));
        verify(cargoRepo, never()).deleteAll();
    }

    @Test
    void saveSnapshot_rejectsStaleRevisionBeforeWritingCargos() {
        when(estadoRepo.findByIdForUpdate(OrganigramaEstado.SINGLETON_ID))
                .thenReturn(Optional.of(estado(5)));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.saveSnapshot(request(4, cargoRequest("cargo-1", 10, 20)), "editor")
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(cargoRepo, never()).saveAll(any());
        verify(cargoRepo, never()).deleteAll();
    }

    @Test
    void saveSnapshot_rejectsCyclesBeforeLockingOrWriting() {
        GuardarOrganigramaRequest request = request(
                0,
                List.of(cargoRequest("a", 0, 0), cargoRequest("b", 100, 100)),
                List.of(relation("a", "b"), relation("b", "a"))
        );

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.saveSnapshot(request, "editor")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("ciclos"));
        verify(estadoRepo, never()).findByIdForUpdate(any());
        verify(cargoRepo, never()).saveAll(any());
    }

    @Test
    void setManualFuncionesUrl_doesNotIncrementGraphRevision() {
        OrganigramaEstado estado = estado(9);
        Cargo cargo = cargo("cargo-1", 10, 20);
        when(estadoRepo.findByIdForUpdate(OrganigramaEstado.SINGLETON_ID))
                .thenReturn(Optional.of(estado));
        when(cargoRepo.findById("cargo-1")).thenReturn(Optional.of(cargo));
        when(cargoRepo.save(cargo)).thenReturn(cargo);

        service.setManualFuncionesUrl("cargo-1", "https://example.com/manual.pdf");

        assertEquals(9, estado.getRevision());
        assertEquals("https://example.com/manual.pdf", cargo.getUrlDocManualFunciones());
        verify(estadoRepo, never()).save(any());
    }

    private static OrganigramaEstado estado(long revision) {
        return new OrganigramaEstado(
                OrganigramaEstado.SINGLETON_ID,
                revision,
                LocalDateTime.of(2026, 8, 10, 12, 0),
                "previous"
        );
    }

    private static Cargo cargo(String id, double x, double y) {
        Cargo cargo = new Cargo();
        cargo.setIdCargo(id);
        cargo.setTituloCargo("Cargo " + id);
        cargo.setDescripcionCargo("Descripción");
        cargo.setDepartamento("Departamento");
        cargo.setNivel(1);
        cargo.setPosicionX(x);
        cargo.setPosicionY(y);
        return cargo;
    }

    private static CargoOrganigramaRequest cargoRequest(String id, double x, double y) {
        CargoOrganigramaRequest cargo = new CargoOrganigramaRequest();
        cargo.setIdCargo(id);
        cargo.setTituloCargo("Cargo " + id);
        cargo.setDescripcionCargo("Descripción");
        cargo.setDepartamento("Departamento");
        cargo.setNivel(1);
        cargo.setPosicionX(x);
        cargo.setPosicionY(y);
        return cargo;
    }

    private static RelacionOrganigramaRequest relation(String boss, String subordinate) {
        RelacionOrganigramaRequest relation = new RelacionOrganigramaRequest();
        relation.setJefeId(boss);
        relation.setSubordinadoId(subordinate);
        return relation;
    }

    private static GuardarOrganigramaRequest request(
            long revision,
            CargoOrganigramaRequest cargo
    ) {
        return request(revision, List.of(cargo), List.of());
    }

    private static GuardarOrganigramaRequest request(
            long revision,
            List<CargoOrganigramaRequest> cargos,
            List<RelacionOrganigramaRequest> relaciones
    ) {
        GuardarOrganigramaRequest request = new GuardarOrganigramaRequest();
        request.setBaseRevision(revision);
        request.setCargos(cargos);
        request.setRelaciones(relaciones);
        return request;
    }
}
