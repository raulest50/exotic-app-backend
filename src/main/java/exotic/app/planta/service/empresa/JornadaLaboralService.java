package exotic.app.planta.service.empresa;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.empresa.JornadaLaboralBloque;
import exotic.app.planta.model.empresa.JornadaLaboralVersion;
import exotic.app.planta.model.empresa.dto.JornadaLaboralBloqueRequest;
import exotic.app.planta.model.empresa.dto.JornadaLaboralDiaRequest;
import exotic.app.planta.model.empresa.dto.JornadaLaboralVersionRequest;
import exotic.app.planta.model.empresa.dto.JornadaLaboralVersionResponse;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JornadaLaboralService {

    private static final int MIN_DIA_SEMANA = 1;
    private static final int MAX_DIA_SEMANA = 7;
    private static final int MAX_BLOQUES_POR_DIA = 2;

    private final JornadaLaboralVersionRepo repo;

    @Transactional(readOnly = true)
    public JornadaLaboralVersionResponse getVigente() {
        return repo.findFirstByEstadoOrderByVersionDesc(JornadaLaboralVersion.Estado.VIGENTE)
                .map(JornadaLaboralVersionResponse::fromEntity)
                .orElseThrow(() -> new IllegalStateException("No existe una jornada laboral vigente configurada."));
    }

    @Transactional(readOnly = true)
    public List<JornadaLaboralVersionResponse> getVersiones() {
        return repo.findAllByOrderByVersionDesc().stream()
                .map(JornadaLaboralVersionResponse::fromEntity)
                .toList();
    }

    @Transactional
    public JornadaLaboralVersionResponse crearNuevaVersion(
            JornadaLaboralVersionRequest request,
            String username
    ) {
        List<ValidatedBlock> validatedBlocks = validateRequest(request);
        LocalDateTime now = AppTime.now();

        repo.findByEstadoForUpdate(JornadaLaboralVersion.Estado.VIGENTE)
                .ifPresent(vigente -> {
                    vigente.setEstado(JornadaLaboralVersion.Estado.RETIRADA);
                    vigente.setVigenteHasta(now);
                    repo.save(vigente);
                });

        JornadaLaboralVersion nueva = new JornadaLaboralVersion();
        nueva.setVersion(repo.findMaxVersion() + 1);
        nueva.setEstado(JornadaLaboralVersion.Estado.VIGENTE);
        nueva.setVigenteDesde(now);
        nueva.setCreadoEn(now);
        nueva.setCreadoPor(trim(username));
        nueva.setMotivoCambio(trim(request.getMotivoCambio()));

        for (ValidatedBlock block : validatedBlocks) {
            JornadaLaboralBloque bloque = new JornadaLaboralBloque();
            bloque.setJornadaLaboralVersion(nueva);
            bloque.setDiaSemana(block.diaSemana());
            bloque.setOrden(block.orden());
            bloque.setHoraInicio(block.horaInicio());
            bloque.setHoraFin(block.horaFin());
            nueva.getBloques().add(bloque);
        }

        return JornadaLaboralVersionResponse.fromEntity(repo.save(nueva));
    }

    private List<ValidatedBlock> validateRequest(JornadaLaboralVersionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de jornada laboral no puede ser nula.");
        }
        if (request.getMotivoCambio() == null || request.getMotivoCambio().trim().isBlank()) {
            throw new IllegalArgumentException("Debe informar el motivo del cambio.");
        }
        if (request.getDias() == null || request.getDias().isEmpty()) {
            throw new IllegalArgumentException("Debe informar al menos un dia de jornada laboral.");
        }

        Set<Integer> seenDays = new HashSet<>();
        List<ValidatedBlock> allBlocks = new ArrayList<>();
        for (JornadaLaboralDiaRequest dia : request.getDias()) {
            validateDiaShell(dia, seenDays);
            List<JornadaLaboralBloqueRequest> bloques = dia.getBloques() != null ? dia.getBloques() : List.of();
            boolean laborable = Boolean.TRUE.equals(dia.getLaborable());

            if (!laborable) {
                if (!bloques.isEmpty()) {
                    throw new IllegalArgumentException("Un dia no laborable no puede tener bloques horarios.");
                }
                continue;
            }
            if (bloques.isEmpty()) {
                throw new IllegalArgumentException("Un dia laborable requiere al menos un bloque horario.");
            }
            if (bloques.size() > MAX_BLOQUES_POR_DIA) {
                throw new IllegalArgumentException("Cada dia admite maximo dos bloques horarios.");
            }

            bloques.forEach(bloque -> validateBloqueShape(bloque));
            List<JornadaLaboralBloqueRequest> orderedBlocks = bloques.stream()
                    .sorted(Comparator.comparing(JornadaLaboralBloqueRequest::getHoraInicio))
                    .toList();
            LocalTime previousEnd = null;
            for (int index = 0; index < orderedBlocks.size(); index++) {
                JornadaLaboralBloqueRequest bloque = orderedBlocks.get(index);
                validateBloqueTimeRange(bloque, dia.getDiaSemana(), previousEnd);
                allBlocks.add(new ValidatedBlock(
                        dia.getDiaSemana(),
                        index,
                        bloque.getHoraInicio(),
                        bloque.getHoraFin()
                ));
                previousEnd = bloque.getHoraFin();
            }
        }

        if (allBlocks.isEmpty()) {
            throw new IllegalArgumentException("La jornada laboral requiere al menos un dia laborable.");
        }

        return allBlocks.stream()
                .sorted(Comparator
                        .comparing(ValidatedBlock::diaSemana)
                        .thenComparing(ValidatedBlock::orden))
                .toList();
    }

    private void validateDiaShell(JornadaLaboralDiaRequest dia, Set<Integer> seenDays) {
        if (dia == null) {
            throw new IllegalArgumentException("La configuracion de dia no puede ser nula.");
        }
        if (dia.getDiaSemana() == null
                || dia.getDiaSemana() < MIN_DIA_SEMANA
                || dia.getDiaSemana() > MAX_DIA_SEMANA) {
            throw new IllegalArgumentException("diaSemana debe estar entre 1 y 7.");
        }
        if (!seenDays.add(dia.getDiaSemana())) {
            throw new IllegalArgumentException("No se permite repetir diaSemana en la jornada laboral.");
        }
        if (dia.getLaborable() == null) {
            throw new IllegalArgumentException("Debe indicar si el dia es laborable.");
        }
    }

    private void validateBloqueShape(JornadaLaboralBloqueRequest bloque) {
        if (bloque == null) {
            throw new IllegalArgumentException("El bloque horario no puede ser nulo.");
        }
        if (bloque.getHoraInicio() == null || bloque.getHoraFin() == null) {
            throw new IllegalArgumentException("Cada bloque requiere horaInicio y horaFin.");
        }
    }

    private void validateBloqueTimeRange(
            JornadaLaboralBloqueRequest bloque,
            int diaSemana,
            LocalTime previousEnd
    ) {
        if (!bloque.getHoraInicio().isBefore(bloque.getHoraFin())) {
            throw new IllegalArgumentException("La hora de inicio debe ser anterior a la hora fin.");
        }
        if (previousEnd != null && previousEnd.isAfter(bloque.getHoraInicio())) {
            throw new IllegalArgumentException("Los bloques horarios no pueden solaparse en el dia " + diaSemana + ".");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record ValidatedBlock(
            int diaSemana,
            int orden,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
    }
}
