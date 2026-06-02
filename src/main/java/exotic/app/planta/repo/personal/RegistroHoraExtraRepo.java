package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RegistroHoraExtraRepo extends JpaRepository<RegistroHoraExtra, Long> {

    List<RegistroHoraExtra> findByIntegrante_IdOrderByFechaDescHoraInicioDesc(Long integranteId);

    @Query("""
            SELECT r
            FROM RegistroHoraExtra r
            JOIN r.integrante i
            WHERE (:desde IS NULL OR r.fecha >= :desde)
              AND (:hasta IS NULL OR r.fecha <= :hasta)
              AND (:estado IS NULL OR r.estado = :estado)
              AND (
                    :q IS NULL
                    OR :q = ''
                    OR LOWER(i.nombres) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(i.apellidos) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR CAST(i.id AS string) LIKE CONCAT('%', :q, '%')
                  )
            ORDER BY r.fecha DESC, r.horaInicio DESC, r.id DESC
            """)
    Page<RegistroHoraExtra> buscar(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("estado") RegistroHoraExtra.Estado estado,
            @Param("q") String q,
            Pageable pageable
    );
}
