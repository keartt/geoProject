package geoProject.service;

import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ShpService {

    String[] getGeomType(MultipartFile shpFile) throws IOException;

    String[] createTableByShp(MultipartFile zipFile, String layerId, String schema) throws IOException;

    void chgColumn(String orgNm, String newNm, String tblNm, String schema);

    String downloadShpFile(String layerName, HttpServletResponse response, String schema);

    List<Map<String, Object>> getStyleList(String geomType);

    boolean existTable(String lyrId, String schema);

    boolean dropUldLyr(Map<String, Object> data);

    int insertNewSty(Map<String, Object> data);

    boolean isStyDefault(String styId);

    boolean createUserLyr(Map<String, Object> data);

    int insertUserLyrOne(Map<String, Object> data);

    List<String> selectLyrColums(String layerId);

    Map<String, Object> selectUldLyrOne(String lyrId);

    int updateSty(Map<String, Object> data);

    String getStyIdByLyr(String lyrId);

    boolean deleteSty(String styId);

    int insertFileUldTable(String lyrId, String workspace, byte[] img, String styId, String userId, String geomType);

    void alterTblNm(Map<String, Object> params);

    int updateFilUldLyrId(Map<String, Object> params);

    int updateLyrPrvwImg(String lyrId, String userId, byte[] img);

    boolean chkSchema(String schema);

    String getSchema(String lyrId);
    String getSchema(String workspace, String regId);

    boolean chkAuth(String userId, String lyrId);
    boolean chkAuth(HttpSession session, String lyrId);

    String deleteUldLyr(Map<String, Object> data);

    List<Map<String, Object>> selectUldPointLyrOne(String lyrId, String column);

    Map<String, Object> getLyrImg(String lyrId);

}
