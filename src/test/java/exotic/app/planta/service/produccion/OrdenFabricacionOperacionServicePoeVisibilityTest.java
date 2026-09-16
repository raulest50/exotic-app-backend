package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.dto.OrdenFabricacionDTOs;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdenFabricacionOperacionServicePoeVisibilityTest {

    @Mock
    private OrdenFabricacionOperacionRepo operacionRepo;
    @InjectMocks
    private OrdenFabricacionOperacionService service;

    @Test
    void soloIncluyeMetadatosPoeDelAreaResponsable() {
        OrdenFabricacionOperacion propia = operacion(1L, 9L, 101L);
        OrdenFabricacionOperacion ajena = operacion(2L, 10L, 102L);
        when(operacionRepo
                .findByOrdenFabricacion_OrdenFabricacionIdOrderByPosicionSecuenciaAsc(55L))
                .thenReturn(List.of(propia, ajena));

        List<OrdenFabricacionDTOs.OperacionResponse> result =
                service.listarOperativo(55L, 9L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPoeDocumentoVersionId()).isEqualTo(101L);
        assertThat(result.get(0).getPoeVersion()).isEqualTo(3);
        assertThat(result.get(1).getPoeDocumentoVersionId()).isNull();
        assertThat(result.get(1).getPoeVersion()).isNull();
        assertThat(result.get(1).getPoeNombreArchivo()).isNull();
    }

    private static OrdenFabricacionOperacion operacion(
            Long id,
            Long responsableId,
            Long poeId
    ) {
        User responsable = new User();
        responsable.setId(responsableId);
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(Math.toIntExact(responsableId));
        area.setNombre("Area " + responsableId);
        area.setResponsableArea(responsable);

        OrdenFabricacion orden = new OrdenFabricacion();
        orden.setOrdenFabricacionId(55L);

        ProcesoProduccionDocumentoVersion poe = new ProcesoProduccionDocumentoVersion();
        poe.setId(poeId);
        poe.setVersion(3);
        poe.setNombreArchivoOriginal("poe-" + poeId + ".pdf");

        OrdenFabricacionOperacion operacion = new OrdenFabricacionOperacion();
        operacion.setId(id);
        operacion.setOrdenFabricacion(orden);
        operacion.setAreaOperativa(area);
        operacion.setPoeDocumentoVersion(poe);
        operacion.setFrontendNodeId("node-" + id);
        operacion.setProcesoProduccionId(30);
        operacion.setProcesoNombre("Proceso " + id);
        operacion.setEstadoEnum(EstadoSeguimientoOrdenArea.ESPERA);
        operacion.setFechaEstadoActual(LocalDateTime.of(2026, 9, 16, 9, 0));
        return operacion;
    }
}
