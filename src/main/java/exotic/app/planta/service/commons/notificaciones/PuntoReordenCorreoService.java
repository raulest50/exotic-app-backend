package exotic.app.planta.service.commons.notificaciones;

import exotic.app.planta.config.TimeZoneConfig;
import exotic.app.planta.model.commons.notificaciones.MaterialEnPuntoReordenDTO;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenCorreoData;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenCorreoData.Identidad;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenCorreoData.MaterialConOcmFila;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenCorreoData.MaterialFila;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenCorreoData.OcmFila;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenCorreoData.SeguimientoFila;
import exotic.app.planta.model.commons.notificaciones.PuntoReordenEvaluacionResult;
import exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.service.empresa.EmpresaIdentidadLegalService;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prepara el snapshot del correo sin modificar la evaluacion compartida con la campana. */
@Service
@RequiredArgsConstructor
public class PuntoReordenCorreoService {

    private static final int ESTADO_OCM_PENDIENTE_INGRESO = 2;
    private static final ZoneId BOGOTA = ZoneId.of(TimeZoneConfig.APP_TIME_ZONE_ID);

    private final PuntoReordenEvaluacionService puntoReordenEvaluacionService;
    private final EmpresaIdentidadLegalService empresaIdentidadLegalService;
    private final MasterDirectiveService masterDirectiveService;
    private final ItemOrdenCompraRepo itemOrdenCompraRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final Clock applicationClock;

    @Transactional(readOnly = true)
    public PuntoReordenCorreoData preparar() {
        LocalDate fechaCorte = LocalDate.now(applicationClock.withZone(BOGOTA));
        PuntoReordenEvaluacionResult evaluacion = puntoReordenEvaluacionService.evaluar();
        Identidad identidad = copiarIdentidad(empresaIdentidadLegalService.getVigente());
        List<MaterialFila> pendientesOrdenar = evaluacion.pendientesOrdenar().stream()
                .map(PuntoReordenCorreoService::copiarMaterial)
                .toList();
        List<MaterialConOcmFila> pendientesIngresoAlmacen = evaluacion.pendientesIngresoAlmacen().stream()
                .map(material -> new MaterialConOcmFila(
                        copiarMaterial(material),
                        material.getOcmsPendientesIngreso().stream()
                                .map(ocm -> new OcmFila(ocm.getOrdenCompraId(), ocm.getFechaEmision()))
                                .toList()))
                .toList();
        List<MaterialFila> sinPuntoReorden = evaluacion.sinPuntoReorden().stream()
                .map(PuntoReordenCorreoService::copiarMaterial)
                .toList();
        int porcentaje = masterDirectiveService.getPuntoReordenSeguimientoPorcentaje();
        int minimoLeadTimeDias = masterDirectiveService.getPuntoReordenSeguimientoMinimoLeadTimeDias();
        ClasificacionSeguimiento clasificacion = clasificarSeguimiento(
                pendientesIngresoAlmacen, fechaCorte, porcentaje, minimoLeadTimeDias);

        return new PuntoReordenCorreoData(
                fechaCorte, identidad, pendientesOrdenar, pendientesIngresoAlmacen, sinPuntoReorden,
                clasificacion.seguimiento(), clasificacion.vencidas(), clasificacion.sinDatos(),
                porcentaje, minimoLeadTimeDias);
    }

