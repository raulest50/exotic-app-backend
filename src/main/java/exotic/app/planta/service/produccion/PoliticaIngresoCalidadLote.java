package exotic.app.planta.service.produccion;

import exotic.app.planta.model.inventarios.Lote;

/**
 * Puerto de integración para que Calidad gobierne, en una iteración futura,
 * si un lote puede ingresar a almacén. Batch Record no participa en esta decisión.
 */
public interface PoliticaIngresoCalidadLote {

    Evaluacion evaluar(Lote lote);

    record Evaluacion(boolean permitido, String motivo) {
        public static Evaluacion permitir() {
            return new Evaluacion(true, null);
        }
    }
}
