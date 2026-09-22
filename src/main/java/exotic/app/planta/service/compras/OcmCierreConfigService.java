package exotic.app.planta.service.compras;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Config;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.ConfigWrite;
import exotic.app.planta.model.compras.dto.OcmCierreDTOs.Modo;
import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcmCierreConfigService {
    private final MasterDirectiveRepo directiveRepo;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    @Transactional(readOnly = true)
    public Config consultar() {
        return directiveRepo.findByNombre(MasterDirectiveKeys.CIERRE_AUTOMATICO_OCM)
                .map(this::leer).orElseGet(Config::desactivada);
    }

    /** Always lock configuration before the OCM, holding it until the operation commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Config bloquearParaOperacion() {
        return directiveRepo.findByNombreForShare(MasterDirectiveKeys.CIERRE_AUTOMATICO_OCM)
                .map(this::leer).orElseGet(Config::desactivada);
    }

    @Transactional
    public Config actualizar(ConfigWrite request) {
        if (request == null || request.modo() == null
                || (request.dias() != null && request.dias() < 1)
                || (request.modo() == Modo.PLAZO && request.dias() == null)) {
            throw new IllegalArgumentException("El cierre por plazo requiere un número entero de días mayor o igual a 1.");
        }
        MasterDirective directive = directiveRepo.findByNombreForUpdate(MasterDirectiveKeys.CIERRE_AUTOMATICO_OCM)
                .orElseThrow(() -> new IllegalStateException("La directiva de cierre OCM no está inicializada."));
        Config previous = leer(directive);
        LocalDateTime activation = request.modo() == Modo.DESACTIVADO ? null
                : previous.modo() == Modo.DESACTIVADO ? LocalDateTime.now(applicationClock).truncatedTo(ChronoUnit.MICROS)
                : previous.activadoDesde();
        Config next = new Config(request.modo(), request.dias(), activation);
        try {
            directive.setValor(objectMapper.writeValueAsString(next));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No fue posible guardar la configuración de cierre OCM.", error);
        }
        directiveRepo.save(directive);
        log.info("Configuración de cierre OCM actualizada: modo={}, dias={}, activadoDesde={}",
                next.modo(), next.dias(), next.activadoDesde());
        return next;
    }

    private Config leer(MasterDirective directive) {
        try {
            Config config = objectMapper.readValue(directive.getValor(), Config.class);
            if (config == null || config.modo() == null
                    || (config.dias() != null && config.dias() < 1)
                    || (config.modo() != Modo.DESACTIVADO && config.activadoDesde() == null)
                    || (config.modo() == Modo.PLAZO && config.dias() == null)) {
                throw new IllegalArgumentException("Configuración incompleta");
            }
            return config;
        } catch (Exception error) {
            log.warn("Directiva de cierre OCM inválida; se deshabilita el cierre automático.");
            return Config.desactivada();
        }
    }
}
