package simulation.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.List;

@FunctionalInterface
public interface CloudletVmSelectionPolicy {
    Vm selectVmFor(Cloudlet cloudlet, List<Vm> availableVms);

    default void sortTasksByDemand(List<Cloudlet> cloudlets) {}
}