    private ClasificacionSeguimiento clasificarSeguimiento(
            List<MaterialConOcmFila> materiales,
            LocalDate fechaCorte,
            int porcentaje,
            int minimoLeadTimeDias
    ) {
        Map<OcmMaterialKey, MaterialFila> materialesPorPar = new LinkedHashMap<>();
        Set<String> productoIds = new LinkedHashSet<>();
        Set<Integer> ocmIds = new LinkedHashSet<>();
        for (MaterialConOcmFila fila : materiales) {
            for (OcmFila ocm : fila.ocms()) {
                materialesPorPar.putIfAbsent(
                        new OcmMaterialKey(ocm.ordenCompraId(), fila.material().productoId()),
                        fila.material());
                productoIds.add(fila.material().productoId());
                ocmIds.add(ocm.ordenCompraId());
            }
        }
        if (materialesPorPar.isEmpty()) {
            return new ClasificacionSeguimiento(List.of(), List.of(), 0L);
        }

        List<ItemOrdenCompraRepo.PuntoReordenSeguimientoProjection> filas =
                itemOrdenCompraRepo.findPuntoReordenSeguimientoRows(
                        productoIds, ocmIds, ESTADO_OCM_PENDIENTE_INGRESO);
        Map<OcmMaterialKey, BigDecimal> recibidoPorPar = new HashMap<>();
        for (Movimiento recepcion : transaccionAlmacenRepo.findRecepcionesPorCausanteYEntidades(
                        Movimiento.Almacen.GENERAL,
                        Movimiento.TipoMovimiento.COMPRA,
                        TransaccionAlmacen.TipoEntidadCausante.OCM,
                        ocmIds)) {
            recibidoPorPar.merge(
                    new OcmMaterialKey(recepcion.getTransaccionAlmacen().getIdEntidadCausante(),
                            recepcion.getProducto().getProductoId()),
                    BigDecimal.valueOf(recepcion.getCantidad()), BigDecimal::add);
        }

        List<SeguimientoFila> seguimiento = new ArrayList<>();
        List<SeguimientoFila> vencidas = new ArrayList<>();
        long sinDatos = 0L;
        for (ItemOrdenCompraRepo.PuntoReordenSeguimientoProjection fila : filas) {
            OcmMaterialKey key = new OcmMaterialKey(fila.getOrdenCompraId(), fila.getProductoId());
            MaterialFila material = materialesPorPar.get(key);
            if (material == null) {
                continue;
            }
            BigDecimal cantidadPendiente = BigDecimal.valueOf(fila.getCantidadOrdenada())
                    .subtract(recibidoPorPar.getOrDefault(key, BigDecimal.ZERO));
            if (cantidadPendiente.signum() <= 0) {
                continue;
            }

            Integer leadTimeDias = fila.getLeadTimeNegociado();
            LocalDate fechaEnvio = fila.getFechaEnvioProveedor() == null
                    ? null : fila.getFechaEnvioProveedor().toLocalDate();
            if (fechaEnvio == null || fechaEnvio.isAfter(fechaCorte)
                    || leadTimeDias == null || leadTimeDias < 0) {
                sinDatos++;
                continue;
            }

            long diasTranscurridos = ChronoUnit.DAYS.between(fechaEnvio, fechaCorte);
            long umbralSeguimiento = ((long) porcentaje * leadTimeDias + 99L) / 100L;
            SeguimientoFila resultado = new SeguimientoFila(
                    material, fila.getOrdenCompraId(), fila.getProveedor(), fechaEnvio,
                    fechaEnvio.plusDays(leadTimeDias), leadTimeDias, diasTranscurridos,
                    cantidadPendiente.doubleValue());
            if (diasTranscurridos > leadTimeDias) {
                vencidas.add(resultado);
            } else if (leadTimeDias >= minimoLeadTimeDias && diasTranscurridos >= umbralSeguimiento) {
                seguimiento.add(resultado);
            }
        }
        return new ClasificacionSeguimiento(seguimiento, vencidas, sinDatos);
    }

    private static MaterialFila copiarMaterial(MaterialEnPuntoReordenDTO material) {
        return new MaterialFila(
                material.getProductoId(), material.getNombre(), material.getTipoMaterialLabel(),
                material.getStockActual(), material.getPuntoReorden(), material.getTipoUnidades());
    }

    private static Identidad copiarIdentidad(EmpresaIdentidadLegalVersion identidad) {
        if (identidad == null || identidad.getNombreComercial() == null
                || identidad.getNombreComercial().isBlank()) {
            throw new IllegalStateException("La identidad legal vigente no tiene un nombre comercial válido.");
        }
        return new Identidad(
                identidad.getNombreComercial(), identidad.getRazonSocial(),
                identidad.getTelefonoPrincipal(), identidad.getEmailPrincipal());
    }

    private record OcmMaterialKey(int ordenCompraId, String productoId) {}

    private record ClasificacionSeguimiento(
            List<SeguimientoFila> seguimiento, List<SeguimientoFila> vencidas, long sinDatos
    ) {}
}
