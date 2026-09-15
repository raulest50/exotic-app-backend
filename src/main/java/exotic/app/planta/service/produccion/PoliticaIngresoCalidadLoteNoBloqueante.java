package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;
import org.springframework.stereotype.Service;

/** Política transitoria acordada: Calidad es documental y ningún estado bloquea. */
@Service
public class PoliticaIngresoCalidadLoteNoBloqueante implements PoliticaIngresoCalidadLote {
    @Override
    public Evaluacion evaluar(Lote lote) {
        return Evaluacion.permitir();
    }
}
