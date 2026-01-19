package task;

import org.cloudsimplus.cloudlets.Cloudlet;
import task.generator.WorkloadGenerator;
import task.model.WorkloadType;
import task.utils.TaskUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CreateTasks {

    private static final Path OUTPUT_DIR = Path.of("output/tasks");

    private static final int[] TASK_COUNTS = {200, 1000, 3000};

    private static final WorkloadType[] WORKLOAD_TYPES = {
            WorkloadType.BALANCED,
            WorkloadType.CPU_HEAVY,
            WorkloadType.RAM_HEAVY
    };

    // Task generation parameters
    private static final int PES_NUMBER = 1;
    private static final long MIN_LENGTH = 500_000L;
    private static final long MAX_LENGTH = 5_000_000L;
    private static final long FILE_SIZE = 300L;
    private static final long OUTPUT_SIZE = 300L;
    private static final long RANDOM_SEED = 2507;

    public static void main(String[] args) {
        try {
            Files.createDirectories(OUTPUT_DIR);
        } catch (IOException e) {
            System.err.println("Failed to create output directory: " + e.getMessage());
            return;
        }

        WorkloadGenerator generator = new WorkloadGenerator(RANDOM_SEED);
        int totalGenerated = 0;

        for (int taskCount : TASK_COUNTS) {
            for (WorkloadType workloadType : WORKLOAD_TYPES) {
                String fileName = String.format("tasks_%d_%s.json",
                        taskCount, workloadType.name().toLowerCase());
                Path outputPath = OUTPUT_DIR.resolve(fileName);

                List<Cloudlet> cloudletList = generator.createCloudlets(
                        taskCount,
                        workloadType,
                        PES_NUMBER,
                        MIN_LENGTH,
                        MAX_LENGTH,
                        FILE_SIZE,
                        OUTPUT_SIZE
                );

                try {
                    TaskUtils.INSTANCE.dumpCloudletsToJson(cloudletList, workloadType, outputPath);
                    System.out.printf("Generated: %s (%d tasks)%n", fileName, taskCount);
                    totalGenerated++;
                } catch (IOException e) {
                    System.err.printf("Failed to write %s: %s%n", fileName, e.getMessage());
                }
            }
        }

        System.out.printf("%nDone! Generated %d task files in %s%n", totalGenerated, OUTPUT_DIR);
    }
}
