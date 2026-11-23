package exotic.app.planta.repo.personal;

import exotic.app.planta.model.organizacion.personal.RegistroVisitante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Optional;

public interface RegistroVisitanteRepo extends JpaRepository<RegistroVisitante, Long> {
    // Explicit scalar selection excludes the PNG and full authorization texts from every page.
    @Query(value = """
            select v.id as id, v.cedula as cedula, v.nombreCompleto as nombreCompleto,
                   v.edad as edad, v.genero as genero, v.motivo as motivo, v.motivoOtro as motivoOtro,
                   v.fechaRegistro as fechaRegistro, v.cargoEmpresa as cargoEmpresa,
                   v.tipoIngreso as tipoIngreso, v.areaPersonaVisita as areaPersonaVisita,
                   case when v.firmaSha256 is not null then true else false end as firmaRegistrada
            from RegistroVisitante v
            where v.fechaRegistro >= :inicio and v.fechaRegistro < :fin
              and (:nombre is null or lower(v.nombreCompleto) like :nombre escape '\\')
            """, countQuery = """
            select count(v.id) from RegistroVisitante v
            where v.fechaRegistro >= :inicio and v.fechaRegistro < :fin
              and (:nombre is null or lower(v.nombreCompleto) like :nombre escape '\\')
            """)
    Page<Resumen> consultar(@Param("inicio") Instant inicio, @Param("fin") Instant fin,
                           @Param("nombre") String nombre, Pageable pageable);

    @Query("select v.firmaContenido from RegistroVisitante v where v.id = :id and v.firmaSha256 is not null")
    Optional<byte[]> obtenerFirma(@Param("id") Long id);

    interface Resumen {
        Long getId();
        String getCedula();
        String getNombreCompleto();
        Integer getEdad();
        RegistroVisitante.Genero getGenero();
        RegistroVisitante.Motivo getMotivo();
        String getMotivoOtro();
        Instant getFechaRegistro();
        String getCargoEmpresa();
        RegistroVisitante.TipoIngreso getTipoIngreso();
        String getAreaPersonaVisita();
        boolean getFirmaRegistrada();
    }
}
