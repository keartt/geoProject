package geoProject.mmap.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface mService {

    String[] getGeomType(MultipartFile shpFile, String layerId) throws IOException;

    String getShapefileGeomType(Path shpFilePath);

    String createTableByShp(Path shpFilePath, String layerId) throws IOException;
    int insertLayerInfo(Map<String, Object> params);

    int insertPublicDept(Map<String, Object> params);

    byte[] returnShpZip(String layerName);
}