package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.RegistroIdempotenciaControl;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RegistroIdempotenciaControlRepo
        extends JpaRepository<RegistroIdempotenciaControl, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO control_idempotencia
                (actor_id, accion, recurso, clave, huella_payload, creada_en)
            VALUES
                (:actorId, :accion, :recurso, :clave, :huella, :creadaEn)
            ON CONFLICT (actor_id, accion, recurso, clave) DO NOTHING
            """, nativeQuery = true)
    int insertarSiAusente(
            @Param("actorId") Long actorId,
            @Param("accion") String accion,
            @Param("recurso") String recurso,
            @Param("clave") String clave,
            @Param("huella") String huella,
            @Param("creadaEn") LocalDateTime creadaEn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT registro
            FROM RegistroIdempotenciaControl registro
            WHERE registro.actor.id = :actorId
              AND registro.accion = :accion
              AND registro.recurso = :recurso
              AND registro.clave = :clave
            """)
    Optional<RegistroIdempotenciaControl> buscarParaActualizar(
            @Param("actorId") Long actorId,
            @Param("accion") String accion,
            @Param("recurso") String recurso,
            @Param("clave") String clave);
}
