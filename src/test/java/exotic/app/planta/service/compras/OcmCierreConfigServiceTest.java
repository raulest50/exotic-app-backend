package exotic.app.planta.service.compras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Config;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.ConfigWrite;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Modo;
import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.model.master.configs.dto.DTO_MasterD_Update;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcmCierreConfigServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:00:00Z"), ZoneId.of("America/Bogota"));
    private MasterDirectiveRepo repo;
    private MasterDirective directive;
    private OcmCierreConfigService service;

    @BeforeEach
    void preparar() throws Exception {
        repo = mock(MasterDirectiveRepo.class);
        directive = new MasterDirective();
        directive.setId(4L);
        directive.setNombre(MasterDirectiveKeys.CIERRE_AUTOMATICO_OCM);
        directive.setValor(mapper.writeValueAsString(Config.desactivada()));
        service = new OcmCierreConfigService(repo, mapper, clock);
    }

    @Test
    void cambiarDiasOModoConservaElCorteYReactivarLoRenueva() throws Exception {
        LocalDateTime anterior = LocalDateTime.now(clock).minusDays(3);
        directive.setValor(mapper.writeValueAsString(new Config(Modo.PLAZO, 3, anterior)));
        when(repo.findByNombreForUpdate(directive.getNombre())).thenReturn(Optional.of(directive));
        assertEquals(anterior, service.actualizar(new ConfigWrite(Modo.PLAZO, 1)).activadoDesde());
        assertEquals(anterior, service.actualizar(new ConfigWrite(Modo.RECEPCION_COMPLETA, null)).activadoDesde());
        assertNull(service.actualizar(new ConfigWrite(Modo.DESACTIVADO, null)).activadoDesde());
        assertEquals(LocalDateTime.now(clock), service.actualizar(new ConfigWrite(Modo.PLAZO, 2)).activadoDesde());
    }

    @Test
    void configuracionAusenteOCorruptaDesactivaLaAutomatizacion() {
        when(repo.findByNombre(directive.getNombre())).thenReturn(Optional.empty());
        assertEquals(Modo.DESACTIVADO, service.consultar().modo());
        directive.setValor("{\"modo\":\"PLAZO\",\"dias\":2}");
        when(repo.findByNombre(directive.getNombre())).thenReturn(Optional.of(directive));
        assertEquals(Modo.DESACTIVADO, service.consultar().modo());
    }

    @Test
    void rechazaDiasNulosCeroNegativosYDecimalesSinRedondearlos() {
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(new ConfigWrite(Modo.PLAZO, null)));
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(new ConfigWrite(Modo.PLAZO, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(new ConfigWrite(Modo.PLAZO, -1)));
        assertThrows(Exception.class, () -> mapper.readValue("{\"modo\":\"PLAZO\",\"dias\":1.5}", ConfigWrite.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"modo\":\"PLAZO\",\"dias\":\"2\"}", ConfigWrite.class));
        verifyNoInteractions(repo);
    }

    @Test
    void elEndpointGenericoNoPuedeOmitirLaValidacionEspecifica() {
        when(repo.findByIdForUpdate(4L)).thenReturn(Optional.of(directive));
        var generic = new MasterDirectiveService(repo);
        assertThrows(IllegalArgumentException.class, () -> generic.updateMasterDirective(new DTO_MasterD_Update(directive, directive)));
        verify(repo, never()).save(any());
    }
}
