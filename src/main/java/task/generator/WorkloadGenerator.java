package task.generator;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import task.model.WorkloadType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorkloadGenerator {

    // CPU Utilization Constants
    // CPU_HEAVY: High CPU demand
    private static final double CPU_HEAVY_CPU_MIN = 0.70;
    private static final double CPU_HEAVY_CPU_MAX = 1.00;
    // RAM_HEAVY: Low CPU demand
    private static final double RAM_HEAVY_CPU_MIN = 0.20;
    private static final double RAM_HEAVY_CPU_MAX = 0.50;
    // BALANCED: Moderate CPU demand
    private static final double BALANCED_CPU_MIN = 0.40;
    private static final double BALANCED_CPU_MAX = 0.80;

    // RAM Utilization Constants
    // CPU_HEAVY: Low RAM demand
    private static final double CPU_HEAVY_RAM_MIN = 0.05;
    private static final double CPU_HEAVY_RAM_MAX = 0.20;
    // RAM_HEAVY: High RAM demand
    private static final double RAM_HEAVY_RAM_MIN = 0.40;
    private static final double RAM_HEAVY_RAM_MAX = 0.70;
    // BALANCED: Moderate RAM demand
    private static final double BALANCED_RAM_MIN = 0.20;
    private static final double BALANCED_RAM_MAX = 0.50;

    // Bandwidth Utilization Constants
    // CPU_HEAVY: Low BW demand
    private static final double CPU_HEAVY_BW_MIN = 0.10;
    private static final double CPU_HEAVY_BW_MAX = 0.30;
    // RAM_HEAVY: Moderate BW demand
    private static final double RAM_HEAVY_BW_MIN = 0.20;
    private static final double RAM_HEAVY_BW_MAX = 0.40;
    // BALANCED: Moderate BW demand
    private static final double BALANCED_BW_MIN = 0.20;
    private static final double BALANCED_BW_MAX = 0.60;
    // Task Length Constants (MI - Million Instructions)
    private static final long SMALL_TASK_MIN_LENGTH = 1_000L;
    private static final long SMALL_TASK_MAX_LENGTH = 50_000L;
    private static final long MEDIUM_TASK_MIN_LENGTH = 50_000L;
    private static final long MEDIUM_TASK_MAX_LENGTH = 500_000L;
    private static final long LARGE_TASK_MIN_LENGTH = 500_000L;
    private static final long LARGE_TASK_MAX_LENGTH = 5_000_000L;

    private final Random random;

    public WorkloadGenerator(long seed) {
        this.random = new Random(seed);
    }

    public List<Cloudlet> createCloudlets(
            int numCloudlets,
            WorkloadType workloadType,
            int pesNumber,
            long minLength,
            long maxLength,
            long fileSize,
            long outputSize
    ) {
        List<Cloudlet> list = new ArrayList<>(numCloudlets);

        for (int i = 0; i < numCloudlets; i++) {
            long length = randomLongBetween(minLength, maxLength);

            UtilizationModel cpuModel;
            UtilizationModel ramModel;
            UtilizationModel bwModel;

            switch (workloadType) {
                case CPU_HEAVY:
                    cpuModel = constantUtilization(randDouble(CPU_HEAVY_CPU_MIN, CPU_HEAVY_CPU_MAX));
                    ramModel = constantUtilization(randDouble(CPU_HEAVY_RAM_MIN, CPU_HEAVY_RAM_MAX));
                    bwModel  = constantUtilization(randDouble(CPU_HEAVY_BW_MIN, CPU_HEAVY_BW_MAX));
                    break;

                case RAM_HEAVY:
                    cpuModel = constantUtilization(randDouble(RAM_HEAVY_CPU_MIN, RAM_HEAVY_CPU_MAX));
                    ramModel = constantUtilization(randDouble(RAM_HEAVY_RAM_MIN, RAM_HEAVY_RAM_MAX));
                    bwModel  = constantUtilization(randDouble(RAM_HEAVY_BW_MIN, RAM_HEAVY_BW_MAX));
                    break;

                case BALANCED:
                default:
                    cpuModel = constantUtilization(randDouble(BALANCED_CPU_MIN, BALANCED_CPU_MAX));
                    ramModel = constantUtilization(randDouble(BALANCED_RAM_MIN, BALANCED_RAM_MAX));
                    bwModel  = constantUtilization(randDouble(BALANCED_BW_MIN, BALANCED_BW_MAX));
                    break;
            }

            Cloudlet cloudlet =
                    new CloudletSimple(length, pesNumber)
                            .setFileSize(fileSize)
                            .setOutputSize(outputSize)
                            .setUtilizationModelCpu(cpuModel)
                            .setUtilizationModelRam(ramModel)
                            .setUtilizationModelBw(bwModel);

            list.add(cloudlet);
        }

        return list;
    }

    public List<Cloudlet> createMixedSizeCloudlets(
            int smallCount, int mediumCount, int largeCount,
            WorkloadType workloadType,
            int pesNumber,
            long fileSize,
            long outputSize
    ) {
        List<Cloudlet> all = new ArrayList<>();

        all.addAll(
                createCloudlets(
                        smallCount, workloadType, pesNumber,
                        SMALL_TASK_MIN_LENGTH, SMALL_TASK_MAX_LENGTH, fileSize, outputSize
                )
        );

        all.addAll(
                createCloudlets(
                        mediumCount, workloadType, pesNumber,
                        MEDIUM_TASK_MIN_LENGTH, MEDIUM_TASK_MAX_LENGTH, fileSize, outputSize
                )
        );

        all.addAll(
                createCloudlets(
                        largeCount, workloadType, pesNumber,
                        LARGE_TASK_MIN_LENGTH, LARGE_TASK_MAX_LENGTH, fileSize, outputSize
                )
        );

        return all;
    }

    private UtilizationModel constantUtilization(double value) {
        return new UtilizationModelDynamic(value);
    }

    private long randomLongBetween(long min, long max) {
        if (max <= min) {
            return min;
        }
        return min + (long) (random.nextDouble() * (max - min));
    }

    private double randDouble(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}