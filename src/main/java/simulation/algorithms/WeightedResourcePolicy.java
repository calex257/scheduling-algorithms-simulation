package simulation.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Selects VMs based on a weighted score, considering both CPU and RAM.
 * Does NOT reorder the tasks.
 * The algorithm assigns cloudlets to the least loaded VM, based on the scoring formula:
 *
 * score = CPU_WEIGHT * (execution / cpu_capacity) + RAM_WEIGHT * (avg_ram_per_task * no_cores)
 *
 * Steps:
 * 1. Track cumulative CPU demand & average RAM usage
 * 2. Score each VM to find the one with most available resources
 * 3. Assign cloudlet to VM with the lowest score (least loaded)
 */
public class WeightedResourcePolicy implements CloudletVmSelectionPolicy {

    private static final double CPU_WEIGHT = 4;
    private static final double RAM_WEIGHT = 30;

    // Track CPU load per VM (in MI - Million Instructions)
    private final Map<Vm, Double> cpuLoad = new HashMap<>();

    // Track RAM usage per VM (in MB)
    private final Map<Vm, Double> ramUsage = new HashMap<>();

    // Track number of tasks assigned to each VM as of now
    private final Map<Vm, Integer> taskCount = new HashMap<>();

    protected static double getCpuWeight() {
        return CPU_WEIGHT;
    }

    protected static double getRamWeight() {
        return RAM_WEIGHT;
    }

    @Override
    public Vm selectVmFor(Cloudlet cloudlet, List<Vm> vms) {
        if (vms.isEmpty()) {
            throw new IllegalStateException("No VMs available for Cloudlet mapping");
        }

        // Initialization if needed
        for (Vm vm : vms) {
            cpuLoad.putIfAbsent(vm, 0.0);
            ramUsage.putIfAbsent(vm, 0.0);
            taskCount.putIfAbsent(vm, 0);
        }

        // Calculate resource demands for this cloudlet
        double cloudletCpuDemand = this.calculateCpuDemand(cloudlet);
        double cloudletRamDemand = this.calculateRamDemand(cloudlet);

        // Find VM with the lowest load score
        Vm selectedVm = null;
        double lowestScore = Double.MAX_VALUE;

        for (Vm vm : vms) {
            double score = this.calculateLoadScore(vm);

            if (score < lowestScore) {
                lowestScore = score;
                selectedVm = vm;
            }
        }

        // Update maps for the selected VM
        if (selectedVm != null) {
            cpuLoad.put(selectedVm, cpuLoad.get(selectedVm) + cloudletCpuDemand);
            ramUsage.put(selectedVm, ramUsage.get(selectedVm) + cloudletRamDemand);
            taskCount.put(selectedVm, taskCount.get(selectedVm) + 1);
        }

        return selectedVm;
    }

    /**
     * Calculate the CPU demand of a cloudlet in Million Instructions (MI).
     */
    protected static double calculateCpuDemand(Cloudlet cloudlet) {
        double cpuUtil = cloudlet.getUtilizationModelCpu() != null
                ? cloudlet.getUtilizationModelCpu().getUtilization()
                : 1.0;

        if (cpuUtil <= 0.0)
            cpuUtil = 0.01;

        return cloudlet.getLength() / cpuUtil;
    }

    /**
     * Calculate the RAM demand of a cloudlet in MB.
     */
    protected static double calculateRamDemand(Cloudlet cloudlet) {
        double ramUtil = cloudlet.getUtilizationModelRam() != null
                ? cloudlet.getUtilizationModelRam().getUtilization()
                : 0.0;

        return ramUtil;
    }

    /**
     * Calculate the load score for a VM.
     * Lower score means less loaded (more available resources).
     *
     * score = CPU_WEIGHT * (execution / cpu_capacity) + RAM_WEIGHT * (avg_ram_per_task * no_cores)
     */
    private double calculateLoadScore(Vm vm) {
        // CPU capacity = MIPS * (no. of PEs (cores))
        double cpuCapacity = vm.getMips() * vm.getPesNumber();

        // Get current loads
        double currentCpuLoad = cpuLoad.get(vm);
        double currentRamUsage = ramUsage.get(vm);
        int currentTaskCount = taskCount.get(vm);

        // CPU load. The work accumulates over time.
        double cpuLoadFraction = currentCpuLoad / cpuCapacity;

        // Estimated average RAM: avgRamPerTask * (no. of cores per vm)
        double avgRamPerTask = currentTaskCount > 0
                ? currentRamUsage / currentTaskCount
                : 0.0;
        double estimatedAvgRam = avgRamPerTask * vm.getPesNumber();

        // Weighted score
        double cpuComponent = getCpuWeight() * cpuLoadFraction;
        double ramComponent = getRamWeight() * estimatedAvgRam;
        double totalScore = cpuComponent + ramComponent;

        return totalScore;
    }
}
