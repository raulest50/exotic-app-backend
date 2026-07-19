package exotic.app.planta.service.bi.inventario;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.SplittableRandom;

@Component
class BootstrapDemandIntervalCalculator {
    private static final int SAMPLE_COUNT = 2_000;
    private static final double LOWER_PERCENTILE = 0.025;
    private static final double UPPER_PERCENTILE = 0.975;

    DemandMeanInterval calculate(double[] dailyDemand, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        double[] sampledMeans = new double[SAMPLE_COUNT];

        for (int sampleIndex = 0; sampleIndex < SAMPLE_COUNT; sampleIndex++) {
            double sampledSum = 0;
            for (int dayIndex = 0; dayIndex < dailyDemand.length; dayIndex++) {
                sampledSum += dailyDemand[random.nextInt(dailyDemand.length)];
            }
            sampledMeans[sampleIndex] = sampledSum / dailyDemand.length;
        }

        Arrays.sort(sampledMeans);
        return new DemandMeanInterval(
                nearestRank(sampledMeans, LOWER_PERCENTILE),
                nearestRank(sampledMeans, UPPER_PERCENTILE));
    }

    private double nearestRank(double[] sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile * sortedValues.length) - 1;
        int boundedIndex = Math.max(0, Math.min(index, sortedValues.length - 1));
        return sortedValues[boundedIndex];
    }

    record DemandMeanInterval(double lowerMean, double upperMean) {
    }
}
