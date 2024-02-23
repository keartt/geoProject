package geoProject.pmap.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

public interface PMapService {

    String[] getGeomType(MultipartFile shpFile) throws IOException;

    String createTableByShp(MultipartFile zipFile, String layerId);

    int insertFileUldTable(MultipartHttpServletRequest multiRequest, String lyr_id, byte[] img) throws ParseException;

    String downloadShpFile(String layerName, HttpServletResponse response);

    List<Map<String, Object>> getStyleList(String geomType);

    int insertStyList(List<Map<String, Object>> styList);

    boolean existTable(String lyrId);

    boolean dropShpTable(String lyrId, String userId);

    int insertNewSty(Map<String, Object> data);
}
