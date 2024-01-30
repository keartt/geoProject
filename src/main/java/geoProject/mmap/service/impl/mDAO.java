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
}