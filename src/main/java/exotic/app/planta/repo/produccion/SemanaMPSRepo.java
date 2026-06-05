package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.SemanaMPS;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SemanaMPSRepo extends JpaRepository<SemanaMPS, Long> {
    Optional<SemanaMPS> findByCodigo(String codigo);
    Optional<SemanaMPS> findByStandardAndStartDate(String standard, LocalDate startDate);
    Optional<SemanaMPS> findByStandardAndAnioSemanaAndNumeroSemana(String standard, int anioSemana, int numeroSemana);
    List<SemanaMPS> findAllByStandardAndAnioSemanaOrderByNumeroSemanaAsc(String standard, int anioSemana);
}
