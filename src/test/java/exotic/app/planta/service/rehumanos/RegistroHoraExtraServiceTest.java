package exotic.app.planta.service.rehumanos;

import exotic.app.planta.model.organizacion.personal.DocTranDePersonal;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraDecisionDTO;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraRequestDTO;
import exotic.app.planta.model.organizacion.personal.dto.RegistroHoraExtraResponseDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.personal.DocTranDePersonalRepo;
import exotic.app.planta.repo.personal.IntegrantePersonalRepo;
import exotic.app.planta.repo.personal.RegistroHoraExtraRepo;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistroHoraExtraServiceTest {

    private RegistroHoraExtraRepo registroHoraExtraRepo;
    private IntegrantePersonalRepo integrantePersonalRepo;
    private DocTranDePersonalRepo docTranDePersonalRepo;
    private RegistroHoraExtraService service;
    private IntegrantePersonal integrante;
    private User gestor;

    @BeforeEach
    void setUp() {
        registroHoraExtraRepo = Mockito.mock(RegistroHoraExtraRepo.class);
        integrantePersonalRepo = Mockito.mock(IntegrantePersonalRepo.class);
        docTranDePersonalRepo = Mockito.mock(DocTranDePersonalRepo.class);
        service = new RegistroHoraExtraService(registroHoraExtraRepo, integrantePersonalRepo, docTranDePersonalRepo);

        integrante = new IntegrantePersonal();
        integrante.setId(1010L);
        integrante.setNombres("Ana");
        integrante.setApellidos("Rios");
        integrante.setEstado(IntegrantePersonal.Estado.ACTIVO);

        gestor = new User();
        gestor.setId(7L);
        gestor.setUsername("rh");
        gestor.setNombreCompleto("Recursos Humanos");

        when(docTranDePersonalRepo.save(any(DocTranDePersonal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registrar_paraIntegranteActivo_calculaMinutosYDocumenta() {
        when(integrantePersonalRepo.findById(1010L)).thenReturn(Optional.of(integrante));
        when(registroHoraExtraRepo.save(any(RegistroHoraExtra.class))).thenAnswer(inv -> {
            RegistroHoraExtra registro = inv.getArgument(0);
            registro.setId(55L);
            return registro;
        });

        RegistroHoraExtraResponseDTO response = service.registrar(
                1010L,
                new RegistroHoraExtraRequestDTO(
                        LocalDate.of(2026, 6, 2),
                        LocalTime.of(18, 0),
                        LocalTime.of(20, 30),
                        "Cierre mensual",
                        "Soporte administrativo"
                ),
                gestor
        );

        assertEquals(55L, response.getId());
        assertEquals(150, response.getMinutos());
        assertEquals(RegistroHoraExtra.Estado.REGISTRADA, response.getEstado());
        assertEquals(gestor.getUsername(), response.getRegistradoPorUsername());
        verify(docTranDePersonalRepo).save(any(DocTranDePersonal.class));
    }

    @Test
    void registrar_rechazaIntegranteInexistente() {
        when(integrantePersonalRepo.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.registrar(
                404L,
                requestValido(),
                gestor
        ));
    }

    @Test
    void registrar_rechazaIntegranteInactivo() {
        integrante.setEstado(IntegrantePersonal.Estado.INACTIVO);
        when(integrantePersonalRepo.findById(1010L)).thenReturn(Optional.of(integrante));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.registrar(
                1010L,
                requestValido(),
                gestor
        ));

        assertEquals("Solo se pueden registrar horas extra para integrantes activos.", error.getMessage());
    }

    @Test
    void registrar_bloqueaRangoHorarioInvalido() {
        when(integrantePersonalRepo.findById(1010L)).thenReturn(Optional.of(integrante));

        RegistroHoraExtraRequestDTO request = new RegistroHoraExtraRequestDTO(
                LocalDate.of(2026, 6, 2),
                LocalTime.of(20, 0),
                LocalTime.of(19, 0),
                "Inventario",
                null
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.registrar(
                1010L,
                request,
                gestor
        ));

        assertEquals("La hora de fin debe ser posterior a la hora de inicio.", error.getMessage());
    }

    @Test
    void aprobar_desdeRegistrada_cambiaEstadoYDocumenta() {
        RegistroHoraExtra registro = registroPersistido(RegistroHoraExtra.Estado.REGISTRADA);
        when(registroHoraExtraRepo.findById(55L)).thenReturn(Optional.of(registro));
        when(registroHoraExtraRepo.save(any(RegistroHoraExtra.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroHoraExtraResponseDTO response = service.aprobar(55L, gestor);

        assertEquals(RegistroHoraExtra.Estado.APROBADA, response.getEstado());
        assertEquals(gestor.getUsername(), response.getAprobadoPorUsername());
        assertNotNull(response.getFechaDecision());
        verify(docTranDePersonalRepo).save(any(DocTranDePersonal.class));
    }

    @Test
    void aprobar_rechazaRegistroRechazado() {
        RegistroHoraExtra registro = registroPersistido(RegistroHoraExtra.Estado.RECHAZADA);
        when(registroHoraExtraRepo.findById(55L)).thenReturn(Optional.of(registro));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.aprobar(55L, gestor));

        assertEquals("Solo se pueden aprobar registros en estado REGISTRADA.", error.getMessage());
    }

    @Test
    void rechazar_desdeRegistrada_cambiaEstadoConMotivo() {
        RegistroHoraExtra registro = registroPersistido(RegistroHoraExtra.Estado.REGISTRADA);
        when(registroHoraExtraRepo.findById(55L)).thenReturn(Optional.of(registro));
        when(registroHoraExtraRepo.save(any(RegistroHoraExtra.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroHoraExtraResponseDTO response = service.rechazar(
                55L,
                new RegistroHoraExtraDecisionDTO("No autorizado"),
                gestor
        );

        assertEquals(RegistroHoraExtra.Estado.RECHAZADA, response.getEstado());
        assertEquals("No autorizado", response.getMotivoRechazoOAnulacion());
        verify(docTranDePersonalRepo).save(any(DocTranDePersonal.class));
    }

    @Test
    void anular_bloqueaRegistroYaAnulado() {
        RegistroHoraExtra registro = registroPersistido(RegistroHoraExtra.Estado.ANULADA);
        when(registroHoraExtraRepo.findById(55L)).thenReturn(Optional.of(registro));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.anular(
                55L,
                new RegistroHoraExtraDecisionDTO("Duplicado"),
                gestor
        ));

        assertEquals("No se puede anular un registro ya anulado.", error.getMessage());
    }

    private RegistroHoraExtraRequestDTO requestValido() {
        return new RegistroHoraExtraRequestDTO(
                LocalDate.of(2026, 6, 2),
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
                "Inventario",
                null
        );
    }

    private RegistroHoraExtra registroPersistido(RegistroHoraExtra.Estado estado) {
        RegistroHoraExtra registro = new RegistroHoraExtra();
        registro.setId(55L);
        registro.setIntegrante(integrante);
        registro.setFecha(LocalDate.of(2026, 6, 2));
        registro.setHoraInicio(LocalTime.of(18, 0));
        registro.setHoraFin(LocalTime.of(20, 0));
        registro.setMinutos(120);
        registro.setMotivo("Inventario");
        registro.setEstado(estado);
        registro.setRegistradoPor(gestor);
        return registro;
    }
}
