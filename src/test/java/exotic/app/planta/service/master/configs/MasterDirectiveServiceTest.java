package exotic.app.planta.service.master.configs;

import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterDirectiveServiceTest {

    @Test
    void getMpsSemanalDiasBloqueoEdicion_allowsZero() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION))
                .thenReturn(Optional.of(directive("0")));

        MasterDirectiveService service = new MasterDirectiveService(repo);

        assertEquals(0, service.getMpsSemanalDiasBloqueoEdicion());
    }

    @Test
    void getMpsSemanalDiasBloqueoEdicion_returnsConfiguredValueInRange() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION))
                .thenReturn(Optional.of(directive("7")));

        MasterDirectiveService service = new MasterDirectiveService(repo);

        assertEquals(7, service.getMpsSemanalDiasBloqueoEdicion());
    }

    @Test
    void getMpsSemanalDiasBloqueoEdicion_fallsBackWhenOutOfRange() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION))
                .thenReturn(Optional.of(directive("8")));

        MasterDirectiveService service = new MasterDirectiveService(repo);

        assertEquals(
                MasterDirectiveKeys.DEFAULT_MPS_SEMANAL_DIAS_BLOQUEO_EDICION,
                service.getMpsSemanalDiasBloqueoEdicion()
        );
    }

    @Test
    void getMpsSemanalDiasBloqueoEdicion_fallsBackWhenMissing() {
        MasterDirectiveRepo repo = mock(MasterDirectiveRepo.class);
        when(repo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION))
                .thenReturn(Optional.empty());

        MasterDirectiveService service = new MasterDirectiveService(repo);

        assertEquals(
                MasterDirectiveKeys.DEFAULT_MPS_SEMANAL_DIAS_BLOQUEO_EDICION,
                service.getMpsSemanalDiasBloqueoEdicion()
        );
    }

    private MasterDirective directive(String valor) {
        MasterDirective directive = new MasterDirective();
        directive.setNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION);
        directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
        directive.setValor(valor);
        return directive;
    }
}
