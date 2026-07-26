package exotic.app.planta.model.bi.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record InformeInventarioDTO(
        int versionContrato,
        PeriodoDTO periodo,
        PeriodoDTO periodoTendencia,
        LocalDateTime fechaHoraCorteStock,
        StockDTO stock,
        MovimientosDTO movimientos,
        AjustesInventarioDTO ajustesInventario,
        OcmPendientesDTO ocmPendientes,
        MaterialDirectoOpDTO materialDirectoOp,
        List<NotaDTO> notas
) {
    @Builder
    public record PeriodoDTO(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String modoFecha,
            int dias
    ) {
    }

    @Builder
    public record StockDTO(
            ResumenStockDTO resumen,
            List<StockUnidadDTO> porUnidad,
            MaterialesPorTipoDTO materialesPorTipo,
            List<ComposicionDTO> composicion,
            AbcDTO abc,
            AlertasDTO alertas
    ) {
    }

    @Builder
    public record ResumenStockDTO(
            double valorEstimado,
            int referenciasConStock,
            int referenciasValorizadas,
            Double coberturaCostosPct,
            ValorizacionInventarioDTO valorizacion,
            CoberturaCostosDetalleDTO coberturaCostosDetalle,
            int referenciasNegativas
    ) {
    }

    @Builder
    public record ValorizacionInventarioDTO(
            ValorizacionMaterialesDTO materiales,
            double terminados
    ) {
    }

    @Builder
    public record ValorizacionMaterialesDTO(
            double total,
            double materiaPrima,
            double empaque
    ) {
    }

    @Builder
    public record CoberturaCostosDetalleDTO(
            Double globalPct,
            Double materialesPct,
            Double terminadosPct
    ) {
    }

    @Builder
    public record StockUnidadDTO(
            String unidadMedida,
            double cantidadNeta,
            double cantidadPositiva,
            double cantidadNegativa,
            int referenciasConStock
    ) {
    }

    @Builder
    public record MaterialesPorTipoDTO(
            List<StockUnidadDTO> materiaPrima,
            List<StockUnidadDTO> empaque
    ) {
    }

    @Builder
    public record ComposicionDTO(
            String tipo,
            int referencias,
            double valorEstimado,
            double participacionPct
    ) {
    }

    @Builder
    public record AbcDTO(
            List<ClaseAbcDTO> clases,
            int referenciasExcluidasSinCosto
    ) {
    }

    @Builder
    public record ClaseAbcDTO(
            String clase,
            int referencias,
            double valorEstimado,
            double participacionPct
    ) {
    }

    @Builder
    public record AlertasDTO(
            int total,
            int negativas,
            int bajoUmbral,
            int sinCosto,
            List<AlertaStockDTO> items
    ) {
    }

    @Builder
    public record AlertaStockDTO(
            String tipo,
            int prioridad,
            String productoId,
            String productoNombre,
            String unidadMedida,
            double stock,
            Double umbral,
            List<String> umbralesIncumplidos
    ) {
    }

    @Builder
    public record MovimientosDTO(
            ResumenMovimientosDTO resumen,
            List<FlujoUnidadDTO> porUnidad,
            List<SerieMovimientoDTO> serieDiaria
    ) {
    }

    @Builder
    public record ResumenMovimientosDTO(
            FlujoDTO recepcionesOcm,
            FlujoDTO dispensaciones,
            FlujoDTO productoTerminado,
            FlujoDTO otrosIngresos
    ) {
    }

    @Builder
    public record FlujoDTO(
            int movimientos,
            int referencias,
            double valorEstimado
    ) {
    }

    @Builder
    public record FlujoUnidadDTO(
            String unidadMedida,
            double recepcionesOcm,
            double dispensaciones,
            double productoTerminado,
            double otrosIngresos
    ) {
    }

    @Builder
    public record SerieMovimientoDTO(
            LocalDate fecha,
            String unidadMedida,
            double recepcionesOcm,
            double dispensaciones,
            double productoTerminado,
            double otrosIngresos,
            double valorRecepcionesOcm,
            double valorDispensaciones,
            double valorProductoTerminado,
            double valorOtrosIngresos
    ) {
    }

    @Builder
    public record AjustesInventarioDTO(
            ResumenAjustesDTO resumen,
            ComparativoAjustesDTO comparativo,
            List<SerieAjusteDTO> serieDiaria,
            MayorImpactoAjustesDTO mayorImpacto
    ) {
    }

    @Builder
    public record ResumenAjustesDTO(
            FlujoDTO positivos,
            FlujoDTO negativos,
            double balanceNeto,
            int transacciones,
            int movimientos,
            int referencias
    ) {
    }

    @Builder
    public record ComparativoAjustesDTO(
            GrupoAjustesDTO materiaPrima,
            GrupoAjustesDTO empaque,
            GrupoAjustesDTO otros
    ) {
    }

    @Builder
    public record GrupoAjustesDTO(
            String grupo,
            FlujoDTO positivos,
            FlujoDTO negativos,
            double balanceNeto,
            int transacciones,
            int movimientos,
            int referencias,
            double participacionValorAjustadoPct
    ) {
    }

    @Builder
    public record SerieAjusteDTO(
            LocalDate fecha,
            String grupo,
            String unidadMedida,
            double cantidadPositiva,
            double cantidadNegativa,
            double valorPositivo,
            double valorNegativo
    ) {
    }

    @Builder
    public record MayorImpactoAjustesDTO(
            int limite,
            List<MaterialImpactoAjusteDTO> materiaPrima,
            List<MaterialImpactoAjusteDTO> empaque
    ) {
    }

    @Builder
    public record MaterialImpactoAjusteDTO(
            String productoId,
            String productoNombre,
            String unidadMedida,
            double cantidadPositiva,
            double cantidadNegativa,
            double balanceCantidad,
            double valorPositivo,
            double valorNegativo,
            double balanceValor,
            double impactoEstimado,
            int movimientos,
            int transacciones,
            LocalDateTime ultimoAjuste,
            boolean costoVigente
    ) {
    }

    @Builder
    public record OcmPendientesDTO(
            int ordenes,
            int referencias,
            List<CantidadUnidadDTO> cantidadesPorUnidad,
            double valorPendienteSinIva
    ) {
    }

    @Builder
    public record OcmDTO(
            int ocmId,
            LocalDateTime fechaEmision,
            String proveedor,
            int referencias,
            List<CantidadUnidadDTO> cantidadesPorUnidad,
            double valorPendienteSinIva,
            List<LineaOcmDTO> lineas
    ) {
    }

    @Builder
    public record LineaOcmDTO(
            int itemId,
            String productoId,
            String productoNombre,
            String unidadMedida,
            double ordenado,
            double recibidoAplicado,
            double pendiente,
            double precioUnitarioSinIva,
            double valorPendienteSinIva
    ) {
    }

    @Builder
    public record CantidadUnidadDTO(
            String unidadMedida,
            double cantidad
    ) {
    }

    @Builder
    public record MaterialDirectoOpDTO(
            int ordenes,
            int referencias,
            List<CantidadUnidadDTO> cantidadesPorUnidad,
            double valorEstimado
    ) {
    }

    @Builder
    public record OpMaterialDTO(
            int opId,
            String lote,
            int estado,
            LocalDateTime fechaReferencia,
            int referencias,
            List<CantidadUnidadDTO> cantidadesPorUnidad,
            double valorEstimado
    ) {
    }

    @Builder
    public record NotaDTO(
            String tipo,
            String mensaje
    ) {
    }
}
