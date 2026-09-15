package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.EstadoCalidadLote;
import exotic.app.planta.model.inventarios.Lote;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticaIngresoCalidadLoteNoBloqueanteTest {

    private final PoliticaIngresoCalidadLote politica =
            new PoliticaIngresoCalidadLoteNoBloqueante();

    @ParameterizedTest
    @EnumSource(EstadoCalidadLote.class)
    void permiteIngresoParaTodoEstadoDeCalidad(EstadoCalidadLote estado) {
        Lote lote = new Lote();
        lote.setEstadoCalidad(estado);

        PoliticaIngresoCalidadLote.Evaluacion evaluacion = politica.evaluar(lote);

        assertTrue(evaluacion.permitido());
        assertNull(evaluacion.motivo());
    }
}
