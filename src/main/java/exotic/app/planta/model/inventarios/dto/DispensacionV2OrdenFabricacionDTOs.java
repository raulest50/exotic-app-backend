package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Contratos específicos de Dispensación V2 para órdenes de fabricación. */
public final class DispensacionV2OrdenFabricacionDTOs {

    private DispensacionV2OrdenFabricacionDTOs() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private Long ordenFabricacionId;
        private String lote;
        private String semiTerminadoId;
        private String semiTerminadoNombre;
        private Double cantidadPlanificada;
        private String unidadMedida;
        private String estado;
        private String estadoDispensacionMateriales;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreparationResponse {
        private Option orden;
        private DispensacionV2AreaDTO area;
        @Builder.Default
        private List<DispensacionV2MaterialDTO> materiales = new ArrayList<>();
        @Builder.Default
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentRequest {
        private Integer areaId;
        private List<DispensacionV2MaterialEditableRequestDTO> materiales = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalizationRequest {
        private Integer areaId;
        private String observaciones;
        private List<DispensacionV2FinalizacionMaterialRequestDTO> materiales = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalizationResponse {
        private Long ordenFabricacionId;
        private String lote;
        private Integer transaccionId;
    }
}
