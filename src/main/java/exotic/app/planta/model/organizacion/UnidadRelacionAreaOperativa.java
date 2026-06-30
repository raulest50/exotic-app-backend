package exotic.app.planta.model.organizacion;

import java.math.BigDecimal;

public enum UnidadRelacionAreaOperativa {
    ML("VOLUMEN", BigDecimal.ONE),
    L("VOLUMEN", new BigDecimal("1000")),
    G("MASA", BigDecimal.ONE),
    KG("MASA", new BigDecimal("1000")),
    U("CONTEO", BigDecimal.ONE);

    private final String dimension;
    private final BigDecimal factorBase;

    UnidadRelacionAreaOperativa(String dimension, BigDecimal factorBase) {
        this.dimension = dimension;
        this.factorBase = factorBase;
    }

    public boolean isCompatibleWith(UnidadRelacionAreaOperativa other) {
        return other != null && dimension.equals(other.dimension);
    }

    public BigDecimal toBase(BigDecimal cantidad) {
        return cantidad.multiply(factorBase);
    }
}
