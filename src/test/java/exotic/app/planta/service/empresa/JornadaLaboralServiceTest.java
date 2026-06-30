package exotic.app.planta.service.empresa;

import exotic.app.planta.model.empresa.JornadaLaboralVersion;
import exotic.app.planta.model.empresa.dto.JornadaLaboralBloqueRequest;
import exotic.app.planta.model.empresa.dto.JornadaLaboralDiaRequest;
import exotic.app.planta.model.empresa.dto.JornadaLaboralVersionRequest;
import exotic.app.planta.model.empresa.dto.JornadaLaboralVersionResponse;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JornadaLaboralServiceTest {

    private JornadaLaboralVersionRepo repo;
    private JornadaLaboralService service;

    @BeforeEach
    void setUp() {
        repo = mock(JornadaLaboralVersionRepo.class);
        service = new JornadaLaboralService(repo);
    }

    @Test
    void crearNuevaVersion_retiraAnteriorYCreaUnicaVigente() {
        JornadaLaboralVersion vigenteAnterior = new JornadaLaboralVersion();
        vigenteAnterior.setId(1L);
        vigenteAnterior.setVersion(1);
        vigenteAnterior.setEstado(JornadaLaboralVersion.Estado.VIGENTE);

        when(repo.findByEstadoForUpdate(JornadaLaboralVersion.Estado.VIGENTE))
                .thenReturn(Optional.of(vigenteAnterior));
        when(repo.findMaxVersion()).thenReturn(1);
        when(repo.save(any(JornadaLaboralVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JornadaLaboralVersionResponse nueva = service.crearNuevaVersion(validRequest(), "admin");

        assertEquals(JornadaLaboralVersion.Estado.RETIRADA, vigenteAnterior.getEstado());
        assertNotNull(vigenteAnterior.getVigenteHasta());
        assertEquals(2, nueva.getVersion());
        assertEquals(JornadaLaboralVersion.Estado.VIGENTE, nueva.getEstado());
        assertEquals("admin", nueva.getCreadoPor());
        assertEquals("Actualizacion jornada", nueva.getMotivoCambio());
        assertNotNull(nueva.getVigenteDesde());
        assertNotNull(nueva.getCreadoEn());
        assertEquals(2, nueva.getBloques().size());
        verify(repo).save(vigenteAnterior);
    }

    @Test
    void crearNuevaVersion_rechazaBloquesSolapados() {
        JornadaLaboralVersionRequest request = validRequest();
        JornadaLaboralDiaRequest monday = request.getDias().getFirst();
        monday.setBloques(List.of(
                block("07:30", "12:00"),
                block("11:30", "17:00")
        ));

        assertThrows(IllegalArgumentException.class, () -> service.crearNuevaVersion(request, "admin"));
    }

    @Test
    void crearNuevaVersion_rechazaDiaNoLaborableConBloques() {
        JornadaLaboralVersionRequest request = validRequest();
        JornadaLaboralDiaRequest sunday = day(7, false, List.of(block("07:30", "12:00")));
        request.setDias(List.of(request.getDias().getFirst(), sunday));

        assertThrows(IllegalArgumentException.class, () -> service.crearNuevaVersion(request, "admin"));
    }

    private static JornadaLaboralVersionRequest validRequest() {
        JornadaLaboralVersionRequest request = new JornadaLaboralVersionRequest();
        request.setMotivoCambio(" Actualizacion jornada ");
        request.setDias(List.of(
                day(1, true, List.of(
                        block("07:30", "12:00"),
                        block("13:00", "17:00")
                )),
                day(7, false, List.of())
        ));
        return request;
    }

    private static JornadaLaboralDiaRequest day(
            int diaSemana,
            boolean laborable,
            List<JornadaLaboralBloqueRequest> bloques
    ) {
        JornadaLaboralDiaRequest request = new JornadaLaboralDiaRequest();
        request.setDiaSemana(diaSemana);
        request.setLaborable(laborable);
        request.setBloques(bloques);
        return request;
    }

    private static JornadaLaboralBloqueRequest block(String horaInicio, String horaFin) {
        JornadaLaboralBloqueRequest request = new JornadaLaboralBloqueRequest();
        request.setHoraInicio(LocalTime.parse(horaInicio));
        request.setHoraFin(LocalTime.parse(horaFin));
        return request;
    }
}
