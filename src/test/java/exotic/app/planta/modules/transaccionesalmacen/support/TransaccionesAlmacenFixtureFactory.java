package exotic.app.planta.modules.transaccionesalmacen.support;

import exotic.app.planta.model.commons.divisas.Divisas;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.packaging.CasePack;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Transactional
public class TransaccionesAlmacenFixtureFactory {

    private final UserRepository userRepository;
    private final AreaProduccionRepo areaProduccionRepo;
    private final MaterialRepo materialRepo;
    private final TerminadoRepo terminadoRepo;
    private final CategoriaRepo categoriaRepo;
    private final ProveedorRepo proveedorRepo;
    private final OrdenCompraRepo ordenCompraRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final LoteRepo loteRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;

    public TransaccionesAlmacenFixtureFactory(
            UserRepository userRepository,
            AreaProduccionRepo areaProduccionRepo,
            MaterialRepo materialRepo,
            TerminadoRepo terminadoRepo,
            CategoriaRepo categoriaRepo,
            ProveedorRepo proveedorRepo,
            OrdenCompraRepo ordenCompraRepo,
            OrdenProduccionRepo ordenProduccionRepo,
            LoteRepo loteRepo,
            TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo
    ) {
        this.userRepository = userRepository;
        this.areaProduccionRepo = areaProduccionRepo;
        this.materialRepo = materialRepo;
        this.terminadoRepo = terminadoRepo;
        this.categoriaRepo = categoriaRepo;
        this.proveedorRepo = proveedorRepo;
        this.ordenCompraRepo = ordenCompraRepo;
        this.ordenProduccionRepo = ordenProduccionRepo;
        this.loteRepo = loteRepo;
        this.transaccionAlmacenHeaderRepo = transaccionAlmacenHeaderRepo;
    }

    public ModuleFixture seedModuleFixture() {
        User master = userRepository.findByUsername("master")
                .orElseThrow(() -> new IllegalStateException("master user must exist for tests"));

        AreaOperativa almacenGeneral = ensureArea("almacen general", "Area base del modulo");
        AreaOperativa mezclado = ensureArea("mezclado", "Area usada por TransaccionesAlmacen");
        AreaOperativa envasado = ensureArea("envasado", "Area secundaria para busquedas");

        Material materialPrincipal = createMaterial("MP-TA-001", "Acido citrico test", 1, "KG", 12.5);
        Material materialEmpaque = createMaterial("ME-TA-001", "Envase test 250ml", 2, "U", 1.2);
        Proveedor proveedor = createProveedor("900123001", "Proveedor QA almacen");
        OrdenCompraMateriales ordenCompra = createOrdenCompra(proveedor, materialPrincipal, 20, 2);
        TransaccionAlmacen ingresoOcm = createCompraTransaccion(
                ordenCompra,
                materialPrincipal,
                20.0,
                "MPLOT-001",
                master,
                "Ingreso inicial para TransaccionesAlmacen"
        );

        Categoria categoria = createCategoria(9001, "Categoria QA", 12);
        Terminado terminado = createTerminado("PT-TA-001", "Shampoo QA", categoria, materialPrincipal);
        OrdenProduccion ordenAbierta = createOrdenProduccion(terminado, "LOT-PT-001", 0, 2.0, mezclado.getNombre());
        OrdenProduccion ordenMasivo = createOrdenProduccion(terminado, "LOT-PT-002", 0, 1.0, mezclado.getNombre());
        OrdenProduccion ordenCancelada = createOrdenProduccion(terminado, "LOT-PT-003", -1, 1.0, envasado.getNombre());

        return new ModuleFixture(
                master,
                almacenGeneral,
                mezclado,
                envasado,
                materialPrincipal,
                materialEmpaque,
                proveedor,
                ordenCompra,
                ingresoOcm,
                ingresoOcm.getMovimientosTransaccion().get(0).getLote(),
                categoria,
                terminado,
                ordenAbierta,
                ordenMasivo,
                ordenCancelada
        );
    }

    public TransaccionAlmacen createDispensacionTransaccion(
            OrdenProduccion ordenProduccion,
            Material material,
            Lote lote,
            double cantidad,
            User user,
            AreaOperativa areaDestino
    ) {
        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(-Math.abs(cantidad));
        movimiento.setProducto(material);
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.DISPENSACION);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        movimiento.setLote(lote);
        movimiento.setAreaOperativa(areaDestino);

