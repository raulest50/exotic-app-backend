package exotic.app.planta.repo.calidad;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ControlProcesoEjecucionSpecifications {

    private ControlProcesoEjecucionSpecifications() {
    }

    public static Specification<ControlProcesoEjecucion> conFiltros(
            Integer areaId,
            Long loteId,
            String producto,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (areaId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("plantilla").get("areaOperativa").get("areaId"),
                        areaId));
            }
            if (loteId != null) {
                predicates.add(criteriaBuilder.equal(root.get("lote").get("id"), loteId));
            }
            if (producto != null && !producto.isBlank()) {
                Join<?, ?> productoJoin = root
                        .join("lote")
                        .join("ordenProduccion")
                        .join("producto");
                String patron = "%" + producto.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(productoJoin.get("productoId")),
                                patron),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(productoJoin.get("nombre")),
                                patron)));
            }
            if (fechaDesde != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("fechaRegistro"), fechaDesde));
            }
            if (fechaHasta != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("fechaRegistro"), fechaHasta));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
