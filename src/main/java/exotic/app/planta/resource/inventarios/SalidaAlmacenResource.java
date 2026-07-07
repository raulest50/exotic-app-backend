package exotic.app.planta.resource.inventarios;


import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.inventarios.dto.*;
import exotic.app.planta.model.produccion.dto.MpsSemanalDraftDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.UserAccessEvaluator;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.inventarios.DispensacionV2MpsService;
import exotic.app.planta.service.inventarios.DispensacionV2WorkflowService;
import exotic.app.planta.service.inventarios.SalidaAlmacenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/salidas_almacen")
@RequiredArgsConstructor
@Slf4j
public class SalidaAlmacenResource {

    private final SalidaAlmacenService salidaAlmacenService;
    private final DispensacionV2MpsService dispensacionV2MpsService;
    private final DispensacionV2WorkflowService dispensacionV2WorkflowService;
    private final UserRepository userRepository;

    @GetMapping("/dispensacion-v2/mps-semanal")
    public ResponseEntity<MpsSemanalDraftDTO> getDispensacionV2MpsSemanal(
            Authentication authentication,
            @RequestParam LocalDate weekStartDate,
            @RequestParam int areaId
    ) {
        User currentUser = getCurrentUser(authentication);
        requireDispensacionV2Access(currentUser);
        MpsSemanalDraftDTO response = dispensacionV2MpsService.getMpsSemanalFiltradoPorArea(weekStartDate, areaId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispensacion-v2/preparacion")
    public ResponseEntity<DispensacionV2PreparacionResponseDTO> prepararDispensacionV2(
            Authentication authentication,
            @RequestBody DispensacionV2PreparacionRequestDTO request
    ) {
        User currentUser = getCurrentUser(authentication);
        requireDispensacionV2Access(currentUser);
        return ResponseEntity.ok(dispensacionV2WorkflowService.preparar(request));
    }

    @PostMapping("/dispensacion-v2/asignacion-lotes")
    public ResponseEntity<DispensacionV2PreparacionResponseDTO> asignarLotesDispensacionV2(
            Authentication authentication,
            @RequestBody DispensacionV2AsignacionLotesRequestDTO request
    ) {
        User currentUser = getCurrentUser(authentication);
        requireDispensacionV2Access(currentUser);
        return ResponseEntity.ok(dispensacionV2WorkflowService.asignarLotes(request));
    }

    @GetMapping("/dispensacion-v2/materiales/{productoId}/lotes-disponibles")
    public ResponseEntity<LoteDisponiblePageResponseDTO> getLotesDisponiblesDispensacionV2(
            Authentication authentication,
            @PathVariable String productoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        User currentUser = getCurrentUser(authentication);
        requireDispensacionV2Access(currentUser);
        return ResponseEntity.ok(dispensacionV2WorkflowService.getLotesDisponiblesV2(productoId, page, size));
    }

    /**
     * Endpoint para obtener recomendaciones de lotes para dispensación.
     * Recibe un producto y cantidad, y devuelve los lotes recomendados para tomar.
     */
    @PostMapping("/recomendar-lotes")
    public ResponseEntity<DispensacionNoPlanificadaDTO> recomendarLotes(
            @RequestBody RecomendacionLotesRequestDTO requestDTO) {
        DispensacionNoPlanificadaDTO recomendacion = salidaAlmacenService.recomendarLotesParaDispensacion(
                requestDTO.getProductoId(), requestDTO.getCantidad());
        return ResponseEntity.ok(recomendacion);
    }

    /**
     * Endpoint para obtener los lotes disponibles de un producto específico.
     * Devuelve información detallada de cada lote, incluyendo fecha de vencimiento y cantidad disponible.
     *
     * @param productoId ID del producto para consultar sus lotes
     * @return Información de lotes disponibles con sus cantidades
     */
    @GetMapping("/lotes-disponibles")
    public ResponseEntity<LoteDisponibleResponseDTO> getLotesDisponibles(
            @RequestParam String productoId) {
        LoteDisponibleResponseDTO lotesDisponibles = salidaAlmacenService.getLotesDisponiblesByProductoId(productoId);
        return ResponseEntity.ok(lotesDisponibles);
    }

    /**
     * Endpoint para obtener los lotes disponibles de un producto específico con paginación.
     * Devuelve información detallada de cada lote, incluyendo fecha de vencimiento y cantidad disponible.
     * Solo retorna lotes con stock disponible mayor a 0.
     *
     * @param productoId ID del producto para consultar sus lotes
     * @param page Número de página (base 0, por defecto 0)
     * @param size Tamaño de página (por defecto 10)
     * @return Información paginada de lotes disponibles con sus cantidades
     */
    @GetMapping("/lotes-disponibles-paginados")
    public ResponseEntity<LoteDisponiblePageResponseDTO> getLotesDisponiblesPaginados(
            @RequestParam String productoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        LoteDisponiblePageResponseDTO lotesDisponibles = salidaAlmacenService.getLotesDisponiblesByProductoIdPaginated(productoId, page, size);
        return ResponseEntity.ok(lotesDisponibles);
    }

    /**
     * Obtiene la lista completa desglosada de todos los materiales base necesarios
     * para una orden de producción, descomponiendo recursivamente los semiterminados.
     * Incluye también los materiales de empaque del CasePack.
     * 
     * @param ordenProduccionId ID de la orden de producción
     * @return DTO con insumos de receta e insumos de empaque
     */
    @GetMapping("/orden-produccion/{ordenProduccionId}/insumos-desglosados")
    public ResponseEntity<InsumosDesglosadosResponseDTO> getInsumosDesglosados(
            @PathVariable int ordenProduccionId) {
        InsumosDesglosadosResponseDTO response = salidaAlmacenService.getInsumosDesglosados(ordenProduccionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint combinado (histórico):
     * Se crea para evitar el aplanado de insumos y permitir dispensaciones parciales
     * con validación de histórico en una sola llamada. Retorna receta no aplanada,
     * materiales de empaque base y el historial de dispensaciones con movimientos.
     */
    @GetMapping("/orden-produccion/{ordenProduccionId}/dispensacion-resumen")
    public ResponseEntity<DispensacionResumenDTO> getDispensacionResumen(
            @PathVariable int ordenProduccionId) {
        DispensacionResumenDTO response = salidaAlmacenService.getDispensacionResumen(ordenProduccionId);
        return ResponseEntity.ok(response);
    }


    /**
     * Endpoint para crear una dispensación asociada a una orden de producción.
     * Este endpoint maneja la salida de materiales del almacén para ejecutar órdenes de producción.
     *
     * @param dispensacionDTO Datos de la dispensación a crear
     * @return La transacción de almacén creada
     */
    @PostMapping("/dispensacion")
    public ResponseEntity<?> createDispensacion(
            Authentication authentication,
            @RequestBody DispensacionDTO dispensacionDTO) {
        User currentUser = getCurrentUser(authentication);
        TransaccionAlmacen transaccion = salidaAlmacenService.createDispensacion(dispensacionDTO, currentUser.getId());
        return ResponseEntity.created(java.net.URI.create("/movimientos/transaccion/" + transaccion.getTransaccionId()))
                .body(transaccion);
    }

    /**
     * Crea una dispensación de reposición justificada por averías reportadas en una orden de producción.
     * Valida que las cantidades no excedan lo pendiente de reposición por avería.
     */
    @PostMapping("/dispensacion-reposicion-averia")
    public ResponseEntity<?> createDispensacionReposicionAveria(@RequestBody DispensacionDTO dispensacionDTO) {
        TransaccionAlmacen transaccion = salidaAlmacenService.createDispensacionReposicionAveria(dispensacionDTO);
        return ResponseEntity.created(java.net.URI.create("/movimientos/transaccion/" + transaccion.getTransaccionId()))
                .body(transaccion);
    }

    /**
     * Endpoint para crear una dispensación no planificada (sin orden de producción).
     * Verifica la directiva "Permitir Consumo No Planificado" antes de permitir la operación.
     */
    @PostMapping("/dispensacion-no-planificada")
    public ResponseEntity<?> createDispensacionNoPlanificada(@RequestBody DispensacionNoPlanificadaDTO dispensacionDTO) {
        TransaccionAlmacen transaccion = salidaAlmacenService.createDispensacionNoPlanificada(dispensacionDTO);
        return ResponseEntity.created(java.net.URI.create("/movimientos/transaccion/" + transaccion.getTransaccionId()))
                .body(transaccion);
    }


    /**
     * Endpoint para obtener recomendaciones de lotes para múltiples productos.
     * Recibe una lista de productos y cantidades, y devuelve los lotes recomendados para todos ellos.
     */
    @PostMapping("/recomendar-lotes-multiple")
    public ResponseEntity<DispensacionNoPlanificadaDTO> recomendarLotesMultiple(
            @RequestBody RecomendacionLotesMultipleRequestDTO requestDTO) {
        DispensacionNoPlanificadaDTO recomendacion = salidaAlmacenService.recomendarLotesParaDispensacionMultiple(
                requestDTO.getItems());
        return ResponseEntity.ok(recomendacion);
    }

    /**
     * Endpoint para buscar dispensaciones con filtros flexibles.
     * Permite filtrar por ID de transacción, ID de orden de producción, lote de producción,
     * producto terminado, y fechas (rango o específica).
     * Retorna DTOs para evitar problemas de serialización JSON con relaciones circulares.
     *
     * @param filtro DTO con los criterios de búsqueda
     * @return Página de DTOs de transacciones que cumplen con los filtros
     */
    @PostMapping("/historial_dispensacion_filter")
    public ResponseEntity<Page<TransaccionAlmacenResponseDTO>> buscarDispensacionesFiltradas(
            @RequestBody FiltroHistDispensacionDTO filtro) {
        Page<TransaccionAlmacenResponseDTO> resultados = salidaAlmacenService.buscarDispensacionesFiltradasDTO(filtro);
        return ResponseEntity.ok(resultados);
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    private void requireDispensacionV2Access(User user) {
        if (isMasterLike(user.getUsername())) {
            return;
        }

        boolean hasTabAccess = UserAccessEvaluator
                .tabNivel(user, ModuloSistema.TRANSACCIONES_ALMACEN, "DISPENSACION_V2")
                .orElse(0) >= 1;

        if (!hasTabAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para Dispensacion v2.");
        }
    }

    private boolean isMasterLike(String username) {
        if (username == null) return false;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return "master".equals(normalized) || "super_master".equals(normalized);
    }


}