        return saveTransaccion(
                TransaccionAlmacen.TipoEntidadCausante.OD,
                ordenProduccion.getOrdenId(),
                "Dispensacion base de prueba",
                user,
                List.of(movimiento)
        );
    }

    public TransaccionAlmacen createReporteAveriaTransaccion(
            OrdenProduccion ordenProduccion,
            Material material,
            Lote lote,
            double cantidad,
            User user,
            AreaOperativa area
    ) {
        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(Math.abs(cantidad));
        movimiento.setProducto(material);
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.AVERIA);
        movimiento.setAlmacen(Movimiento.Almacen.AVERIAS);
        movimiento.setLote(lote);
        movimiento.setAreaOperativa(area);

        return saveTransaccion(
                TransaccionAlmacen.TipoEntidadCausante.RA,
                ordenProduccion.getOrdenId(),
                "Averia base de prueba",
                user,
                List.of(movimiento)
        );
    }

    private AreaOperativa ensureArea(String nombre, String descripcion) {
        return areaProduccionRepo.findByNombre(nombre).orElseGet(() -> {
            AreaOperativa area = new AreaOperativa();
            area.setNombre(nombre);
            area.setDescripcion(descripcion);
            return areaProduccionRepo.save(area);
        });
    }

    private Material createMaterial(String productoId, String nombre, int tipoMaterial, String tipoUnidades, double costo) {
        Material material = new Material();
        material.setProductoId(productoId);
        material.setNombre(nombre);
        material.setTipoMaterial(tipoMaterial);
        material.setTipoUnidades(tipoUnidades);
        material.setCantidadUnidad(1.0);
        material.setCosto(costo);
        material.setIvaPercentual(19.0);
        material.setPuntoReorden(5);
        material.setObservaciones("Fixture " + productoId);
        material.setInventareable(true);
        return materialRepo.save(material);
    }

    private Proveedor createProveedor(String businessId, String nombre) {
        Proveedor proveedor = new Proveedor();
        proveedor.setId(businessId);
        proveedor.setTipoIdentificacion(1);
        proveedor.setNombre(nombre);
        proveedor.setDireccion("Zona Industrial QA");
        proveedor.setRegimenTributario(0);
        proveedor.setCiudad("Barranquilla");
        proveedor.setDepartamento("Atlantico");
        proveedor.setCondicionPago("credito");
        proveedor.setCategorias(new int[] {1, 2});
        proveedor.setObservacion("Proveedor fixture");
        return proveedorRepo.save(proveedor);
    }

    private OrdenCompraMateriales createOrdenCompra(Proveedor proveedor, Material material, int cantidad, int estado) {
        OrdenCompraMateriales ordenCompra = new OrdenCompraMateriales();
        ordenCompra.setFechaEmision(LocalDateTime.now().minusDays(2));
        ordenCompra.setFechaVencimiento(LocalDateTime.now().plusDays(10));
        ordenCompra.setProveedor(proveedor);
        ordenCompra.setCondicionPago("credito");
        ordenCompra.setTiempoEntrega("48h");
        ordenCompra.setPlazoPago(30);
        ordenCompra.setEstado(estado);
        ordenCompra.setDivisas(Divisas.DIVISAS.COP);
        ordenCompra.setTrm(1.0);
        ordenCompra.setObservaciones("OCM fixture");

        ItemOrdenCompra item = new ItemOrdenCompra();
        item.setOrdenCompraMateriales(ordenCompra);
        item.setMaterial(material);
        item.setCantidad(cantidad);
        item.setPrecioUnitario(1000);
        item.setIvaCOP(190);
        item.setSubTotal(cantidad * 1000);
        item.setCantidadCorrecta(1);
        item.setPrecioCorrecto(1);

        ordenCompra.setItemsOrdenCompra(new ArrayList<>(List.of(item)));
        ordenCompra.setSubTotal(item.getSubTotal());
        ordenCompra.setIvaCOP(item.getIvaCOP() * cantidad);
        ordenCompra.setTotalPagar(ordenCompra.getSubTotal() + ordenCompra.getIvaCOP());

        return ordenCompraRepo.save(ordenCompra);
    }

    private TransaccionAlmacen createCompraTransaccion(
            OrdenCompraMateriales ordenCompra,
            Material material,
            double cantidad,
            String batchNumber,
            User user,
            String observaciones
    ) {
        Lote lote = new Lote();
        lote.setBatchNumber(batchNumber);
        lote.setProductionDate(LocalDate.now().minusDays(7));
        lote.setExpirationDate(LocalDate.now().plusMonths(6));
        lote.setOrdenCompraMateriales(ordenCompra);
        lote = loteRepo.save(lote);

        Movimiento movimiento = new Movimiento();
        movimiento.setCantidad(cantidad);
        movimiento.setProducto(material);
        movimiento.setTipoMovimiento(Movimiento.TipoMovimiento.COMPRA);
        movimiento.setAlmacen(Movimiento.Almacen.GENERAL);
        movimiento.setLote(lote);

        return saveTransaccion(
                TransaccionAlmacen.TipoEntidadCausante.OCM,
                ordenCompra.getOrdenCompraId(),
                observaciones,
                user,
                List.of(movimiento)
        );
    }

    private Categoria createCategoria(int categoriaId, String nombre, int loteSize) {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(categoriaId);
        categoria.setCategoriaNombre(nombre);
        categoria.setCategoriaDescripcion("Categoria fixture");
        categoria.setLoteSize(loteSize);
        categoria.setTiempoDiasFabricacion(2);
        categoria.setCapacidadProductivaDiaria(100);
        return categoriaRepo.save(categoria);
    }

    private Terminado createTerminado(
            String productoId,
            String nombre,
            Categoria categoria,
            Material materialPrincipal
    ) {
        Terminado terminado = new Terminado();
        terminado.setProductoId(productoId);
        terminado.setNombre(nombre);
        terminado.setObservaciones("Terminado fixture");
        terminado.setCosto(35.0);
        terminado.setIvaPercentual(19.0);
        terminado.setTipoUnidades("U");
        terminado.setCantidadUnidad(1.0);
        terminado.setInventareable(true);
        terminado.setStatus(0);
        terminado.setCategoria(categoria);
        terminado.setPrefijoLote("QAT");

        Insumo insumo = new Insumo();
        insumo.setProducto(materialPrincipal);
        insumo.setCantidadRequerida(2.0);
        terminado.setInsumos(new ArrayList<>(List.of(insumo)));

        CasePack casePack = new CasePack();
        casePack.setUnitsPerCase(12);
        casePack.setInsumosEmpaque(new ArrayList<>());
        terminado.setCasePack(casePack);

        return terminadoRepo.save(terminado);
    }

    private OrdenProduccion createOrdenProduccion(
            Terminado terminado,
            String loteAsignado,
            int estadoOrden,
            double cantidadProducir,
            String areaOperativa
    ) {
        OrdenProduccion ordenProduccion = new OrdenProduccion();
        ordenProduccion.setProducto(terminado);
        ordenProduccion.setLoteAsignado(loteAsignado);
        ordenProduccion.setEstadoOrden(estadoOrden);
        ordenProduccion.setObservaciones("Orden fixture " + loteAsignado);
        ordenProduccion.setCantidadProducir(cantidadProducir);
        ordenProduccion.setFechaLanzamiento(LocalDateTime.now().minusDays(1));
        ordenProduccion.setFechaFinalPlanificada(LocalDateTime.now().plusDays(2));
        ordenProduccion.setAreaOperativa(areaOperativa);
        ordenProduccion.setDepartamentoOperativo("produccion");
        ordenProduccion.setNumeroPedidoComercial("PED-" + loteAsignado);
        return ordenProduccionRepo.save(ordenProduccion);
    }

    private TransaccionAlmacen saveTransaccion(
            TransaccionAlmacen.TipoEntidadCausante tipoEntidadCausante,
            int idEntidadCausante,
            String observaciones,
            User user,
            List<Movimiento> movimientos
    ) {
        TransaccionAlmacen transaccion = new TransaccionAlmacen();
        transaccion.setTipoEntidadCausante(tipoEntidadCausante);
        transaccion.setIdEntidadCausante(idEntidadCausante);
        transaccion.setObservaciones(observaciones);
        transaccion.setUsuarioAprobador(user);
        transaccion.setUsuariosResponsables(new ArrayList<>(List.of(user)));

        List<Movimiento> movimientosPersistibles = new ArrayList<>(movimientos);
        for (Movimiento movimiento : movimientosPersistibles) {
            movimiento.setTransaccionAlmacen(transaccion);
        }
        transaccion.setMovimientosTransaccion(movimientosPersistibles);
        return transaccionAlmacenHeaderRepo.save(transaccion);
    }

    public record ModuleFixture(
            User masterUser,
            AreaOperativa almacenGeneral,
            AreaOperativa mezclado,
            AreaOperativa envasado,
            Material materialPrincipal,
            Material materialEmpaque,
            Proveedor proveedor,
            OrdenCompraMateriales ordenCompra,
            TransaccionAlmacen ingresoOcm,
            Lote loteMateriaPrima,
            Categoria categoria,
            Terminado terminado,
            OrdenProduccion ordenAbierta,
            OrdenProduccion ordenMasivo,
            OrdenProduccion ordenCancelada
    ) {
    }
}
