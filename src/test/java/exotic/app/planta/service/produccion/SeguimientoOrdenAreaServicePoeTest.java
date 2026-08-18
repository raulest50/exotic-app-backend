package exotic.app.planta.service.produccion;

import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.repo.empresa.JornadaLaboralVersionRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeguimientoOrdenAreaServicePoeTest {

    @Test
    void inicializarSeguimiento_freezesCurrentPoeVersion() {
        Fixture fixture = fixture(true);

        fixture.service.inicializarSeguimiento(fixture.orden);

        assertSame(fixture.documento, fixture.savedSeguimiento.getPoeDocumentoVersion());
    }

    @Test
    void inicializarSeguimiento_keepsLegacyNodeWithoutPoe() {
        Fixture fixture = fixture(false);

        fixture.service.inicializarSeguimiento(fixture.orden);

        assertNull(fixture.savedSeguimiento.getPoeDocumentoVersion());
        verify(fixture.documentoRepo, never()).findAllByProcesoProcesoIdInAndEstado(any(), any());
    }

    private Fixture fixture(boolean withProcess) {
        SeguimientoOrdenAreaRepo seguimientoRepo = mock(SeguimientoOrdenAreaRepo.class);
        SeguimientoOrdenAreaEventoRepo eventoRepo = mock(SeguimientoOrdenAreaEventoRepo.class);
        RutaProcesoCatVersionRepo rutaRepo = mock(RutaProcesoCatVersionRepo.class);
        ProcesoProduccionDocumentoVersionRepo documentoRepo =
                mock(ProcesoProduccionDocumentoVersionRepo.class);
        BatchRecordService batchRecordService = mock(BatchRecordService.class);

        SeguimientoOrdenAreaService service = new SeguimientoOrdenAreaService(
                seguimientoRepo,
                eventoRepo,
                mock(AreaProduccionRepo.class),
                rutaRepo,
                mock(JornadaLaboralVersionRepo.class),
                mock(RutaProcesoEstimacionService.class),
                mock(ReporteProduccionLoteService.class),
                mock(UserRepository.class),
                mock(MasterDirectiveService.class),
                batchRecordService,
                documentoRepo,
                mock(OrdenFabricacionOperacionRepo.class),
                mock(LoteRepo.class),
                Clock.fixed(Instant.parse("2026-08-18T15:00:00Z"), ZoneId.of("America/Bogota"))
        );

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(7);
        Terminado producto = new Terminado();
        producto.setProductoId("TER-001");
        producto.setCategoria(categoria);
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(55);
        orden.setProducto(producto);

        AreaOperativa area = new AreaOperativa();
        area.setAreaId(10);
        RutaProcesoNode node = new RutaProcesoNode();
        node.setId(100L);
        node.setAreaOperativa(area);

        ProcesoProduccionDocumentoVersion documento = null;
        if (withProcess) {
            ProcesoProduccion proceso = new ProcesoProduccion();
            proceso.setProcesoId(701);
            proceso.setNombre("Pesaje");
            node.setProcesoProduccion(proceso);

            documento = new ProcesoProduccionDocumentoVersion();
            documento.setId(9001L);
            documento.setProceso(proceso);
            documento.setVersion(3);
            when(documentoRepo.findAllByProcesoProcesoIdInAndEstado(
                    anyCollection(),
                    eq(ProcesoProduccionDocumentoVersion.Estado.VIGENTE)
            )).thenReturn(List.of(documento));
        }

        RutaProcesoCatVersion ruta = new RutaProcesoCatVersion();
        ruta.setId(20L);
        ruta.setNodes(List.of(node));
        ruta.setEdges(List.of());
        node.setRutaProcesoCatVersion(ruta);
        when(rutaRepo.findByCategoriaIdAndEstado(7, RutaProcesoCatVersion.Estado.VIGENTE))
                .thenReturn(Optional.of(ruta));

        Fixture fixture = new Fixture();
        fixture.service = service;
        fixture.orden = orden;
        fixture.documento = documento;
        fixture.documentoRepo = documentoRepo;
        when(seguimientoRepo.save(any(SeguimientoOrdenArea.class))).thenAnswer(invocation -> {
            fixture.savedSeguimiento = invocation.getArgument(0);
            return fixture.savedSeguimiento;
        });
        when(eventoRepo.saveAndFlush(any(SeguimientoOrdenAreaEvento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return fixture;
    }

    private static class Fixture {
        private SeguimientoOrdenAreaService service;
        private OrdenProduccion orden;
        private ProcesoProduccionDocumentoVersion documento;
        private ProcesoProduccionDocumentoVersionRepo documentoRepo;
        private SeguimientoOrdenArea savedSeguimiento;
    }
}
