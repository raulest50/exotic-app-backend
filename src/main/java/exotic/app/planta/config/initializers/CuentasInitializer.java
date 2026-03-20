package exotic.app.planta.config.initializers;

import exotic.app.planta.model.contabilidad.CuentaContable;
import exotic.app.planta.model.contabilidad.CuentaContable.SaldoNormal;
import exotic.app.planta.model.contabilidad.CuentaContable.TipoCuenta;
import exotic.app.planta.repo.contabilidad.CuentaContableRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CuentasInitializer {

    private final CuentaContableRepo cuentaContableRepository;

    /**
     * Inicializa la tabla 'cuenta' con el catálogo mínimo
     * sólo si aún no existen registros.
     */
    public void initializeCuentas() {
        if (cuentaContableRepository.count() == 0) {
            List<CuentaContable> cuentaContables = List.of(
                    new CuentaContable("1000", "Caja", TipoCuenta.ACTIVO, SaldoNormal.DEBITO, false),
                    new CuentaContable("1010", "Banco", TipoCuenta.ACTIVO, SaldoNormal.DEBITO, false),
                    new CuentaContable("1200", "Inventario Materiales (M)", TipoCuenta.ACTIVO, SaldoNormal.DEBITO, false),
                    new CuentaContable("1210", "Inventario WIP (Trabajo en Proceso)", TipoCuenta.ACTIVO, SaldoNormal.DEBITO, false),
                    new CuentaContable("1220", "Inventario Productos Terminados (FG)", TipoCuenta.ACTIVO, SaldoNormal.DEBITO, false),
                    new CuentaContable("1300", "Activos Fijos", TipoCuenta.ACTIVO, SaldoNormal.DEBITO, false),
                    new CuentaContable("1310", "Depreciación Acumulada", TipoCuenta.ACTIVO, SaldoNormal.CREDITO, false),
                    new CuentaContable("2000", "Cuentas por Pagar – Proveedores", TipoCuenta.PASIVO, SaldoNormal.CREDITO, true),
                    new CuentaContable("3000", "Capital Social/Aportes", TipoCuenta.PATRIMONIO, SaldoNormal.CREDITO, false),
                    new CuentaContable("4100", "Ingresos por Ajustes de Inventario", TipoCuenta.INGRESO, SaldoNormal.CREDITO, false),
                    new CuentaContable("5100", "Gasto por Depreciación", TipoCuenta.GASTO, SaldoNormal.DEBITO, false),
                    new CuentaContable("5200", "Gasto por Scrap (Desperdicio)", TipoCuenta.GASTO, SaldoNormal.DEBITO, false),
                    new CuentaContable("5300", "Pérdidas por Ajustes de Inventario", TipoCuenta.GASTO, SaldoNormal.DEBITO, false)
            );
            cuentaContableRepository.saveAll(cuentaContables);
            System.out.println(">> Tabla de cuentaContables inicializada con valores por defecto");
        }
    }
}
