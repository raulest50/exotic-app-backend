package exotic.app.planta.repo.controles;

import exotic.app.planta.model.controles.AmbitoControl;
import exotic.app.planta.model.controles.ControlRequerido;
import exotic.app.planta.model.controles.EjecucionControl;
import exotic.app.planta.model.controles.ResultadoEjecucionControl;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class EjecucionControlSpecifications {

    private EjecucionControlSpecifications() {
    }

    /**
     * Omite los filtros ausentes para no enviar a PostgreSQL parámetros nulos
     * sin tipo en expresiones IS NULL. El ámbito se filtra siempre.
     */
    public static Specification<EjecucionControl> conFiltros(
            AmbitoControl ambito,
            Long loteId,
            Long batchRecordId,
            String search,
            ResultadoEjecucionControl resultado,
            LocalDateTime desde,
            LocalDateTime hasta
    ) {
        return (root, query, criteriaBuilder) -> {
            Path<ControlRequerido> requerido = root.get("controlRequerido");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(requerido.get("ambitoSnapshot"), ambito));

            if (loteId != null) {
                predicates.add(criteriaBuilder.equal(requerido.get("lote").get("id"), loteId));
            }
            if (batchRecordId != null) {
                predicates.add(criteriaBuilder.equal(
                        requerido.get("batchRecord").get("id"), batchRecordId));
            }
            if (search != null && !search.isBlank()) {
                Expression<String> patron = criteriaBuilder.lower(
                        criteriaBuilder.literal("%" + search + "%"));
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(requerido.get("planCodigoSnapshot")), patron),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(requerido.get("planNombreSnapshot")), patron),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(requerido.get("lote").get("batchNumber")), patron),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(requerido.get("productoIdSnapshot")), patron)));
            }
            if (desde != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fechaRegistro"), desde));
            }
            if (hasta != null) {
                // El servicio convierte la fecha final al inicio del día siguiente.
                predicates.add(criteriaBuilder.lessThan(root.get("fechaRegistro"), hasta));
            }
            if (resultado != null) {
                predicates.add(criteriaBuilder.equal(root.get("resultado"), resultado));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
