package exotic.app.planta.service.contabilidad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import exotic.app.planta.model.activos.fijos.ActivoFijo;
import exotic.app.planta.model.activos.fijos.gestion.DepreciacionActivo;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.contabilidad.LineaAsientoContable;
import exotic.app.planta.model.contabilidad.PeriodoContable;
import exotic.app.planta.repo.activos.fijos.ActivoFijoRepo;
import exotic.app.planta.repo.activos.fijos.gestion.DepreciacionActivoRepo;
import exotic.app.planta.repo.contabilidad.AsientoContableRepo;
import exotic.app.planta.repo.contabilidad.LineaAsientoContableRepo;

/**
 * Programador de tareas para la depreciación automática de activos fijos.
 * Se ejecuta al final de cada mes para calcular y registrar la depreciación
 * de todos los activos fijos depreciables.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DepreciacionScheduler {

    private final ActivoFijoRepo activoFijoRepo;
    private final DepreciacionActivoRepo depreciacionRepo;
    private final AsientoContableRepo asientoContableRepo;
    private final LineaAsientoContableRepo lineaAsientoContableRepo;
    private final PeriodoContableService periodoContableService;
    private final Clock applicationClock;

    /**
     * Ejecuta el proceso de depreciación el último día de cada mes a las 23:59.
     * Utiliza la expresión cron "0 59 23 L * ?" donde L representa el último día del mes.
     */
    @Scheduled(cron = "0 59 23 L * ?", zone = "America/Bogota")
    @Transactional
    public void depreciarActivosMesVencido() {
        YearMonth mesActual = YearMonth.now(applicationClock);
        LocalDate fechaDepreciacion = mesActual.atEndOfMonth();

        log.info("Iniciando proceso de depreciación automática para {}", mesActual);

        // Obtener o crear el período contable para el mes actual
        PeriodoContable periodo = periodoContableService.obtenerOCrearPeriodo(mesActual);

        // Obtener todos los activos depreciables
        List<ActivoFijo> activos = activoFijoRepo.findActivosDepreciables();

        log.info("Se encontraron {} activos depreciables", activos.size());

        for (ActivoFijo activo : activos) {
            try {
                // Verificar si ya existe una depreciación para este activo en esta fecha
                if (depreciacionRepo.existsByActivoFijoAndFechaDepreciacion(activo, fechaDepreciacion)) {
                    log.info("El activo {} ya tiene una depreciación registrada para {}", activo.getId(), fechaDepreciacion);
                    continue;
                }

                // Calcular el monto de depreciación según el método configurado
                BigDecimal montoDepreciacion = calcularDepreciacionMensual(activo);

                // Si no hay monto a depreciar, continuar con el siguiente activo
                if (montoDepreciacion.compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("Activo {} no requiere depreciación", activo.getId());
                    continue;
                }

                // Generar asiento contable para la depreciación
                AsientoContable asiento = generarAsientoDepreciacion(activo, montoDepreciacion, fechaDepreciacion, periodo);

                // Registrar la depreciación del activo
                DepreciacionActivo depreciacion = new DepreciacionActivo();
                depreciacion.setActivoFijo(activo);
                depreciacion.setFechaDepreciacion(fechaDepreciacion);
                depreciacion.setMontoDepreciado(montoDepreciacion);
                depreciacion.setMetodoDepreciacion(activo.getMetodoDespreciacion());

                // Calcular el valor en libros actual
                BigDecimal depreciacionAcumulada = calcularDepreciacionAcumulada(activo).add(montoDepreciacion);
                BigDecimal valorLibroActual = activo.getValorAdquisicion().subtract(depreciacionAcumulada);
                depreciacion.setValorLibroActual(valorLibroActual);

                // Asociar el asiento contable
                depreciacion.setAsientoContable(asiento);

                // Guardar el registro de depreciación
                depreciacionRepo.save(depreciacion);

                log.info("Depreciación aplicada al activo {}: {} - Valor en libros: {}", 
                        activo.getId(), montoDepreciacion, valorLibroActual);
            } catch (Exception e) {
                log.error("Error al procesar depreciación para activo {}: {}", activo.getId(), e.getMessage(), e);
                // Continuar con el siguiente activo en caso de error
            }
        }

        log.info("Proceso de depreciación para {} completado exitosamente", mesActual);
    }

    /**
     * Calcula el monto de depreciación mensual para un activo según el método configurado.
     * Soporta los métodos de línea recta (SL) y balance decreciente (DB).
     * 
     * @param activo El activo a depreciar
     * @return El monto de depreciación calculado
     */
    private BigDecimal calcularDepreciacionMensual(ActivoFijo activo) {
        String metodo = activo.getMetodoDespreciacion();
        BigDecimal valorResidual = activo.getValorResidual() != null ? 
                activo.getValorResidual() : BigDecimal.ZERO;
        BigDecimal valorDepreciable = activo.getValorAdquisicion().subtract(valorResidual);
        BigDecimal depreciacionAcumulada = calcularDepreciacionAcumulada(activo);
        BigDecimal valorLibroActual = activo.getValorAdquisicion().subtract(depreciacionAcumulada);

        // No depreciar más allá del valor residual
        if (valorLibroActual.compareTo(valorResidual) <= 0) {
            return BigDecimal.ZERO;
        }

        // Método de línea recta (SL)
        if (metodo == null || metodo.equals("SL") || metodo.equals("LINEAL")) {
            return calcularDepreciacionLineaRecta(activo, valorDepreciable);
        }
        // Método de balance decreciente (DB)
        else if (metodo.equals("DB") || metodo.equals("SUMA_DIGITOS")) {
            return calcularDepreciacionBalanceDecreciente(activo, valorLibroActual, valorResidual);
        }
        // Método por defecto (línea recta)
        else {
            log.warn("Método de depreciación no reconocido: {}. Usando línea recta por defecto.", metodo);
            return calcularDepreciacionLineaRecta(activo, valorDepreciable);
        }
    }

    /**
     * Calcula la depreciación usando el método de línea recta.
     * 
     * @param activo El activo a depreciar
     * @param valorDepreciable El valor depreciable (valor adquisición - valor residual)
     * @return El monto de depreciación mensual
     */
    private BigDecimal calcularDepreciacionLineaRecta(ActivoFijo activo, BigDecimal valorDepreciable) {
        // Depreciación mensual = Valor depreciable / Vida útil en meses
        return valorDepreciable.divide(
                BigDecimal.valueOf(activo.getVidaUtilMeses()), 
                2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula la depreciación usando el método de balance decreciente.
     * 
     * @param activo El activo a depreciar
     * @param valorLibroActual El valor en libros actual
     * @param valorResidual El valor residual
     * @return El monto de depreciación mensual
     */
    private BigDecimal calcularDepreciacionBalanceDecreciente(ActivoFijo activo, BigDecimal valorLibroActual, BigDecimal valorResidual) {
        // Tasa de depreciación anual (por defecto 2x la tasa de línea recta = 200%)
        double tasaAnual = 2.0 / activo.getVidaUtilMeses() * 12;

        // Depreciación mensual = Valor libro actual * Tasa anual / 12
        BigDecimal depreciacionMensual = valorLibroActual
                .multiply(BigDecimal.valueOf(tasaAnual))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // No depreciar por debajo del valor residual
        BigDecimal montoRestante = valorLibroActual.subtract(valorResidual);
        return depreciacionMensual.min(montoRestante);
    }

    /**
     * Calcula la depreciación acumulada hasta el momento para un activo.
     * 
     * @param activo El activo fijo
     * @return La suma de todas las depreciaciones registradas
     */
    private BigDecimal calcularDepreciacionAcumulada(ActivoFijo activo) {
        if (activo.getDepreciaciones() == null || activo.getDepreciaciones().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return activo.getDepreciaciones().stream()
                .map(DepreciacionActivo::getMontoDepreciado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Genera el asiento contable para la depreciación de un activo.
     * 
     * @param activo El activo fijo
     * @param monto El monto de depreciación
     * @param fechaDepreciacion La fecha de depreciación
     * @param periodo El período contable
     * @return El asiento contable generado
     */
    private AsientoContable generarAsientoDepreciacion(ActivoFijo activo, BigDecimal monto, 
            LocalDate fechaDepreciacion, PeriodoContable periodo) {

        AsientoContable asiento = new AsientoContable();
        asiento.setFecha(fechaDepreciacion.atStartOfDay());
        asiento.setDescripcion("Depreciación mensual - " + activo.getNombre());
        asiento.setModulo("ACTIVOS_FIJOS");
        asiento.setDocumentoOrigen(activo.getId());
        asiento.setEstado(AsientoContable.EstadoAsiento.PUBLICADO);
        asiento.setPeriodoContable(periodo);

        // Guardar el asiento contable primero para obtener su ID
        asiento = asientoContableRepo.save(asiento);

        // Crear líneas del asiento según el tipo de activo
        List<LineaAsientoContable> lineas = new ArrayList<>();

        // Determinar las cuentas contables según el tipo de activo
        String cuentaGasto;
        String cuentaDepreciacionAcumulada;

        switch (activo.getTipoActivo()) {
            case PRODUCCION:
                cuentaGasto = "6134"; // Gasto depreciación maquinaria y equipo
                cuentaDepreciacionAcumulada = "1592"; // Depreciación acumulada maquinaria y equipo
                break;
            case MOBILIARIO:
                cuentaGasto = "6136"; // Gasto depreciación muebles y enseres
                cuentaDepreciacionAcumulada = "1596"; // Depreciación acumulada muebles y enseres
                break;
            case EQUIPO:
                cuentaGasto = "6135"; // Gasto depreciación equipo de oficina
                cuentaDepreciacionAcumulada = "1594"; // Depreciación acumulada equipo de oficina
                break;
            default:
                cuentaGasto = "6139"; // Gasto depreciación otros activos
                cuentaDepreciacionAcumulada = "1599"; // Depreciación acumulada otros activos
        }

        // Línea de débito (gasto depreciación)
        LineaAsientoContable lineaDebito = new LineaAsientoContable();
        lineaDebito.setAsientoContable(asiento);
        lineaDebito.setCuentaCodigo(cuentaGasto);
        lineaDebito.setDebito(monto);
        lineaDebito.setCredito(BigDecimal.ZERO);
        lineaDebito.setDescripcion("Gasto depreciación - " + activo.getNombre());
        lineaAsientoContableRepo.save(lineaDebito);

        // Línea de crédito (depreciación acumulada)
        LineaAsientoContable lineaCredito = new LineaAsientoContable();
        lineaCredito.setAsientoContable(asiento);
        lineaCredito.setCuentaCodigo(cuentaDepreciacionAcumulada);
        lineaCredito.setDebito(BigDecimal.ZERO);
        lineaCredito.setCredito(monto);
        lineaCredito.setDescripcion("Depreciación acumulada - " + activo.getNombre());
        lineaAsientoContableRepo.save(lineaCredito);

        // Retornar el asiento
        return asiento;
    }
}
