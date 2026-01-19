package task.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import task.model.CloudletInfo;
import task.model.WorkloadType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public enum TaskUtils {
    INSTANCE;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void dumpCloudletsToJson(List<Cloudlet> cloudlets,
                                           WorkloadType workloadType,
                                           Path outputPath) throws IOException {

        List<CloudletInfo> infos = new ArrayList<>(cloudlets.size());
        for (Cloudlet c : cloudlets) {
            infos.add(new CloudletInfo(c, workloadType.name()));
        }

        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            gson.toJson(infos, writer);
        }
    }

    public List<Cloudlet> loadCloudletsFromJson(Path inputPath) throws IOException {
        try (Reader reader = Files.newBufferedReader(inputPath)) {
            CloudletInfo[] infos = gson.fromJson(reader, CloudletInfo[].class);
            List<Cloudlet> cloudlets = new ArrayList<>(infos.length);

            for (CloudletInfo info : infos) {
                Cloudlet cloudlet =
                        new CloudletSimple(info.getLength(), info.getPes())
                                .setFileSize(info.getFileSize())
                                .setOutputSize(info.getOutputSize())
                                .setUtilizationModelCpu(new UtilizationModelDynamic(info.getCpuUtil()))
                                .setUtilizationModelRam(new UtilizationModelDynamic(info.getRamUtil()))
                                .setUtilizationModelBw(new UtilizationModelDynamic(info.getBwUtil()));

                cloudlet.setId(info.getId());

                cloudlets.add(cloudlet);
            }

            return cloudlets;
        }
    }
}
