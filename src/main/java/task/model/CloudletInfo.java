package task.model;

import org.cloudsimplus.cloudlets.Cloudlet;

public class CloudletInfo {
    private long id;
    private String workloadType;
    private long length;
    private long pes;
    private long fileSize;
    private long outputSize;
    private double cpuUtil;
    private double ramUtil;
    private double bwUtil;

    public CloudletInfo(Cloudlet c, String workloadType) {
        this.id = c.getId();
        this.workloadType = workloadType;
        this.length = c.getLength();
        this.pes = c.getPesNumber();
        this.fileSize = c.getFileSize();
        this.outputSize = c.getOutputSize();
        this.cpuUtil = c.getUtilizationModelCpu().getUtilization(0);
        this.ramUtil = c.getUtilizationModelRam().getUtilization(0);
        this.bwUtil = c.getUtilizationModelBw().getUtilization(0);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getWorkloadType() {
        return workloadType;
    }

    public void setWorkloadType(String workloadType) {
        this.workloadType = workloadType;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public long getPes() {
        return pes;
    }

    public void setPes(long pes) {
        this.pes = pes;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(long outputSize) {
        this.outputSize = outputSize;
    }

    public double getCpuUtil() {
        return cpuUtil;
    }

    public void setCpuUtil(double cpuUtil) {
        this.cpuUtil = cpuUtil;
    }

    public double getRamUtil() {
        return ramUtil;
    }

    public void setRamUtil(double ramUtil) {
        this.ramUtil = ramUtil;
    }

    public double getBwUtil() {
        return bwUtil;
    }

    public void setBwUtil(double bwUtil) {
        this.bwUtil = bwUtil;
    }
}
