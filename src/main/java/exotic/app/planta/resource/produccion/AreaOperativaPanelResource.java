package exotic.app.planta.resource.produccion;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.dto.ErrorResponse;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.produccion.dto.MpsSemanalOrdenProduccionListItemDTO;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.AreaOperativaPanelDetalleService;
import exotic.app.planta.service.produccion.AreaOperativaPanelDetalleService.AreaOperativaOrdenDetalleDTO;
import exotic.app.planta.resource.produccion.exceptions.MpsSemanalNotFoundException;
import exotic.app.planta.service.produccion.MasterProductionScheduleDraftService;
import exotic.app.planta.service.produccion.MasterProductionScheduleOrderGenerationService;
import exotic.app.planta.service.users.UserOperationalCompatibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/area-operativa-panel")
@RequiredArgsConstructor
@Slf4j
public class AreaOperativaPanelResource {

    private final AreaOperativaPanelDetalleService areaOperativaPanelDetalleService;
    private final MasterProductionScheduleDraftService masterProductionScheduleDraftService;
    private final MasterProductionScheduleOrderGenerationService masterProductionScheduleOrderGenerationService;
    private final UserOperationalCompatibilityService userOperationalCompatibilityService;
    private final UserRepository userRepository;

    @GetMapping("/ordenes/{ordenId}/detalle-operativo")
    public ResponseEntity<?> getDetalleOperativoOrden(
            Authentication authentication,
            @PathVariable int ordenId
    ) {
        User user = getCurrentUser(authentication);

        try {
            AreaOperativaOrdenDetalleDTO detalle = areaOperativaPanelDetalleService.getDetalleOperativoOrden(
                    ordenId,
                    user.getId()
            );
            return ResponseEntity.ok(detalle);
        } catch (AccessDeniedException e) {
            log.warn("Acceso denegado al detalle operativo de orden {} para user {}: {}", ordenId, user.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Acceso denegado", e.getMessage()));
        } catch (NoSuchElementException e) {
            log.warn("Detalle operativo no encontrado para orden {}: {}", ordenId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Detalle no encontrado", e.getMessage()));
        }
    }

    @GetMapping("/mps-semanal/actual")
    public ResponseEntity<?> getMpsSemanalActual(Authentication authentication) {
        User user = getCurrentUser(authentication);
        LocalDate weekStartDate = getCurrentIsoWeekStartDate();
        return getMpsSemanalOperativo(
                user,
                weekStartDate,
                "MPS operativo actual",
                "El MPS de la semana actual aun no esta aprobado para consulta operativa."
        );
    }

    @GetMapping("/mps-semanal")
    public ResponseEntity<?> getMpsSemanalPorSemana(
            Authentication authentication,
            @RequestParam LocalDate weekStartDate
    ) {
        User user = getCurrentUser(authentication);
        return getMpsSemanalOperativo(
                user,
                weekStartDate,
                "MPS operativo solicitado",
                "El MPS de la semana solicitada aun no esta aprobado para consulta operativa."
        );
    }

    @GetMapping("/mps-semanal/actual/odps")
    public ResponseEntity<?> getOdpsMpsSemanalActual(Authentication authentication) {
        User user = getCurrentUser(authentication);
        LocalDate weekStartDate = getCurrentIsoWeekStartDate();
        return getOdpsMpsSemanalOperativo(
                user,
                weekStartDate,
                "ODPs del MPS operativo actual",
                "El MPS de la semana actual aun no esta aprobado para consulta operativa."
        );
    }

    @GetMapping("/mps-semanal/odps")
    public ResponseEntity<?> getOdpsMpsSemanalPorSemana(
            Authentication authentication,
            @RequestParam LocalDate weekStartDate
    ) {
        User user = getCurrentUser(authentication);
        return getOdpsMpsSemanalOperativo(
                user,
                weekStartDate,
                "ODPs del MPS operativo solicitado",
                "El MPS de la semana solicitada aun no esta aprobado para consulta operativa."
        );
    }

    private ResponseEntity<?> getMpsSemanalOperativo(
            User user,
            LocalDate weekStartDate,
            String logLabel,
            String unavailableMessage
    ) {
        try {
            assertAreaResponsable(user);
            MpsSemanalDraftDTO mps = masterProductionScheduleDraftService.getByWeekStartDate(weekStartDate);
            if (mps.getEstado() != EstadoMpsSemanal.APROBADO && mps.getEstado() != EstadoMpsSemanal.CERRADO) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse(
                                "MPS no disponible",
                                unavailableMessage
                        ));
            }

            return ResponseEntity.ok(mps);
        } catch (AccessDeniedException e) {
            log.warn("Acceso denegado a {} para user {}: {}", logLabel, user.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Acceso denegado", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            log.warn("{} no encontrado para semana {}: {}", logLabel, weekStartDate, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("MPS no encontrado", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida de {} para semana {}: {}", logLabel, weekStartDate, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Solicitud invalida", e.getMessage()));
        }
    }

    private ResponseEntity<?> getOdpsMpsSemanalOperativo(
            User user,
            LocalDate weekStartDate,
            String logLabel,
            String unavailableMessage
    ) {
        try {
            assertAreaResponsable(user);
            MpsSemanalDraftDTO mps = masterProductionScheduleDraftService.getByWeekStartDate(weekStartDate);
            if (mps.getEstado() != EstadoMpsSemanal.APROBADO && mps.getEstado() != EstadoMpsSemanal.CERRADO) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse(
                                "MPS no disponible",
                                unavailableMessage
                        ));
            }

            List<MpsSemanalOrdenProduccionListItemDTO> ordenes =
                    masterProductionScheduleOrderGenerationService.getOrdenesGeneradasPorSemana(weekStartDate);
            return ResponseEntity.ok(ordenes);
        } catch (AccessDeniedException e) {
            log.warn("Acceso denegado a {} para user {}: {}", logLabel, user.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Acceso denegado", e.getMessage()));
        } catch (MpsSemanalNotFoundException e) {
            log.warn("{} no encontradas para semana {}: {}", logLabel, weekStartDate, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("MPS no encontrado", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud invalida de {} para semana {}: {}", logLabel, weekStartDate, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Solicitud invalida", e.getMessage()));
        }
    }

    private void assertAreaResponsable(User user) {
        if (!userOperationalCompatibilityService.isAreaResponsable(user.getId())) {
            throw new AccessDeniedException("Solo usuarios responsables de area pueden consultar el MPS operativo.");
        }
    }

    private LocalDate getCurrentIsoWeekStartDate() {
        return AppTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }
}
