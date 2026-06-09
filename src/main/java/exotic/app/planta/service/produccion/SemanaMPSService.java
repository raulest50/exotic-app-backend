package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.SemanaMPS;
import exotic.app.planta.model.produccion.dto.SemanaMPSDTO;
import exotic.app.planta.repo.produccion.MasterProductionScheduleSemanalRepo;
import exotic.app.planta.repo.produccion.SemanaMPSRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class SemanaMPSService {

    private static final String STANDARD = SemanaMPS.STANDARD_ISO_8601_MONDAY_SATURDAY;

    private final SemanaMPSRepo semanaMPSRepo;
    private final MasterProductionScheduleSemanalRepo masterProductionScheduleSemanalRepo;

    public SemanaMPS getOrCreateByStartDate(LocalDate weekStartDate) {
        log.debug("[MPS_SEMANAL] service getOrCreateSemana begin weekStartDate={}", weekStartDate);
        WeekDefinition definition = buildDefinitionFromStartDate(weekStartDate);
        return semanaMPSRepo.findByStandardAndStartDate(STANDARD, definition.startDate())
                .orElseGet(() -> saveNewSemana(definition));
    }

    @Transactional(readOnly = true)
    public List<SemanaMPSDTO> listIsoWeeksForYear(int anioSemana) {
        log.debug("[MPS_SEMANAL] service listIsoWeeksForYear begin anioSemana={}", anioSemana);
        validateAnioSemana(anioSemana);

        int maxWeeks = getMaxIsoWeeksInYear(anioSemana);
        List<SemanaMPS> existingWeeks = semanaMPSRepo.findAllByStandardAndAnioSemanaOrderByNumeroSemanaAsc(STANDARD, anioSemana);
        Map<Integer, SemanaMPS> existingByWeekNumber = existingWeeks.stream()
                .collect(Collectors.toMap(SemanaMPS::getNumeroSemana, Function.identity(), (left, ignored) -> left));

        LocalDate firstStartDate = startOfIsoWeek(anioSemana, 1);
        LocalDate lastStartDate = startOfIsoWeek(anioSemana, maxWeeks);
        Map<LocalDate, MasterProductionScheduleSemanal> mpsByStartDate = masterProductionScheduleSemanalRepo
                .findAllByWeekStartDateBetween(firstStartDate, lastStartDate)
                .stream()
                .collect(Collectors.toMap(MasterProductionScheduleSemanal::getWeekStartDate, Function.identity(), (left, ignored) -> left));

        List<SemanaMPSDTO> response = IntStream.rangeClosed(1, maxWeeks)
                .mapToObj(weekNumber -> {
                    SemanaMPS semana = existingByWeekNumber.getOrDefault(
                            weekNumber,
                            buildTransientSemana(buildDefinitionFromAnioNumero(anioSemana, weekNumber))
                    );
                    return SemanaMPSDTO.fromSemanaAndMps(semana, mpsByStartDate.get(semana.getStartDate()));
                })
                .toList();
        log.info(
                "[MPS_SEMANAL] service listIsoWeeksForYear success anioSemana={} maxWeeks={} persistedWeeks={} mpsWeeks={}",
                anioSemana,
                maxWeeks,
                existingWeeks.size(),
                mpsByStartDate.size()
        );
        return response;
    }

    private SemanaMPS saveNewSemana(WeekDefinition definition) {
        SemanaMPS semana = buildTransientSemana(definition);
        try {
            SemanaMPS saved = semanaMPSRepo.save(semana);
            log.info("[MPS_SEMANAL] service saveNewSemana success semanaMpsId={} codigo={} startDate={} endDate={}", saved.getId(), saved.getCodigo(), saved.getStartDate(), saved.getEndDate());
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("[MPS_SEMANAL] service saveNewSemana duplicate_detected codigo={} startDate={} message={}", definition.codigo(), definition.startDate(), e.getMessage());
            return semanaMPSRepo.findByStandardAndStartDate(STANDARD, definition.startDate())
                    .orElseThrow(() -> e);
        }
    }

    private SemanaMPS buildTransientSemana(WeekDefinition definition) {
        SemanaMPS semana = new SemanaMPS();
        semana.setCodigo(definition.codigo());
        semana.setAnioSemana(definition.anioSemana());
        semana.setNumeroSemana(definition.numeroSemana());
        semana.setStartDate(definition.startDate());
        semana.setEndDate(definition.endDate());
        semana.setStandard(STANDARD);
        return semana;
    }

    private WeekDefinition buildDefinitionFromStartDate(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate es obligatorio.");
        }
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate debe corresponder a un lunes.");
        }

        int anioSemana = weekStartDate.get(IsoFields.WEEK_BASED_YEAR);
        int numeroSemana = weekStartDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return buildDefinitionFromAnioNumero(anioSemana, numeroSemana);
    }

    private WeekDefinition buildDefinitionFromAnioNumero(int anioSemana, int numeroSemana) {
        validateAnioSemana(anioSemana);
        int maxWeeks = getMaxIsoWeeksInYear(anioSemana);
        if (numeroSemana < 1 || numeroSemana > maxWeeks) {
            throw new IllegalArgumentException("numeroSemana no es valido para el año ISO indicado.");
        }

        LocalDate startDate = startOfIsoWeek(anioSemana, numeroSemana);
        LocalDate endDate = startDate.plusDays(5);
        String codigo = "S" + String.format("%02d", numeroSemana) + "-" + anioSemana;
        return new WeekDefinition(codigo, anioSemana, numeroSemana, startDate, endDate);
    }

    private LocalDate startOfIsoWeek(int anioSemana, int numeroSemana) {
        return LocalDate.of(anioSemana, 1, 4)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, numeroSemana)
                .with(ChronoField.DAY_OF_WEEK, DayOfWeek.MONDAY.getValue());
    }

    private int getMaxIsoWeeksInYear(int anioSemana) {
        validateAnioSemana(anioSemana);
        return LocalDate.of(anioSemana, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private void validateAnioSemana(int anioSemana) {
        if (anioSemana < 1900 || anioSemana > 2200) {
            throw new IllegalArgumentException("anioSemana debe estar entre 1900 y 2200.");
        }
    }

    private record WeekDefinition(
            String codigo,
            int anioSemana,
            int numeroSemana,
            LocalDate startDate,
            LocalDate endDate
    ) {}
}
