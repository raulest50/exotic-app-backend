package exotic.app.planta.service.contabilidad;

import exotic.app.planta.model.activos.fijos.gestion.IncorporacionActivoHeader;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.contabilidad.CuentaContableCodigo;
import exotic.app.planta.model.contabilidad.LineaAsientoContable;
import exotic.app.planta.model.contabilidad.dto.search.DTO_SearchIncorporacionActivo;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.repo.activos.fijos.gestion.IncorporacionActivoHeaderRepo;
import exotic.app.planta.repo.contabilidad.AsientoContableRepo;
import exotic.app.planta.repo.contabilidad.CuentaContableRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para gestionar operaciones contables.
 * Encapsula la lógica de creación y validación de asientos contables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContabilidadService {

    private final AsientoContableRepo asientoContableRepo;
    private final CuentaContableRepo cuentaContableRepo;
    private final IncorporacionActivoHeaderRepo incorporacionActivoHeaderRepo;
    private final Clock applicationClock;

    /**
     * Registra un asiento contable para un ingreso de mercancía por OCM
     * 
     * @param transaccion La transacción de almacén
     * @param ocm La orden de compra de materiales
     * @param montoTotal El monto total de la transacción
     * @return El asiento contable creado
     */
    public AsientoContable registrarAsientoIngresoOCM(
            TransaccionAlmacen transaccion, 
            OrdenCompraMateriales ocm,
            BigDecimal montoTotal) {

        // Validar que las cuentas existan
        validarCuentasExisten(
            CuentaContableCodigo.INVENTARIO_MATERIAS_PRIMAS.getCodigo(),
            CuentaContableCodigo.CUENTAS_POR_PAGAR_PROVEEDORES.getCodigo()
        );

        AsientoContable asiento = new AsientoContable();
        asiento.setFecha(LocalDateTime.now(applicationClock));
        asiento.setDescripcion("Ingreso de mercancía por OCM #" + ocm.getOrdenCompraId());
        asiento.setModulo("INVENTARIO");
        asiento.setDocumentoOrigen("OCM-" + ocm.getOrdenCompraId());
        asiento.setEstado(AsientoContable.EstadoAsiento.PUBLICADO);

        List<LineaAsientoContable> lineas = new ArrayList<>();

        // 1. Débito a Inventario Materias Primas
        lineas.add(crearLineaAsiento(
            asiento,
            CuentaContableCodigo.INVENTARIO_MATERIAS_PRIMAS.getCodigo(),
            montoTotal,
            BigDecimal.ZERO,
            "Ingreso de materias primas a inventario"
        ));

        // 2. Crédito a Cuentas por Pagar - Proveedores
        lineas.add(crearLineaAsiento(
            asiento,
            CuentaContableCodigo.CUENTAS_POR_PAGAR_PROVEEDORES.getCodigo(),
            BigDecimal.ZERO,
            montoTotal,
            "Obligación con proveedor " + ocm.getProveedor().getNombre()
        ));

        asiento.setLineas(lineas);

        // Validar que el asiento esté balanceado
        validarCuadreContable(asiento);

        return asientoContableRepo.save(asiento);
    }

    /**
     * Crea una línea de asiento contable
     */
    private LineaAsientoContable crearLineaAsiento(
            AsientoContable asiento, 
            String cuentaCodigo, 
            BigDecimal debito, 
            BigDecimal credito, 
            String descripcion) {
        LineaAsientoContable linea = new LineaAsientoContable();
        linea.setAsientoContable(asiento);
        linea.setCuentaCodigo(cuentaCodigo);
        linea.setDebito(debito);
        linea.setCredito(credito);
        linea.setDescripcion(descripcion);
        return linea;
    }

    /**
     * Valida que las cuentas contables existan en el sistema
     */
    private void validarCuentasExisten(String... codigos) {
        for (String codigo : codigos) {
            if (!cuentaContableRepo.existsById(codigo)) {
                throw new RuntimeException("La cuenta contable con código " + codigo + " no existe");
            }
        }
    }

    /**
     * Registra un asiento contable para un ingreso de producto terminado (BACKFLUSH)
     * 
     * @param transaccion La transacción de almacén
     * @param ordenProduccion La orden de producción
     * @param montoTotal El monto total de la transacción
     * @return El asiento contable creado
     */
    public AsientoContable registrarAsientoBackflush(
            TransaccionAlmacen transaccion, 
            OrdenProduccion ordenProduccion,
            BigDecimal montoTotal) {

        // Validar que las cuentas existan
        validarCuentasExisten(
            CuentaContableCodigo.INVENTARIO_PRODUCTOS_TERMINADOS.getCodigo(),
            CuentaContableCodigo.INVENTARIO_WIP.getCodigo()
        );

        AsientoContable asiento = new AsientoContable();
        asiento.setFecha(LocalDateTime.now(applicationClock));
        asiento.setDescripcion("Ingreso de producto terminado por OP #" + ordenProduccion.getOrdenId());
        asiento.setModulo("PRODUCCION");
        asiento.setDocumentoOrigen("OP-" + ordenProduccion.getOrdenId());
        asiento.setEstado(AsientoContable.EstadoAsiento.PUBLICADO);

        List<LineaAsientoContable> lineas = new ArrayList<>();

        // 1. Débito a Inventario Productos Terminados
        lineas.add(crearLineaAsiento(
            asiento,
            CuentaContableCodigo.INVENTARIO_PRODUCTOS_TERMINADOS.getCodigo(),
            montoTotal,
            BigDecimal.ZERO,
            "Ingreso de producto terminado a inventario"
        ));

        // 2. Crédito a Inventario WIP (Work in Progress)
        lineas.add(crearLineaAsiento(
            asiento,
            CuentaContableCodigo.INVENTARIO_WIP.getCodigo(),
            BigDecimal.ZERO,
            montoTotal,
            "Salida de WIP por finalización de OP #" + ordenProduccion.getOrdenId()
        ));

        asiento.setLineas(lineas);

        // Validar que el asiento esté balanceado
        validarCuadreContable(asiento);

        return asientoContableRepo.save(asiento);
    }

    /**
     * Valida que un asiento contable esté balanceado (débitos = créditos)
     */
    private void validarCuadreContable(AsientoContable asiento) {
        BigDecimal totalDebitos = asiento.getLineas().stream()
            .map(LineaAsientoContable::getDebito)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCreditos = asiento.getLineas().stream()
            .map(LineaAsientoContable::getCredito)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebitos.compareTo(totalCreditos) != 0) {
            throw new RuntimeException(
                "El asiento contable no está balanceado: " +
                "Débitos=" + totalDebitos + ", Créditos=" + totalCreditos
            );
        }
    }

    /**
     * Busca incorporaciones de activos fijos según los criterios especificados.
     * 
     * @param searchParams DTO con los parámetros de búsqueda
     * @param pageable Configuración de paginación
     * @return Página de incorporaciones que cumplen con los criterios
     */
    public Page<IncorporacionActivoHeader> searchIncorporaciones(
            DTO_SearchIncorporacionActivo searchParams, 
            Pageable pageable) {

        log.info("Buscando incorporaciones de activos. Estado contable: {}, Estado: {}, Fecha inicio: {}, Fecha fin: {}", 
                 searchParams.getEstadoContable(), searchParams.getEstado(), 
                 searchParams.getFechaInicio(), searchParams.getFechaFin());

        // Usar la consulta dinámica con todos los filtros
        return incorporacionActivoHeaderRepo.findByFilters(
            searchParams.getEstadoContable(), 
            searchParams.getEstado(), 
            searchParams.getFechaInicio(), 
            searchParams.getFechaFin(), 
            pageable
        );
    }

    /**
     * Registra un asiento contable para una incorporación de activos fijos.
     * 
     * @param incorporacion La incorporación de activos fijos
     * @return El asiento contable creado
     */
    public AsientoContable registrarAsientoIncorporacionActivos(IncorporacionActivoHeader incorporacion) {
        // Implementación pendiente - Este método se completará en una fase posterior
        // cuando se defina la lógica contable específica para incorporaciones de activos

        AsientoContable asiento = new AsientoContable();
        asiento.setFecha(LocalDateTime.now(applicationClock));
        asiento.setDescripcion("Incorporación de activos fijos #" + incorporacion.getIncorporacionId());
        asiento.setModulo("ACTIVOS_FIJOS");
        asiento.setDocumentoOrigen("INCORP-" + incorporacion.getIncorporacionId());
        asiento.setEstado(AsientoContable.EstadoAsiento.BORRADOR);

        // Aquí se agregarían las líneas del asiento según la lógica contable específica
        // Por ahora, dejamos este método como un placeholder

        return asientoContableRepo.save(asiento);
    }

    /**
     * Registra un asiento contable para un consumo no planificado de materiales
     * 
     * @param transaccion La transacción de almacén
     * @param montoTotal El monto total de la transacción
     * @return El asiento contable creado
     */
    public AsientoContable registrarAsientoConsumoNoPlanificado(
            TransaccionAlmacen transaccion, 
            BigDecimal montoTotal) {

        // Validar que las cuentas existan
        validarCuentasExisten(
            CuentaContableCodigo.INVENTARIO_MATERIAS_PRIMAS.getCodigo(),
            CuentaContableCodigo.GASTOS_PRODUCCION.getCodigo()
        );

        AsientoContable asiento = new AsientoContable();
        asiento.setFecha(LocalDateTime.now(applicationClock));
        asiento.setDescripcion("Consumo no planificado de materiales");
        asiento.setModulo("INVENTARIO");
        asiento.setDocumentoOrigen("DISP-NP-" + transaccion.getTransaccionId());
        asiento.setEstado(AsientoContable.EstadoAsiento.PUBLICADO);

        List<LineaAsientoContable> lineas = new ArrayList<>();

        // 1. Débito a Gastos de Producción
        lineas.add(crearLineaAsiento(
            asiento,
            CuentaContableCodigo.GASTOS_PRODUCCION.getCodigo(),
            montoTotal,
            BigDecimal.ZERO,
            "Consumo no planificado de materiales"
        ));

        // 2. Crédito a Inventario Materias Primas
        lineas.add(crearLineaAsiento(
            asiento,
            CuentaContableCodigo.INVENTARIO_MATERIAS_PRIMAS.getCodigo(),
            BigDecimal.ZERO,
            montoTotal,
            "Salida de materiales por consumo no planificado"
        ));

        asiento.setLineas(lineas);

        // Validar que el asiento esté balanceado
        validarCuadreContable(asiento);

        return asientoContableRepo.save(asiento);
    }

    /**
     * Registra un asiento contable para un backflush no planificado
     * 
     * @param transaccion La transacción de almacén
     * @param producto El producto ingresado
     * @param montoTotal El monto total de la transacción
     * @return El asiento contable creado
     */
    public AsientoContable registrarAsientoBackflushNoPlanificado(
            TransaccionAlmacen transaccion, 
            Producto producto,
            BigDecimal montoTotal) {

        // Validar que las cuentas existan
        validarCuentasExisten(
            CuentaContableCodigo.INVENTARIO_PRODUCTOS_TERMINADOS.getCodigo(),
            CuentaContableCodigo.INVENTARIO_WIP.getCodigo()
        );

        AsientoContable asiento = new AsientoContable();
        asiento.setFecha(LocalDateTime.now(applicationClock));
        asiento.setDescripcion("Ingreso no planificado de producto: " + producto.getNombre());
        asiento.setModulo("INVENTARIO");
        asiento.setDocumentoOrigen("BF-NP-" + transaccion.getTransaccionId());
        asiento.setEstado(AsientoContable.EstadoAsiento.PUBLICADO);

        List<LineaAsientoContable> lineas = new ArrayList<>();

        // 1. Débito a Inventario Productos Terminados
        lineas.add(crearLineaAsiento(
            asiento,
            CuentaContableCodigo.INVENTARIO_PRODUCTOS_TERMINADOS.getCodigo(),
            montoTotal,
            BigDecimal.ZERO,
            "Ingreso de producto terminado a inventario"
        ));

        // 2. Crédito a Inventario WIP
        lineas.add(crearLineaAsiento(
            asiento,
            CuentaContableCodigo.INVENTARIO_WIP.getCodigo(),
            BigDecimal.ZERO,
            montoTotal,
            "Salida de WIP por backflush no planificado"
        ));

        asiento.setLineas(lineas);

        // Validar que el asiento esté balanceado
        validarCuadreContable(asiento);

        return asientoContableRepo.save(asiento);
    }
}
