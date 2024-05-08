package geoProject.service.impl;

import egovframework.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository("shpDAO")
public class ShpDAO extends EgovAbstractMapper {
    public int insertFileUldTable(Map<String, Object> params) {
        return (int) insert("pmapDAO.insertFileUldTable", params);
    }

    public List<Map<String, Object>> getStyleList(String geomType) {
        return selectList("pmapDAO.getStyleList", geomType);
    }

    public boolean existTable(Map<String, Object> data) {
        return selectOne("pmapDAO.existTable", data);
    }

    public int deleteLyrTbl(Map<String, Object> params) {
        return (int) delete("pmapDAO.deleteLyrTbl", params);
    }

    public void dropShpTbl(Map<String, Object> params) {
        update("pmapDAO.dropShpTbl", params);
    }

    public int insertNewSty(Map<String, Object> data) {
        return (int) insert("pmapDAO.insertNewSty", data);
    }

    public String isStyDefault(String styId) {
        return selectOne("pmapDAO.isStyDefault", styId);
    }

    public void createUserLyr(Map<String, Object> data) {
        update("pmapDAO.createUserLyr", data);
    }

    public int insertUserLyrOne(Map<String, Object> input) {
        return (int) insert("pmapDAO.insertUserLyrOne", input);
    }

    public List<String> selectLyrColums(Map<String, Object> data) {
        return selectList("pmapDAO.selectLyrColums", data);
    }

    public Map<String, Object> selectUldLyrOne(String lyrId) {
        return selectOne("pmapDAO.selectUldLyrOne", lyrId);
    }

    public int updateSty(Map<String, Object> data) {
        return update("pmapDAO.updateSty", data);
    }

    public String getStyIdByLyr(String lyrId) {
        return selectOne("pmapDAO.getStyIdByLyr", lyrId);
    }

    public int deleteSty(String styId) {
        return (int) delete("pmapDAO.deleteSty", styId);
    }

    public Map<String, Object> selectPmapLyrOne(String lyrId) {
        return selectOne("pmapDAO.selectPmapLyrOne", lyrId);
    }

    public void alterTblNm(Map<String, Object> params) {
        update("pmapDAO.alterTblNm", params);
    }

    public int updateFilUldLyrId(Map<String, Object> params) {
        return (int) update("pmapDAO.updateFilUldLyrId", params);
    }

    public void createSchema(String schema) {
        update("pmapDAO.createSchema", schema);
    }

    public boolean chkSchema(String schema) {
        return (boolean) selectOne("pmapDAO.chkSchema", schema);
    }


    public void dropIndex(Map<String, Object> params) {
        update("pmapDAO.dropIndex", params);
    }

    public List<String> getIndexs(Map<String, Object> params) {
        return selectList("pmapDAO.getIndexs", params);
    }

    public void chgColumn(Map<String, Object> data) {
        update("pmapDAO.chgColumn", data);
    }

    public int updateLyrPrvwImg(Map<String, Object> params) {
        return (int) update("pmapDAO.updateLyrPrvwImg", params);
    }


    public List<Map<String, Object>> selectUldPointLyrOne(Map<String, Object> param) {
        return selectList("pmapDAO.selectUldPointLyrOne", param);
    }


    public Map<String, Object> getLyrImg(String lyrId) {
        return selectOne("pmapDAO.getLyrImg", lyrId);
    }

}
