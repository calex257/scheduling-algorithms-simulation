package simulation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmResourceStats;
import org.cloudsimplus.vms.VmSimple;
import simulation.algorithms.*;
import task.utils.TaskUtils;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class WorkloadSimulationRunner {

    private final CloudletVmSelectionPolicy vmSelectionPolicy;
    private final VmRamTracker ramTracker = new VmRamTracker();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static class TaskStats {
        public long id;
        public String status;
        public long vmId;
        public double waitingTime;
        public double finishTime;
        public double execTime;
        public double actualCpuTime;

        public TaskStats(Cloudlet c) {
            this.id = c.getId();
            this.status = c.getStatus().toString();
            this.vmId = c.getVm() == null ? -1 : c.getVm().getId();
            this.waitingTime = c.getWaitingTime();
            this.finishTime = c.getFinishTime();
            this.execTime = c.getFinishTime() - c.getExecStartTime();
            this.actualCpuTime = c.getActualCpuTime();
        }
    }

    public static class TaskCompletionReport {
        public String policy;
        public String workloadFile;
        public double makespan;
        public double simulationClock;
        public int totalTasks;
        public List<TaskStats> tasks;

        public TaskCompletionReport(String policy, String workloadFile, double makespan, 
                                    double simulationClock, List<TaskStats> tasks) {
            this.policy = policy;
            this.workloadFile = workloadFile;
            this.makespan = makespan;
            this.simulationClock = simulationClock;
            this.totalTasks = tasks.size();
            this.tasks = tasks;
        }
    }

    public static class VmStats {
        public long vmId;
        public double avgCpuPercent;
        public double peakCpuPercent;
        public double avgRamPercent;
        public double peakRamPercent;
        public long taskCount;
        public int ramSamples;

        public VmStats(long vmId, double avgCpu, double peakCpu, double avgRam, 
                       double peakRam, long taskCount, int ramSamples) {
            this.vmId = vmId;
            this.avgCpuPercent = avgCpu;
            this.peakCpuPercent = peakCpu;
            this.avgRamPercent = avgRam;
            this.peakRamPercent = peakRam;
            this.taskCount = taskCount;
            this.ramSamples = ramSamples;
        }
    }

    public static class MachineUtilizationReport {
        public String policy;
        public String workloadFile;
        public int vmCount;
        public List<VmStats> vms;
        public double avgClusterCpuPercent;
        public double avgClusterRamPercent;

        public MachineUtilizationReport(String policy, String workloadFile, List<VmStats> vms) {
            this.policy = policy;
            this.workloadFile = workloadFile;
            this.vmCount = vms.size();
            this.vms = vms;
            this.avgClusterCpuPercent = vms.stream().mapToDouble(v -> v.avgCpuPercent).average().orElse(0);
            this.avgClusterRamPercent = vms.stream().mapToDouble(v -> v.avgRamPercent).average().orElse(0);
        }
    }

    public WorkloadSimulationRunner(CloudletVmSelectionPolicy vmSelectionPolicy) {
        this.vmSelectionPolicy = Objects.requireNonNull(vmSelectionPolicy);
    }

    public void run(Path workloadFile) throws IOException {
        run(workloadFile, true);
    }

    public void run(Path workloadFile, boolean consoleOutput) throws IOException {
        long startTimeMs = System.currentTimeMillis();

        CloudSimPlus simulation = new CloudSimPlus();

        createDatacenter(simulation);
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);

        broker.setShutdownWhenIdle(false);

        List<Vm> vmList = createVms(simulation, 8);
        broker.submitVmList(vmList);

        Function<Cloudlet, Vm> mapper = cloudlet ->
                vmSelectionPolicy.selectVmFor(cloudlet, Collections.unmodifiableList(vmList));
        broker.setVmMapper(mapper);

        List<Cloudlet> cloudlets = TaskUtils.INSTANCE.loadCloudletsFromJson(workloadFile);
        cloudlets.forEach(c -> {
            if (c.getUtilizationModelCpu() == null) {
                c.setUtilizationModelCpu(new UtilizationModelFull());
            }
        });

        if (vmSelectionPolicy instanceof SortedTaskBestFitPolicy) {
            vmSelectionPolicy.sortTasksByDemand(cloudlets);
        }

        broker.submitCloudletList(cloudlets);

        simulation.addOnClockTickListener(evt -> ramTracker.recordSnapshot(vmList, evt.getTime()));

        simulation.start();

        long elapsedTimeMs = System.currentTimeMillis() - startTimeMs;

        if (consoleOutput) {
            printCloudletStatistics(cloudlets);
            printVmUtilizationStatistics(vmList, cloudlets);
            printOverallMakespan(cloudlets, simulation);
            System.out.println(vmSelectionPolicy instanceof SortedTaskBestFitPolicy);
            System.out.printf(Locale.US, "Real-world execution time: %d ms (%.2f seconds)%n",
                    elapsedTimeMs, elapsedTimeMs / 1000.0);
        }

        String policyName = vmSelectionPolicy.getClass().getSimpleName();
        writeTaskStatsToJson(cloudlets, workloadFile, policyName, simulation);
        writeMachineStatsToJson(vmList, cloudlets, workloadFile, policyName);
    }

    private Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();

        int hostCount = 4;
        int hostPes = 16;
        long hostMipsPerPe = 10_000;
        long ram = 64_000;          // MB
        long bw = 100_000;          // Mbps
        long storage = 1_000_000;   // MB

        for (int i = 0; i < hostCount; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < hostPes; p++) {
                peList.add(new PeSimple(hostMipsPerPe));
            }

            HostSimple host = new HostSimple(ram, bw, storage, peList);
            host.setVmScheduler(new VmSchedulerTimeShared());
            hostList.add(host);
        }

        DatacenterSimple datacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
        datacenter.setSchedulingInterval(1.0);
        return datacenter;
    }

    private List<Vm> createVms(CloudSimPlus simulation, int vmCount) {
        List<Vm> vmList = new ArrayList<>();

        int vmPes = 4;
        long mipsPerPe = 5_000;
        int ram = 16_000;   // MB
        long bw = 20_000;  // Mbps
        long size = 20_000; // MB

        for (int i = 0; i < vmCount; i++) {
            Vm vm = new VmSimple(i, mipsPerPe, vmPes);
            vm.setRam(ram).setBw(bw).setSize(size);
            vm.setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vm.enableUtilizationStats();

            vmList.add(vm);
        }
        return vmList;
    }

    private void printCloudletStatistics(List<Cloudlet> cloudlets) {
        System.out.println("Cloudlet execution results");
        System.out.println("ID\tStatus\tVM\tStart\tFinish\tExecTime\tActualCpuTime");

        for (Cloudlet c : cloudlets) {
            double start = c.getExecStartTime();
            double finish = c.getFinishTime();
            double execTime = finish - start;

            Vm vm = c.getVm();

            System.out.printf(Locale.US,
                    "%3d\t%s\t%3d\t%7.2f\t%7.2f\t%9.2f\t%13.2f%n",
                    c.getId(),
                    c.getStatus(),
                    vm == null ? -1 : vm.getId(),
                    c.getWaitingTime(),
                    finish,
                    execTime,
                    c.getActualCpuTime());
        }
    }

    private void printVmUtilizationStatistics(List<Vm> vms, List<Cloudlet> cloudlets) {
        System.out.println("\nNode (VM) resource usage summary");
        System.out.println("VM\tAvgCPU%\tPeakCPU%\tAvgRAM%\tPeakRAM%\tTaskCount\tSamples");

        for (Vm vm : vms) {
            VmResourceStats cpuHistory = vm.getCpuUtilizationStats();
            double avgCpu = cpuHistory.getMean();
            double peakCpu = cpuHistory.getMax();

            double avgRamPercent = ramTracker.getAverageRamUtilization(vm);
            double peakRamPercent = ramTracker.getPeakRamUtilization(vm);
            int ramSamples = ramTracker.getSampleCount(vm);

            long taskCount = cloudlets.stream()
                    .filter(c -> c.getVm() != null && c.getVm().getId() == vm.getId())
                    .count();

            System.out.printf(Locale.US,
                    "%3d\t%7.2f\t%8.2f\t%7.2f\t%8.2f\t%9d\t%7d%n",
                    vm.getId(),
                    avgCpu * 100.0,
                    peakCpu * 100.0,
                    avgRamPercent * 100.0,
                    peakRamPercent * 100.0,
                    taskCount,
                    ramSamples);
        }
    }

    private void printOverallMakespan(List<Cloudlet> cloudlets, CloudSimPlus simulation) {
        double makespan = cloudlets.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0.0);

        System.out.printf(Locale.US,
                "%nTotal simulated completion time (makespan): %.2f seconds%n", makespan);
        System.out.printf(Locale.US,
                "Simulation clock at end: %.2f seconds%n", simulation.clock());
    }

    private void writeTaskStatsToJson(List<Cloudlet> cloudlets, Path workloadFile, 
                                       String policyName, CloudSimPlus simulation) throws IOException {
        List<TaskStats> taskStatsList = new ArrayList<>();
        for (Cloudlet c : cloudlets) {
            taskStatsList.add(new TaskStats(c));
        }

        double makespan = cloudlets.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0.0);

        TaskCompletionReport report = new TaskCompletionReport(
                policyName,
                workloadFile.getFileName().toString(),
                makespan,
                simulation.clock(),
                taskStatsList
        );

        Path outputDir = Path.of("output/results");
        Files.createDirectories(outputDir);

        String baseName = workloadFile.getFileName().toString().replace(".json", "");
        Path outputPath = outputDir.resolve(baseName + "_" + policyName + "_task_stats.json");

        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            gson.toJson(report, writer);
        }

        System.out.printf("%nTask stats written to: %s%n", outputPath);
    }

    private void writeMachineStatsToJson(List<Vm> vms, List<Cloudlet> cloudlets, 
                                          Path workloadFile, String policyName) throws IOException {
        List<VmStats> vmStatsList = new ArrayList<>();

        for (Vm vm : vms) {
            VmResourceStats cpuHistory = vm.getCpuUtilizationStats();
            double avgCpu = cpuHistory.getMean() * 100.0;
            double peakCpu = cpuHistory.getMax() * 100.0;

            double avgRam = ramTracker.getAverageRamUtilization(vm) * 100.0;
            double peakRam = ramTracker.getPeakRamUtilization(vm) * 100.0;
            int ramSamples = ramTracker.getSampleCount(vm);

            long taskCount = cloudlets.stream()
                    .filter(c -> c.getVm() != null && c.getVm().getId() == vm.getId())
                    .count();

            vmStatsList.add(new VmStats(
                    vm.getId(), avgCpu, peakCpu, avgRam, peakRam, taskCount, ramSamples
            ));
        }

        MachineUtilizationReport report = new MachineUtilizationReport(
                policyName,
                workloadFile.getFileName().toString(),
                vmStatsList
        );

        Path outputDir = Path.of("output/results");
        Files.createDirectories(outputDir);

        String baseName = workloadFile.getFileName().toString().replace(".json", "");
        Path outputPath = outputDir.resolve(baseName + "_" + policyName + "_machine_stats.json");

        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            gson.toJson(report, writer);
        }

        System.out.printf("Machine stats written to: %s%n", outputPath);
    }

    public static void main(String[] args) throws IOException {
        org.cloudsimplus.util.Log.setLevel(ch.qos.logback.classic.Level.ERROR);

        Path workloadJson = Path.of("output/tasks/tasks_3000_ram_heavy.json");


        // 1. Round Robin
//         CloudletVmSelectionPolicy policy = new RoundRobinCloudletVmPolicy();

        // 2. Weighted Resource Balancing
//         CloudletVmSelectionPolicy policy = new WeightedResourcePolicy();

        // 3. Sorted Task Best-Fit
        CloudletVmSelectionPolicy policy = new SortedTaskBestFitPolicy();

        System.out.println("Policy: " + policy.getClass().getSimpleName());
        System.out.println("Workload: " + workloadJson);
        System.out.println();

        WorkloadSimulationRunner runner = new WorkloadSimulationRunner(policy);
        runner.run(workloadJson);
    }
}
