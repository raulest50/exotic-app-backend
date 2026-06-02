package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.HorasExtraBiEstadoDTO;
import exotic.app.planta.model.bi.dto.HorasExtraBiResumenDTO;
import exotic.app.planta.model.bi.dto.HorasExtraBiSerieDTO;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.personal.RegistroHoraExtraRepo;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalBiServiceTest {

    private RegistroHoraExtraRepo registroHoraExtraRepo;
    private PersonalBiService service;
    private IntegrantePersonal integrante;
    private User gestor;

    @BeforeEach
    void setUp() {
        registroHoraExtraRepo = Mockito.mock(RegistroHoraExtraRepo.class);
        service = new PersonalBiService(registroHoraExtraRepo);

        integrante = new IntegrantePersonal();
        integrante.setId(1010L);
        integrante.setNombres("Ana");
        integrante.setApellidos("Rios");
        integrante.setCargo("Operario");
        integrante.setDepartamento(IntegrantePersonal.Departamento.PRODUCCION);
        integrante.setEstado(IntegrantePersonal.Estado.ACTIVO);

        gestor = new User();
        gestor.setId(7L);
        gestor.setUsername("rh");
        gestor.setNombreCompleto("Recursos Humanos");
    }

    @Test
    void resumenHorasExtra_sumaMinutosYRegistrosPorEstado() {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);
        when(registroHoraExtraRepo.buscarBiHorasExtra(desde, hasta, null, null, null))
                .thenReturn(List.of(
                        registro(1L, LocalDate.of(2026, 6, 2), 120, RegistroHoraExtra.Estado.APROBADA),
                        registro(2L, LocalDate.of(2026, 6, 3), 30, RegistroHoraExtra.Estado.APROBADA),
                        registro(3L, LocalDate.of(2026, 6, 4), 60, RegistroHoraExtra.Estado.REGISTRADA)
                ));

        HorasExtraBiResumenDTO result = service.resumenHorasExtra(desde, hasta, null, null, null);

        assertEquals(3, result.getTotalRegistros());
        assertEquals(210, result.getTotalMinutos());
        assertEquals(3.5, result.getTotalHoras());
        HorasExtraBiEstadoDTO aprobada = estado(result, RegistroHoraExtra.Estado.APROBADA);
        assertEquals(2, aprobada.getRegistros());
        assertEquals(150, aprobada.getMinutos());
        assertEquals(2.5, aprobada.getHoras());
        assertEquals(1, estado(result, RegistroHoraExtra.Estado.REGISTRADA).getRegistros());
    }

    @Test
    void serieHorasExtra_agrupaPorSemana() {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 15);
        when(registroHoraExtraRepo.buscarBiHorasExtra(desde, hasta, null, null, null))
                .thenReturn(List.of(
                        registro(1L, LocalDate.of(2026, 6, 2), 120, RegistroHoraExtra.Estado.APROBADA),
                        registro(2L, LocalDate.of(2026, 6, 4), 60, RegistroHoraExtra.Estado.RECHAZADA),
                        registro(3L, LocalDate.of(2026, 6, 9), 30, RegistroHoraExtra.Estado.ANULADA)
                ));

        HorasExtraBiSerieDTO result = service.serieHorasExtra(desde, hasta, HorasExtraBiGranularidad.SEMANA, null, null, null);

        assertEquals(2, result.getPuntos().size());
        assertEquals(LocalDate.of(2026, 6, 1), result.getPuntos().get(0).getFechaInicio());
        assertEquals(2.0, result.getPuntos().get(0).getHorasAprobada());
        assertEquals(1.0, result.getPuntos().get(0).getHorasRechazada());
        assertEquals(LocalDate.of(2026, 6, 8), result.getPuntos().get(1).getFechaInicio());
        assertEquals(0.5, result.getPuntos().get(1).getHorasAnulada());
    }

    @Test
    void filtros_seDeleganAlRepositorio() {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);
        when(registroHoraExtraRepo.buscarBiHorasExtra(
                desde,
                hasta,
                1010L,
                IntegrantePersonal.Departamento.PRODUCCION,
                "Operario"
        )).thenReturn(List.of());

        service.resumenHorasExtra(desde, hasta, 1010L, IntegrantePersonal.Departamento.PRODUCCION, " Operario ");

        verify(registroHoraExtraRepo).buscarBiHorasExtra(
                desde,
                hasta,
                1010L,
                IntegrantePersonal.Departamento.PRODUCCION,
                "Operario"
        );
    }

    @Test
    void rangoInvalido_lanzaIllegalArgumentException() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.resumenHorasExtra(
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 6, 1),
                null,
                null,
                null
        ));

        assertEquals("fechaHasta no puede ser anterior a fechaDesde.", error.getMessage());
    }

    @Test
    void exportarHorasExtraExcel_contieneTresHojas() throws Exception {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);
        when(registroHoraExtraRepo.buscarBiHorasExtra(desde, hasta, null, null, null))
                .thenReturn(List.of(registro(1L, LocalDate.of(2026, 6, 2), 120, RegistroHoraExtra.Estado.APROBADA)));

        byte[] excel = service.exportarHorasExtraExcel(desde, hasta, HorasExtraBiGranularidad.DIA, null, null, null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            assertEquals(3, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Serie temporal"));
            assertNotNull(workbook.getSheet("Detalle"));
        }
    }

    private HorasExtraBiEstadoDTO estado(HorasExtraBiResumenDTO result, RegistroHoraExtra.Estado estado) {
        return result.getEstados().stream()
                .filter((item) -> item.getEstado() == estado)
                .findFirst()
                .orElseThrow();
    }

    private RegistroHoraExtra registro(Long id, LocalDate fecha, int minutos, RegistroHoraExtra.Estado estado) {
        RegistroHoraExtra registro = new RegistroHoraExtra();
        registro.setId(id);
        registro.setIntegrante(integrante);
        registro.setFecha(fecha);
        registro.setHoraInicio(LocalTime.of(18, 0));
        registro.setHoraFin(LocalTime.of(18, 0).plusMinutes(minutos));
        registro.setMinutos(minutos);
        registro.setMotivo("Inventario");
        registro.setObservaciones("Observacion");
        registro.setEstado(estado);
        registro.setRegistradoPor(gestor);
        registro.setFechaRegistro(LocalDateTime.of(fecha, LocalTime.of(17, 45)));
        if (estado != RegistroHoraExtra.Estado.REGISTRADA) {
            registro.setAprobadoPor(gestor);
            registro.setFechaDecision(LocalDateTime.of(fecha, LocalTime.of(20, 0)));
        }
        return registro;
    }
}
