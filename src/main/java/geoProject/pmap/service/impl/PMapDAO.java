package geoProject.pmap.service.impl;

import egovframework.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository("pmapDAO")
public class PMapDAO extends EgovAbstractMapper {
    public int insertFileUldTable(Map<String, Object> params) {
        return (int) insert("pmapDAO.insertFileUldTable", params);
    }

    public int insertShrDeptTable(Map<String, Object> params) {
        return (int) insert("pmapDAO.insertShrDeptTable", params);
    }

    public List<Map<String, Object>> getStyleList(String geomType) {
        return selectList("pmapDAO.getStyleList", geomType);
    }

    public int insertStyList(List<Map<String, Object>> styList) {
        return (int) insert("pmapDAO.insertStyList", styList);
    }

    public boolean existTable(String lyrId) {
        return selectOne("pmapDAO.existTable", lyrId);
    }

    public boolean pmaIsGeomTypeSame(String lyrId, String styId) {
        Map<String, Object> params = new HashMap<>();
        params.put("lyr_id", lyrId);
        params.put("sty_id", styId);

        return selectOne("pmapDAO.pmaIsGeomTypeSame", params);
    }

    public int deleteLyrTbl(Map<String, Object> params) {
        return (int) delete("deleteLyrTbl", params);
    }

    public void dropShpTbl(String lyrId) {
        update("dropShpTbl", lyrId);
    }

    public int insertNewSty(Map<String, Object> data) {
        return (int) insert("pmapDAO.insertNewSty", data);
    }
}
