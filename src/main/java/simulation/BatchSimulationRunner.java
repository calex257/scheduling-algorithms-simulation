package simulation;

import simulation.algorithms.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BatchSimulationRunner {

    private static final Path TASKS_DIR = Path.of("output/tasks");

    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private static final List<Class<? extends CloudletVmSelectionPolicy>> POLICY_CLASSES = List.of(
            RoundRobinCloudletVmPolicy.class,
            WeightedResourcePolicy.class,
            SortedTaskBestFitPolicy.class
    );

    public static void main(String[] args) {
        org.cloudsimplus.util.Log.setLevel(ch.qos.logback.classic.Level.ERROR);

        List<Path> taskFiles = getTaskFiles();

        if (taskFiles.isEmpty()) {
            System.err.println("No task files found in " + TASKS_DIR);
            System.err.println("Run CreateTasks first to generate task files.");
            return;
        }

        int totalRuns = taskFiles.size() * POLICY_CLASSES.size();

        System.out.println("=".repeat(80));
        System.out.println("BATCH SIMULATION RUNNER (PARALLEL)");
        System.out.println("=".repeat(80));
        System.out.printf("Found %d task files and %d policies%n", taskFiles.size(), POLICY_CLASSES.size());
        System.out.printf("Total simulations to run: %d%n", totalRuns);
        System.out.printf("Thread pool size: %d%n", THREAD_POOL_SIZE);
        System.out.println("=".repeat(80));
        System.out.println();

        List<SimulationTask> tasks = new ArrayList<>();
        for (Path taskFile : taskFiles) {
            for (Class<? extends CloudletVmSelectionPolicy> policyClass : POLICY_CLASSES) {
                tasks.add(new SimulationTask(taskFile, policyClass));
            }
        }

        AtomicInteger completed = new AtomicInteger(0);
        long batchStartTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<SimulationResult>> futures = new ArrayList<>();

        for (SimulationTask task : tasks) {
            futures.add(executor.submit(() -> runSimulation(task, completed, totalRuns)));
        }

        List<SimulationResult> results = new ArrayList<>();
        for (Future<SimulationResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Failed to get result: " + e.getMessage());
            }
        }

        executor.shutdown();

        long batchElapsedMs = System.currentTimeMillis() - batchStartTime;

        results.sort(Comparator
                .comparing((SimulationResult r) -> r.taskFile)
                .thenComparing(r -> r.policy));

        System.out.println();
        printSummary(results, batchElapsedMs);
    }

    private static SimulationResult runSimulation(SimulationTask task, AtomicInteger completed, int total) {
        String fileName = task.taskFile.getFileName().toString();
        String policyName = task.policyClass.getSimpleName();

        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;

        try {
            CloudletVmSelectionPolicy policy = task.policyClass.getDeclaredConstructor().newInstance();

            WorkloadSimulationRunner runner = new WorkloadSimulationRunner(policy);
            runner.run(task.taskFile, false);
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
        }

        long elapsedMs = System.currentTimeMillis() - startTime;

        int done = completed.incrementAndGet();
        synchronized (System.out) {
            System.out.printf("\r[%d/%d] Completed: %s + %s (%d ms)%s",
                    done, total, truncate(fileName, 25), truncate(policyName, 20), elapsedMs,
                    " ".repeat(20));
        }

        return new SimulationResult(fileName, policyName, success, elapsedMs, errorMessage);
    }

    private static List<Path> getTaskFiles() {
        try (Stream<Path> paths = Files.list(TASKS_DIR)) {
            return paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("tasks.json"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Failed to list task files: " + e.getMessage());
            return List.of();
        }
    }

    private static void printSummary(List<SimulationResult> results, long wallClockTimeMs) {
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("BATCH SIMULATION SUMMARY");
        System.out.println("=".repeat(80));
        System.out.println();

        long successCount = results.stream().filter(r -> r.success).count();
        long failCount = results.size() - successCount;

        System.out.printf("Total simulations: %d%n", results.size());
        System.out.printf("Successful: %d%n", successCount);
        System.out.printf("Failed: %d%n", failCount);
        System.out.println();

        System.out.println("Execution Times:");
        System.out.println("-".repeat(80));
        System.out.printf("%-35s %-30s %10s %8s%n", "Task File", "Policy", "Time (ms)", "Status");
        System.out.println("-".repeat(80));

        for (SimulationResult result : results) {
            System.out.printf("%-35s %-30s %10d %8s%n",
                    truncate(result.taskFile, 35),
                    truncate(result.policy, 30),
                    result.elapsedMs,
                    result.success ? "OK" : "FAILED");
        }

        System.out.println("-".repeat(80));

        long totalSimTimeMs = results.stream().mapToLong(r -> r.elapsedMs).sum();
        double speedup = (double) totalSimTimeMs / wallClockTimeMs;

        System.out.printf("Sum of individual times: %d ms (%.2f seconds)%n", totalSimTimeMs, totalSimTimeMs / 1000.0);
        System.out.printf("Actual wall-clock time:  %d ms (%.2f seconds)%n", wallClockTimeMs, wallClockTimeMs / 1000.0);
        System.out.printf("Parallel speedup:        %.2fx%n", speedup);

        if (failCount > 0) {
            System.out.println();
            System.out.println("ERRORS:");
            for (SimulationResult result : results) {
                if (!result.success) {
                    System.out.printf("  - %s + %s: %s%n", result.taskFile, result.policy, result.errorMessage);
                }
            }
        }

        System.out.println();
        System.out.println("Results written to: output/results/");
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private static class SimulationTask {
        final Path taskFile;
        final Class<? extends CloudletVmSelectionPolicy> policyClass;

        SimulationTask(Path taskFile, Class<? extends CloudletVmSelectionPolicy> policyClass) {
            this.taskFile = taskFile;
            this.policyClass = policyClass;
        }
    }

    private static class SimulationResult {
        final String taskFile;
        final String policy;
        final boolean success;
        final long elapsedMs;
        final String errorMessage;

        SimulationResult(String taskFile, String policy, boolean success, long elapsedMs, String errorMessage) {
            this.taskFile = taskFile;
            this.policy = policy;
            this.success = success;
            this.elapsedMs = elapsedMs;
            this.errorMessage = errorMessage;
        }
    }
}

