package simulation.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;

import java.util.List;

/**
 * Sorted Task Best-Fit Policy
 *
 * Extends WeightedResourcePolicy with task pre-sorting
 *
 * Steps:
 * 1. Pre-sort all tasks by combined demand (execDemand + ramDemand) - DESCENDING
 * 2. For each task (in sorted order):
 *    - Use identical VM selection logic as WeightedResourcePolicy
 *    - Assign to VM with the lowest score (least loaded)
 *    - Update VM state
 */
public class SortedTaskBestFitPolicy extends WeightedResourcePolicy {

    private static final double CPU_WEIGHT = 6;
    private static final double RAM_WEIGHT = 3 * 16000;

    protected static double getCpuWeight() {
        return CPU_WEIGHT;
    }

    protected static double getRamWeight() {
        return RAM_WEIGHT;
    }

    protected static double calculateCombinedDemand(Cloudlet cloudlet) {
        return (getCpuWeight() * calculateCpuDemand(cloudlet)) + (getRamWeight() * calculateRamDemand(cloudlet));
    }

    public void sortTasksByDemand(List<Cloudlet> cloudlets) {
        cloudlets.sort((c1, c2) -> {
            double demand1 = calculateCombinedDemand(c1);
            double demand2 = calculateCombinedDemand(c2);
            return Double.compare(demand1, demand2); // Ascending: light tasks first
        });
    }
}
