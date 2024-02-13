package geoProject.mmap.service.impl;

import egovframework.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository("mDAO")
public class mDAO extends EgovAbstractMapper{

    public int insertLayerInfo(Map<String, Object> params) {
        return (int)insert("mDAO.insertShpLayer", params);
    }

    public int insertPublicDept(Map<String, Object> params) {
        return (int)insert("mDAO.insertPublicDept", params);
    }

    public int insertBlob(Map<String, Object> params) {
        return (int)insert("mDAO.insertBlob", params);
    }

    public Map<String, Object> getBlob(String layerName){
        return selectOne("mDAO.selectBlob", layerName);
    }

    public void insertSty(String[] successArray) {
        insert("mDAO.insertSty", successArray);
    }
}