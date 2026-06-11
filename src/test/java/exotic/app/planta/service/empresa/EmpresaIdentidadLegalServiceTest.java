package exotic.app.planta.service.empresa;

import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.dto.EmpresaIdentidadLegalVersionRequest;
import exotic.app.planta.repo.empresa.EmpresaIdentidadLegalVersionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmpresaIdentidadLegalServiceTest {

    private EmpresaIdentidadLegalVersionRepo repo;
    private EmpresaIdentidadLegalService service;

    @BeforeEach
    void setUp() {
        repo = mock(EmpresaIdentidadLegalVersionRepo.class);
        service = new EmpresaIdentidadLegalService(repo);
    }

    @Test
    void crearNuevaVersion_retiraAnteriorYCreaUnicaVigente() {
        EmpresaIdentidadLegalVersion vigenteAnterior = new EmpresaIdentidadLegalVersion();
        vigenteAnterior.setId(1L);
        vigenteAnterior.setVersion(1);
        vigenteAnterior.setEstado(EmpresaIdentidadLegalVersion.Estado.VIGENTE);

        when(repo.findByEstadoForUpdate(EmpresaIdentidadLegalVersion.Estado.VIGENTE))
                .thenReturn(Optional.of(vigenteAnterior));
        when(repo.findMaxVersion()).thenReturn(1);
        when(repo.save(any(EmpresaIdentidadLegalVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmpresaIdentidadLegalVersion nueva = service.crearNuevaVersion(validRequest(), "admin");

        assertEquals(EmpresaIdentidadLegalVersion.Estado.RETIRADA, vigenteAnterior.getEstado());
        assertNotNull(vigenteAnterior.getVigenteHasta());
        assertEquals(2, nueva.getVersion());
        assertEquals(EmpresaIdentidadLegalVersion.Estado.VIGENTE, nueva.getEstado());
        assertEquals("Napolitana J.P S.A.S.", nueva.getRazonSocial());
        assertEquals("EXOTIC EXPERT", nueva.getNombreComercial());
        assertEquals("admin", nueva.getCreadoPor());
        assertNotNull(nueva.getVigenteDesde());
        assertNotNull(nueva.getCreadoEn());
        verify(repo).save(vigenteAnterior);
        verify(repo).save(nueva);
    }

    @Test
    void getVigente_throwsWhenNoVigenteExists() {
        when(repo.findFirstByEstadoOrderByVersionDesc(EmpresaIdentidadLegalVersion.Estado.VIGENTE))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.getVigente());
    }

    @Test
    void resolveVersionForOcm_usesCurrentWhenIdIsNull() {
        EmpresaIdentidadLegalVersion vigente = new EmpresaIdentidadLegalVersion();
        vigente.setId(1L);
        when(repo.findFirstByEstadoOrderByVersionDesc(EmpresaIdentidadLegalVersion.Estado.VIGENTE))
                .thenReturn(Optional.of(vigente));

        assertEquals(vigente, service.resolveVersionForOcm(null));
    }

    @Test
    void resolveVersionForOcm_usesExplicitVersionWhenIdIsProvided() {
        EmpresaIdentidadLegalVersion historica = new EmpresaIdentidadLegalVersion();
        historica.setId(9L);
        when(repo.findById(9L)).thenReturn(Optional.of(historica));

        assertEquals(historica, service.resolveVersionForOcm(9L));
    }

    private static EmpresaIdentidadLegalVersionRequest validRequest() {
        EmpresaIdentidadLegalVersionRequest request = new EmpresaIdentidadLegalVersionRequest();
        request.setRazonSocial(" Napolitana J.P S.A.S. ");
        request.setNombreComercial(" EXOTIC EXPERT ");
        request.setTipoIdentificacion(" NIT ");
        request.setNumeroIdentificacion(" 901751897 ");
        request.setDigitoVerificacion(" 1 ");
        request.setTelefonoPrincipal(" 301 711 51 81 ");
        request.setEmailPrincipal(" produccion.exotic@gmail.com ");
        request.setMotivoCambio(" Actualizacion de datos legales ");
        return request;
    }
}
