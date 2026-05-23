package exotic.app.planta.service.inventarios;

import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecepcionOcmPolicyServiceTest {

    @Test
    void resolverLimiteEfectivoRecepcionesParciales_usesProviderLimitOnly() {
        MasterDirectiveService masterDirectiveService = mock(MasterDirectiveService.class);
        RecepcionOcmPolicyService service = new RecepcionOcmPolicyService(masterDirectiveService);
        Proveedor proveedor = new Proveedor();
        proveedor.setId("900123");
        proveedor.setLimiteRecepcionesParcialesOcm(5);

        assertEquals(5, service.resolverLimiteEfectivoRecepcionesParciales(proveedor));
    }

    @Test
    void resolverLimiteEfectivoRecepcionesParciales_usesProviderDefaultForLegacyNull() {
        MasterDirectiveService masterDirectiveService = mock(MasterDirectiveService.class);
        RecepcionOcmPolicyService service = new RecepcionOcmPolicyService(masterDirectiveService);
        Proveedor proveedor = new Proveedor();
        proveedor.setId("900123");
        proveedor.setLimiteRecepcionesParcialesOcm(null);

        assertEquals(
                MasterDirectiveKeys.DEFAULT_LIMITE_RECEPCIONES_PARCIALES_OCM_PROVEEDOR,
                service.resolverLimiteEfectivoRecepcionesParciales(proveedor)
        );
    }

    @Test
    void validarLimiteProveedor_rejectsNull() {
        MasterDirectiveService masterDirectiveService = mock(MasterDirectiveService.class);
        RecepcionOcmPolicyService service = new RecepcionOcmPolicyService(masterDirectiveService);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.validarLimiteProveedor(null)
        );

        assertEquals("El limite de recepciones parciales OCM del proveedor es obligatorio", error.getMessage());
    }

    @Test
    void validarLimiteProveedor_rejectsValuesAboveGlobalTop() {
        MasterDirectiveService masterDirectiveService = mock(MasterDirectiveService.class);
        when(masterDirectiveService.getLimiteRecepcionesParcialesOcm()).thenReturn(3);
        RecepcionOcmPolicyService service = new RecepcionOcmPolicyService(masterDirectiveService);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.validarLimiteProveedor(4)
        );

        assertEquals("El limite de recepciones parciales OCM del proveedor no puede superar el tope global 3", error.getMessage());
    }
}
