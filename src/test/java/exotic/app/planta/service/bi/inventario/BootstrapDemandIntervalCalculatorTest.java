package exotic.app.planta.service.bi.inventario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapDemandIntervalCalculatorTest {
    private final BootstrapDemandIntervalCalculator calculator =
            new BootstrapDemandIntervalCalculator();

    @Test
    void isDeterministicForTheSameSeriesAndSeed() {
        double[] dailyDemand = {0, 0, 10, 0, 5, 0, 0};

        var first = calculator.calculate(dailyDemand, 1234L);
        var second = calculator.calculate(dailyDemand, 1234L);

        assertEquals(first, second);
        assertTrue(first.lowerMean() <= first.upperMean());
    }
}
