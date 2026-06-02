package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
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

    @Query("""
            SELECT r
            FROM RegistroHoraExtra r
            JOIN FETCH r.integrante i
            JOIN FETCH r.registradoPor rp
            LEFT JOIN FETCH r.aprobadoPor ap
            WHERE r.fecha >= :fechaDesde
              AND r.fecha <= :fechaHasta
              AND (:integranteId IS NULL OR i.id = :integranteId)
              AND (:departamento IS NULL OR i.departamento = :departamento)
              AND (
                    :cargo IS NULL
                    OR :cargo = ''
                    OR LOWER(i.cargo) LIKE LOWER(CONCAT('%', :cargo, '%'))
                  )
            ORDER BY r.fecha ASC, r.horaInicio ASC, r.id ASC
            """)
    List<RegistroHoraExtra> buscarBiHorasExtra(
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            @Param("integranteId") Long integranteId,
            @Param("departamento") IntegrantePersonal.Departamento departamento,
            @Param("cargo") String cargo
    );
}
