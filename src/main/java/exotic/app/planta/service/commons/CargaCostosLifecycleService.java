package exotic.app.planta.service.commons;

import exotic.app.planta.model.producto.costos.CargaCostosLote;
import exotic.app.planta.repo.producto.costos.CargaCostosLoteRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CargaCostosLifecycleService {
    private static final int RETENTION_DAYS = 30;

    private final CargaCostosLoteRepo loteRepo;
    private final Clock applicationClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expirarSiCorresponde(UUID loteId, Long usuarioId) {
        loteRepo.expirarSiCorresponde(
                loteId,
                usuarioId,
                LocalDateTime.now(applicationClock),
                CargaCostosLote.Estado.PREPARADO,
                CargaCostosLote.Estado.EXPIRADO);
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "America/Bogota")
    @Transactional
    public void mantenerLotes() {
        LocalDateTime now = LocalDateTime.now(applicationClock);
        int expired = loteRepo.expirarVencidos(
                now,
                CargaCostosLote.Estado.PREPARADO,
                CargaCostosLote.Estado.EXPIRADO);
        int deleted = loteRepo.eliminarTerminalesAntiguos(
                List.of(
                        CargaCostosLote.Estado.EXPIRADO,
                        CargaCostosLote.Estado.BLOQUEADO,
                        CargaCostosLote.Estado.CANCELADO),
                now.minusDays(RETENTION_DAYS));
        if (expired > 0 || deleted > 0) {
            log.info("[CARGA_COSTOS] Mantenimiento: expirados={}, eliminados={}", expired, deleted);
        }
    }
}
