package exotic.app.planta.service.produccion;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProduccionCierreLockService {

    private static final int FECHA_NAMESPACE = 8101;
    private static final int IDEMPOTENCY_NAMESPACE = 8102;

    private final EntityManager entityManager;

    public void lockFecha(LocalDate fechaProduccion) {
        lock(FECHA_NAMESPACE, Math.toIntExact(fechaProduccion.toEpochDay()));
    }

    public void lockIdempotency(UUID idempotencyKey) {
        lock(IDEMPOTENCY_NAMESPACE, idempotencyKey.hashCode());
    }

    private void lock(int namespace, int key) {
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(:namespace, :lockKey)")
                .setParameter("namespace", namespace)
                .setParameter("lockKey", key)
                .getSingleResult();
    }
}
