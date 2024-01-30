package geoProject.mmap.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.Map;

public interface mService {

    String[] getGeomType(MultipartFile shpFile, String layerId) throws IOException;

    int createFileDB(MultipartHttpServletRequest file);

    int insertLayerInfo(Map<String, Object> params);

    int insertPublicDept(Map<String, Object> params);
}