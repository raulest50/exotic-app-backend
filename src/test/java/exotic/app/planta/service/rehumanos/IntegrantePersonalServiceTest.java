package exotic.app.planta.service.rehumanos;

import exotic.app.planta.model.organizacion.personal.DocTranDePersonal;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.dto.IntegrantePersonalDetalleDTO;
import exotic.app.planta.model.organizacion.personal.dto.IntegrantePersonalRequestDTO;
import exotic.app.planta.model.organizacion.personal.dto.IntegrantePersonalResumenDTO;
import exotic.app.planta.repo.personal.DocTranDePersonalRepo;
import exotic.app.planta.repo.personal.IntegrantePersonalRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrantePersonalServiceTest {

    private IntegrantePersonalRepo integrantePersonalRepo;
    private DocTranDePersonalRepo docTranDePersonalRepo;
    private IntegrantePersonalService service;

    @BeforeEach
    void setUp() {
        integrantePersonalRepo = Mockito.mock(IntegrantePersonalRepo.class);
        docTranDePersonalRepo = Mockito.mock(DocTranDePersonalRepo.class);
        service = new IntegrantePersonalService(integrantePersonalRepo, docTranDePersonalRepo);
    }

    @Test
    void saveRejectsMissingFechaIngreso() {
        IntegrantePersonalRequestDTO request = validRequest();
        request.setFechaIngreso(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveIntegrantePersonal(request, "sistema")
        );

        assertEquals("La fecha de ingreso es obligatoria.", error.getMessage());
    }

    @Test
    void saveReturnsDetalleWithFechaRegistroFromIngresoDocument() {
        when(integrantePersonalRepo.existsById(101L)).thenReturn(false);
        when(integrantePersonalRepo.save(any(IntegrantePersonal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(docTranDePersonalRepo.save(any(DocTranDePersonal.class))).thenAnswer(inv -> inv.getArgument(0));

        IntegrantePersonalDetalleDTO response = service.saveIntegrantePersonal(validRequest(), "sistema");

        assertEquals(101L, response.getId());
        assertEquals(LocalDate.of(2026, 7, 1), response.getFechaIngreso());
        assertEquals("Banco Uno", response.getBanco());
        assertNotNull(response.getFechaRegistro());
    }

    @Test
    void searchReturnsResumenWithoutSensitiveFields() throws Exception {
        IntegrantePersonal integrante = integrante();
        integrante.setBanco("Banco Uno");
        integrante.setNumeroCuentaBancaria("123456");
        integrante.setNombreContactoEmergencia("Contacto");
        Page<IntegrantePersonal> page = new PageImpl<>(List.of(integrante));

        when(integrantePersonalRepo.findByNombresOrApellidosContainingIgnoreCase(
                eq("Ana"),
                any(PageRequest.class)
        )).thenReturn(page);

        Page<IntegrantePersonalResumenDTO> response = service.searchIntegrantes("Ana", 0, 10);

        assertEquals(1, response.getTotalElements());
        assertEquals("Ana", response.getContent().get(0).getNombres());
        assertThrows(NoSuchFieldException.class,
                () -> IntegrantePersonalResumenDTO.class.getDeclaredField("banco"));
        assertThrows(NoSuchFieldException.class,
                () -> IntegrantePersonalResumenDTO.class.getDeclaredField("numeroCuentaBancaria"));
        assertThrows(NoSuchFieldException.class,
                () -> IntegrantePersonalResumenDTO.class.getDeclaredField("nombreContactoEmergencia"));
    }

    @Test
    void updateRegistersPersonalDataDocumentWhenFieldsChange() {
        IntegrantePersonal integrante = integrante();
        DocTranDePersonal ingreso = new DocTranDePersonal();
        ingreso.setFechaHora(LocalDateTime.of(2026, 7, 2, 8, 30));

        IntegrantePersonalRequestDTO request = validRequest();
        request.setBanco("Banco Dos");
        request.setNumeroCuentaBancaria("987654");

        when(integrantePersonalRepo.findById(101L)).thenReturn(Optional.of(integrante));
        when(integrantePersonalRepo.save(any(IntegrantePersonal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(docTranDePersonalRepo.findFirstByIdIntegrante_IdAndTipoDocTranOrderByFechaHoraAsc(
                eq(101L),
                eq(DocTranDePersonal.TipoDocTran.INGRESO)
        )).thenReturn(Optional.of(ingreso));

        IntegrantePersonalDetalleDTO response = service.updateIntegrantePersonal(101L, request, "sistema");

        ArgumentCaptor<DocTranDePersonal> captor = ArgumentCaptor.forClass(DocTranDePersonal.class);
        verify(docTranDePersonalRepo).save(captor.capture());
        DocTranDePersonal documento = captor.getValue();

        assertEquals("Banco Dos", response.getBanco());
        assertEquals(DocTranDePersonal.TipoDocTran.MODIFICACION_DATOS_PERSONALES, documento.getTipoDocTran());
        assertTrue(documento.getValoresNuevos().contains("\"banco\":\"Banco Dos\""));
        assertTrue(documento.getValoresNuevos().contains("\"numeroCuentaBancaria\":\"987654\""));
        assertEquals(LocalDateTime.of(2026, 7, 2, 8, 30), response.getFechaRegistro());
    }

    private IntegrantePersonalRequestDTO validRequest() {
        IntegrantePersonalRequestDTO request = new IntegrantePersonalRequestDTO();
        request.setId(101L);
        request.setNombres("Ana");
        request.setApellidos("Rios");
        request.setCelular("3001234567");
        request.setDireccion("Calle 1");
        request.setEmail("ana@example.com");
        request.setNombreContactoEmergencia("Luis Rios");
        request.setCelularContactoEmergencia("3007654321");
        request.setEstadoCivil(IntegrantePersonal.EstadoCivil.CASADO);
        request.setNumeroHijos(1);
        request.setFechaIngreso(LocalDate.of(2026, 7, 1));
        request.setNumeroCuentaBancaria("123456");
        request.setBanco("Banco Uno");
        request.setCargo("Operaria");
        request.setDepartamento(IntegrantePersonal.Departamento.PRODUCCION);
        request.setSalario(1500000);
        request.setEstado(IntegrantePersonal.Estado.ACTIVO);
        return request;
    }

    private IntegrantePersonal integrante() {
        IntegrantePersonal integrante = new IntegrantePersonal();
        integrante.setId(101L);
        integrante.setNombres("Ana");
        integrante.setApellidos("Rios");
        integrante.setCelular("3001234567");
        integrante.setDireccion("Calle 1");
        integrante.setEmail("ana@example.com");
        integrante.setNombreContactoEmergencia("Luis Rios");
        integrante.setCelularContactoEmergencia("3007654321");
        integrante.setEstadoCivil(IntegrantePersonal.EstadoCivil.CASADO);
        integrante.setNumeroHijos(1);
        integrante.setFechaIngreso(LocalDate.of(2026, 7, 1));
        integrante.setNumeroCuentaBancaria("123456");
        integrante.setBanco("Banco Uno");
        integrante.setCargo("Operaria");
        integrante.setDepartamento(IntegrantePersonal.Departamento.PRODUCCION);
        integrante.setSalario(1500000);
        integrante.setEstado(IntegrantePersonal.Estado.ACTIVO);
        return integrante;
    }
}
