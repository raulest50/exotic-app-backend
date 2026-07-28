package exotic.app.planta.service.empresa;

import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion;
import exotic.app.planta.model.empresa.dto.EmpresaIdentidadDocumentalVigenteResponse;
import exotic.app.planta.model.empresa.dto.EmpresaLogoDocumentalMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmpresaIdentidadDocumentalServiceTest {

    @Test
    void getVigente_componeRevisionConSeriesIndependientes() {
        EmpresaIdentidadLegalService identidadService = mock(EmpresaIdentidadLegalService.class);
        EmpresaLogoDocumentalService logoService = mock(EmpresaLogoDocumentalService.class);
        EmpresaIdentidadLegalVersion identidad = identidad(2L, 4);
        EmpresaLogoDocumentalMetadata logo = logo(9L, 7);
        when(identidadService.getVigente()).thenReturn(identidad);
        when(logoService.getVigenteMetadata()).thenReturn(logo);

        EmpresaIdentidadDocumentalVigenteResponse response =
                new EmpresaIdentidadDocumentalService(identidadService, logoService).getVigente();

        assertEquals("identidad-2-logo-9", response.revision());
        assertEquals(4, response.identidadLegal().version());
        assertEquals("Laboratorios Novum S.A.S.", response.identidadLegal().razonSocial());
        assertEquals(7, response.logo().version());
        assertEquals("/api/empresa-logo-documental/versiones/9/imagen", response.logo().imagenUrl());
    }

    @Test
    void getVigente_fallaSiNoExisteConfiguracionCompleta() {
        EmpresaIdentidadLegalService identidadService = mock(EmpresaIdentidadLegalService.class);
        EmpresaLogoDocumentalService logoService = mock(EmpresaLogoDocumentalService.class);
        when(identidadService.getVigente())
                .thenThrow(new IllegalStateException("No existe una identidad legal vigente configurada."));

        EmpresaIdentidadDocumentalService service =
                new EmpresaIdentidadDocumentalService(identidadService, logoService);

        assertThrows(IllegalStateException.class, service::getVigente);
    }

    private static EmpresaIdentidadLegalVersion identidad(Long id, int version) {
        EmpresaIdentidadLegalVersion identidad = new EmpresaIdentidadLegalVersion();
        identidad.setId(id);
        identidad.setVersion(version);
        identidad.setRazonSocial("Laboratorios Novum S.A.S.");
        identidad.setNombreComercial("Novum");
        identidad.setTipoIdentificacion("NIT");
        identidad.setNumeroIdentificacion("902038623");
        identidad.setDigitoVerificacion("5");
        identidad.setTelefonoPrincipal("3000000000");
        identidad.setEmailPrincipal("documental@example.com");
        return identidad;
    }

    private static EmpresaLogoDocumentalMetadata logo(Long id, int version) {
        EmpresaLogoDocumentalMetadata logo = mock(EmpresaLogoDocumentalMetadata.class);
        when(logo.getId()).thenReturn(id);
        when(logo.getVersion()).thenReturn(version);
        when(logo.getEstado()).thenReturn(EmpresaLogoDocumentalVersion.Estado.VIGENTE);
        when(logo.getSha256()).thenReturn("sha-logo");
        when(logo.getContentType()).thenReturn("image/png");
        when(logo.getTamanoBytes()).thenReturn(123L);
        when(logo.getAnchoPx()).thenReturn(578);
        when(logo.getAltoPx()).thenReturn(582);
        return logo;
    }
}
