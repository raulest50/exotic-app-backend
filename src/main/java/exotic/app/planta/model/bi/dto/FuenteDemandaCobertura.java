package exotic.app.planta.model.bi.dto;

public enum FuenteDemandaCobertura {
    SOLO_DISPENSACIONES,
    DISPENSACIONES_MAS_CONTINGENCIAS;

    public boolean incluyeContingencias() {
        return this == DISPENSACIONES_MAS_CONTINGENCIAS;
    }
}
