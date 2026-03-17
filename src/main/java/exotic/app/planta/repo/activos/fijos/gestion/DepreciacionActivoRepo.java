package exotic.app.planta.repo.activos.fijos.gestion;

import exotic.app.planta.model.activos.fijos.ActivoFijo;
import exotic.app.planta.model.activos.fijos.gestion.DepreciacionActivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio para la gestión de depreciaciones de activos fijos.
 * Proporciona métodos para realizar operaciones CRUD y consultas personalizadas
 * sobre la entidad DepreciacionActivo.
 */
@Repository
public interface DepreciacionActivoRepo extends JpaRepository<DepreciacionActivo, Long> {

    boolean existsByAsientoContable_Id(Long asientoId);
    
    /**
     * Encuentra todas las depreciaciones para un activo específico.
     * 
     * @param activoFijo El activo fijo
     * @return Lista de depreciaciones del activo
     */
    List<DepreciacionActivo> findByActivoFijo(ActivoFijo activoFijo);
    
    /**
     * Encuentra depreciaciones por fecha.
     * 
     * @param fecha La fecha de depreciación
     * @return Lista de depreciaciones en la fecha especificada
     */
    List<DepreciacionActivo> findByFechaDepreciacion(LocalDate fecha);
    
    /**
     * Verifica si existe una depreciación para un activo en una fecha específica.
     * 
     * @param activoFijo El activo fijo
     * @param fecha La fecha de depreciación
     * @return true si existe una depreciación para el activo en la fecha especificada
     */
    boolean existsByActivoFijoAndFechaDepreciacion(ActivoFijo activoFijo, LocalDate fecha);
}