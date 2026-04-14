package exotic.app.planta.service.commons;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.commons.divisas.TRM;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Servicio simplificado que devuelve un valor fijo para la TRM.
 * Utilizado temporalmente para prototipos.
 */
@Service
@Slf4j
public class TRMService {

    private static final BigDecimal TRM_VALUE = new BigDecimal("4100");

    /**
     * Retorna la TRM actual. El valor es constante y la fecha corresponde
     * al momento de la llamada.
     */
    public TRM getTRMActual() {
        log.info("Devolviendo TRM constante");
        TRM trm = new TRM();
        trm.setFecha(AppTime.today());
        trm.setValor(TRM_VALUE);
        trm.setUnidad("COP");
        return trm;
    }

    /**
     * Retorna la TRM para una fecha dada. El valor es constante
     * sin importar la fecha solicitada.
     */
    public TRM getTRMPorFecha(LocalDate fecha) {
        log.info("Devolviendo TRM constante para la fecha {}", fecha);
        TRM trm = new TRM();
        trm.setFecha(fecha);
        trm.setValor(TRM_VALUE);
        trm.setUnidad("COP");
        return trm;
    }
}
