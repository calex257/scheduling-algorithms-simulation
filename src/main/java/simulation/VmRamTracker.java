package simulation;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks RAM utilization statistics for VMs over time.
 */
public class VmRamTracker {

    private double lastSampleTime = -1.0;
    // Only sample after minimum <sampleInterval> seconds. Note that sampling only happens when an event occurs.
    private final double sampleInterval = 0.2;

    private static class RamStats {
        private double sum = 0.0;
        private double peak = 0.0;
        private int count = 0;

        void record(double utilization) {
            sum += utilization;
            count++;
            if (utilization > peak) {
                peak = utilization;
            }
        }

        double getAverage() {
            return count > 0 ? sum / count : 0.0;
        }

        double getPeak() {
            return peak;
        }
    }

    private final Map<Vm, RamStats> vmStats = new HashMap<>();

    public void recordSnapshot(List<Vm> vms, double currentTime) {
        // Only sample at the specified interval
        if (currentTime - lastSampleTime < sampleInterval) {
            return;  // Skip this sample
        }
        lastSampleTime = currentTime;

        for (Vm vm : vms) {
            vmStats.putIfAbsent(vm, new RamStats());

            // Calculate total RAM demand (including what needs to be swapped)
            // Sum up RAM requirements of all running cloudlets
            double capacity = vm.getRam().getCapacity();
            double totalDemandMB = 0.0;

            for (var cloudletExec : vm.getCloudletScheduler().getCloudletExecList()) {
                Cloudlet cloudlet = cloudletExec.getCloudlet();
                if (cloudlet != null && cloudlet.getUtilizationModelRam() != null) {
                    double ramUtil = cloudlet.getUtilizationModelRam().getUtilization();
                    totalDemandMB += ramUtil * capacity;
                }
            }

            double utilization = capacity > 0 ? totalDemandMB / capacity : 0.0;

            vmStats.get(vm).record(utilization);
        }
    }

    public double getAverageRamUtilization(Vm vm) {
        RamStats stats = vmStats.get(vm);
        return stats != null ? stats.getAverage() : 0.0;
    }

    public double getPeakRamUtilization(Vm vm) {
        RamStats stats = vmStats.get(vm);
        return stats != null ? stats.getPeak() : 0.0;
    }

    public int getSampleCount(Vm vm) {
        RamStats stats = vmStats.get(vm);
        return stats != null ? stats.count : 0;
    }
}
