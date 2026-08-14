package exotic.app.planta.resource.productos;

import exotic.app.planta.model.producto.UnidadTiempoVencimiento;
import exotic.app.planta.model.producto.dto.CategoriaResponseDTO;
import exotic.app.planta.model.producto.dto.CategoriaVidaUtilRequestDTO;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.security.ModuleTabAccessGuard;
import exotic.app.planta.service.productos.CategoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoriaResourceAccessTest {

    @Test
    void updateVidaUtil_requiresLevelThreeOnExactProductionTab() {
        CategoriaService service = mock(CategoriaService.class);
        ModuleTabAccessGuard guard = mock(ModuleTabAccessGuard.class);
        CategoriaResource resource = new CategoriaResource(service, guard);
        Authentication authentication = mock(Authentication.class);
        CategoriaVidaUtilRequestDTO request = new CategoriaVidaUtilRequestDTO();
        request.setVidaUtilCantidad(6);
        request.setVidaUtilUnidad(UnidadTiempoVencimiento.MESES);
        CategoriaResponseDTO expected = mock(CategoriaResponseDTO.class);
        when(service.updateVidaUtil(7, 6, UnidadTiempoVencimiento.MESES)).thenReturn(expected);

        CategoriaResponseDTO actual = resource
                .updateVidaUtil(authentication, 7, request)
                .getBody();

        assertSame(expected, actual);
        verify(guard).requireTabAccess(
                eq(authentication),
                eq(ModuloSistema.PRODUCCION),
                eq("PARAMETROS_POR_CATEGORIA"),
                eq(3),
                anyString()
        );
    }
}
