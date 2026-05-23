package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.compras.OrdenCompraMateriales;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecepcionOcmPolicyService {

    private final MasterDirectiveService masterDirectiveService;

    /**
     * Limite efectivo = limite operativo propio del proveedor.
     * El tope global solo valida cambios futuros en proveedores y no es retroactivo.
     */
    public int resolverLimiteEfectivoRecepcionesParciales(OrdenCompraMateriales ordenCompraMateriales) {
        Proveedor proveedor = ordenCompraMateriales != null ? ordenCompraMateriales.getProveedor() : null;
        return resolverLimiteEfectivoRecepcionesParciales(proveedor);
    }

    public int resolverLimiteEfectivoRecepcionesParciales(Proveedor proveedor) {
        Integer limiteProveedor = proveedor != null ? proveedor.getLimiteRecepcionesParcialesOcm() : null;

        if (limiteProveedor == null) {
            log.warn("Proveedor {} no tiene limite de recepciones OCM configurado. Usando fallback defensivo {}.",
                    proveedor != null ? proveedor.getId() : null,
                    MasterDirectiveKeys.DEFAULT_LIMITE_RECEPCIONES_PARCIALES_OCM_PROVEEDOR);
            return MasterDirectiveKeys.DEFAULT_LIMITE_RECEPCIONES_PARCIALES_OCM_PROVEEDOR;
        }

        if (limiteProveedor < 1) {
            log.warn("Proveedor {} tiene limite de recepciones OCM invalido: {}. Usando fallback defensivo {}.",
                    proveedor.getId(),
                    limiteProveedor,
                    MasterDirectiveKeys.DEFAULT_LIMITE_RECEPCIONES_PARCIALES_OCM_PROVEEDOR);
            return MasterDirectiveKeys.DEFAULT_LIMITE_RECEPCIONES_PARCIALES_OCM_PROVEEDOR;
        }

        return limiteProveedor;
    }

    public void validarLimiteProveedor(Integer limiteProveedor) {
        if (limiteProveedor == null) {
            throw new IllegalArgumentException("El limite de recepciones parciales OCM del proveedor es obligatorio");
        }

        if (limiteProveedor < 1) {
            throw new IllegalArgumentException("El limite de recepciones parciales OCM del proveedor debe ser mayor o igual a 1");
        }

        int limiteGlobal = masterDirectiveService.getLimiteRecepcionesParcialesOcm();
        if (limiteProveedor > limiteGlobal) {
            throw new IllegalArgumentException("El limite de recepciones parciales OCM del proveedor no puede superar el tope global " + limiteGlobal);
        }
    }
}
