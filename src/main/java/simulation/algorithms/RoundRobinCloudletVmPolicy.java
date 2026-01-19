package simulation.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.List;

/**
 * Blindly circles through the VMs when assigning tasks.
 * Does NOT reorder the tasks.
 */
public class RoundRobinCloudletVmPolicy implements CloudletVmSelectionPolicy{
    private int nextIndex = 0;

    @Override
    public Vm selectVmFor(Cloudlet cloudlet, List<Vm> vms) {
        if (vms.isEmpty()) {
            throw new IllegalStateException("No VMs available for Cloudlet mapping");
        }
        Vm vm = vms.get(nextIndex);
        nextIndex = (nextIndex + 1) % vms.size();
        return vm;
    }
}
