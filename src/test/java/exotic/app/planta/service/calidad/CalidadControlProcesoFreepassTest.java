package exotic.app.planta.service.calidad;

import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.EjecucionRequest;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordEtapaRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalidadControlProcesoFreepassTest {
    private final ControlProcesoPlantillaRepo plantillas = mock(ControlProcesoPlantillaRepo.class);
    private final ControlProcesoEjecucionRepo ejecuciones = mock(ControlProcesoEjecucionRepo.class);
    private final LoteRepo lotes = mock(LoteRepo.class);
    private final BatchRecordRepo expedientes = mock(BatchRecordRepo.class);
    private final BatchRecordEtapaRepo etapas = mock(BatchRecordEtapaRepo.class);
    private final SeguimientoOrdenAreaRepo seguimientos = mock(SeguimientoOrdenAreaRepo.class);
    private final CalidadControlProcesoService service = new CalidadControlProcesoService(
            plantillas, ejecuciones, mock(AreaProduccionRepo.class), lotes, expedientes,
            etapas, mock(MasterDirectiveService.class), seguimientos);
    private AreaOperativa area;
    private Lote lote;
    private SeguimientoOrdenArea seguimiento;
    private ControlProcesoPlantilla plantilla;

    @BeforeEach
    void opYPlantillaDelArea() {
        area = new AreaOperativa();
        area.setAreaId(9);
        area.setNombre("Mezcla");
        OrdenProduccion orden = new OrdenProduccion();
        orden.setOrdenId(77);
        orden.setProducto(new Terminado());
        lote = new Lote();
        lote.setId(10L);
        lote.setOrdenProduccion(orden);
        seguimiento = new SeguimientoOrdenArea();
        seguimiento.setId(20L);
        seguimiento.setOrdenProduccion(orden);
        seguimiento.setAreaOperativa(area);
        seguimiento.setAplicaEjecucionOp(false);
        plantilla = new ControlProcesoPlantilla();
        plantilla.setId(30L);
        plantilla.setAreaOperativa(area);
        plantilla.setEstado(EstadoControlProcesoPlantilla.VIGENTE);
        plantilla.setVersion(1);
        when(lotes.findById(10L)).thenReturn(Optional.of(lote));
        when(seguimientos.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(77))
                .thenReturn(List.of(seguimiento));
    }

    @Test
    void preparacionRechazaFreepassAntesDeExistirExpedienteSinUsarLaPlantillaVigente() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.prepararEjecucion(9, 10L, null))
                .withMessageContaining("free pass");

        verifyNoInteractions(expedientes, etapas, plantillas, ejecuciones);
    }

    @Test
    void escrituraDirectaNoCreaControlAntesDeExistirExpediente() {
        when(plantillas.findById(30L)).thenReturn(Optional.of(plantilla));
        EjecucionRequest request = new EjecucionRequest();
        request.setLoteId(10L);
        request.setPlantillaId(30L);

        assertThatIllegalArgumentException().isThrownBy(
                () -> service.guardarEjecucion(new User(), request))
                .withMessageContaining("free pass");

        verifyNoInteractions(expedientes, etapas, ejecuciones);
    }

    @Test
    void snapshotHistoricoNullConservaPreparacionAunqueElAreaAhoraSeaSoloOf() {
        seguimiento.setAplicaEjecucionOp(null);
        area.setAlcanceEjecucion(AreaOperativa.AlcanceEjecucion.SOLO_OF);
        when(expedientes.findByLoteResultado_Id(10L)).thenReturn(Optional.empty());
        when(plantillas.findFirstByAreaOperativa_AreaIdAndEstado(
                9, EstadoControlProcesoPlantilla.VIGENTE)).thenReturn(Optional.of(plantilla));

        var response = service.prepararEjecucion(9, 10L, null);

        assertThat(response.getPlantilla().getId()).isEqualTo(30L);
        assertThat(response.getBatchRecordEtapaId()).isNull();
        assertThat(response.getLote().getOrdenProduccionId()).isEqualTo(77);
        verifyNoInteractions(ejecuciones, etapas);
    }

    @Test
    void otraEtapaAplicableDeLaMismaAreaNoAutorizaUnaEtapaExplicitaConFreepass() {
        SeguimientoOrdenArea aplicable = new SeguimientoOrdenArea();
        aplicable.setAreaOperativa(area);
        aplicable.setAplicaEjecucionOp(true);
        when(seguimientos.findByOrdenProduccion_OrdenIdOrderByPosicionSecuenciaAsc(77))
                .thenReturn(List.of(seguimiento, aplicable));
        BatchRecord record = new BatchRecord();
        record.setId(11L);
        record.setOrdenProduccion(lote.getOrdenProduccion());
        BatchRecordEtapa etapa = new BatchRecordEtapa();
        etapa.setId(12L);
        etapa.setBatchRecord(record);
        etapa.setAreaOperativa(area);
        etapa.setSeguimientoOrdenArea(seguimiento);
        etapa.setControlProcesoPlantilla(plantilla);
        when(expedientes.findByLoteResultado_Id(10L)).thenReturn(Optional.of(record));
        when(etapas.findByIdAndBatchRecord_Id(12L, 11L)).thenReturn(Optional.of(etapa));

        assertThatIllegalArgumentException().isThrownBy(
                () -> service.prepararEjecucion(9, 10L, 12L))
                .withMessageContaining("free pass");

        verifyNoInteractions(plantillas, ejecuciones);
    }
}
