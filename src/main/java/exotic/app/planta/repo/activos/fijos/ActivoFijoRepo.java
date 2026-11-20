package exotic.app.planta.repo.activos.fijos;

import exotic.app.planta.model.activos.fijos.ActivoFijo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para la gestión de activos fijos.
 * Proporciona métodos para realizar operaciones CRUD y consultas personalizadas
 * sobre la entidad ActivoFijo.
 */
@Repository
public interface ActivoFijoRepo extends JpaRepository<ActivoFijo, String>, JpaSpecificationExecutor<ActivoFijo> {

    @Query("select a from ActivoFijo a join fetch a.tipoRecurso r where a.estado = 0 and a.tipoActivo = :tipo and r.id in :recursos order by a.nombre, a.id")
    java.util.List<ActivoFijo> equiposDeRecursos(
            @org.springframework.data.repository.query.Param("tipo") ActivoFijo.TipoActivo tipo,
            @org.springframework.data.repository.query.Param("recursos") java.util.Collection<Long> recursos);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select a from ActivoFijo a where a.id = :id")
    java.util.Optional<ActivoFijo> findByIdForLote(@org.springframework.data.repository.query.Param("id") String id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ActivoFijo a where a.id = :id")
    java.util.Optional<ActivoFijo> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") String id);

    /**
     * Busca activos fijos por ubicación.
     * 
     * @param ubicacion La ubicación a buscar
     * @return Lista de activos fijos que coinciden con la ubicación especificada
     */
    java.util.List<ActivoFijo> findByUbicacion(String ubicacion);

    /**
     * Busca activos fijos por responsable.
     * 
     * @param responsableId El ID del responsable
     * @return Lista de activos fijos asignados al responsable especificado
     */
    java.util.List<ActivoFijo> findByResponsableId(long responsableId);

    /**
     * Busca activos fijos por tipo.
     * 
     * @param tipoActivo El tipo de activo a buscar
     * @return Lista de activos fijos del tipo especificado
     */
    java.util.List<ActivoFijo> findByTipoActivo(ActivoFijo.TipoActivo tipoActivo);

    /**
     * Encuentra activos fijos que pueden ser depreciados.
     * Un activo es depreciable si:
     * - Está activo (estado = 0)
     * - Tiene valor de adquisición
     * - Tiene vida útil definida mayor a 0
     * 
     * @return Lista de activos fijos depreciables
     */
    @Query("SELECT a FROM ActivoFijo a WHERE a.estado = 0 AND a.valorAdquisicion IS NOT NULL AND a.vidaUtilMeses > 0")
    java.util.List<ActivoFijo> findActivosDepreciables();
}
