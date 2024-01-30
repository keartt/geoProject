package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;
import it.geosolutions.geoserver.rest.encoder.GSLayerEncoder;
import it.geosolutions.geoserver.rest.encoder.GSPostGISDatastoreEncoder;
import it.geosolutions.geoserver.rest.encoder.feature.GSFeatureTypeEncoder;
import org.springframework.stereotype.Service;
import geoProject.mmap.service.geoService;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Properties;

import static geoProject.mmap.service.myUtil.*;

@Service("geoService")
public class geoServiceImpl extends EgovAbstractServiceImpl implements geoService {
    private GeoServerRESTReader reader;
    private GeoServerRESTPublisher publisher;

    private String workspace;

    @Resource(name = "globalProperties")
    Properties globalProperties;

    @PostConstruct
    private void initializeProperties() throws MalformedURLException {
        String url = globalProperties.getProperty("2dmap.server.url");
        String user = "admin";
        String password = "geoserver";
        workspace = "mmap";
        this.reader = new GeoServerRESTReader(url, user, password);
        this.publisher = new GeoServerRESTPublisher(url, user, password);
    }

    public GeoServerRESTReader getReader(){
        return reader;
    }

    public GeoServerRESTPublisher getPublisher() {
        return publisher;
    }

    @Override
    public boolean createDataStrore(String userId) {
        GSPostGISDatastoreEncoder datastoreEncoder = new GSPostGISDatastoreEncoder();
        datastoreEncoder.setName(userId);
        Map<String, Object> PostgisInfo = getPostgisInfo(globalProperties);

        datastoreEncoder.setHost((String) PostgisInfo.get("host"));
        datastoreEncoder.setPort((Integer) PostgisInfo.get("port"));
        datastoreEncoder.setDatabase((String) PostgisInfo.get("database"));
        datastoreEncoder.setSchema((String) PostgisInfo.get("schema"));
        datastoreEncoder.setUser((String) PostgisInfo.get("user"));
        datastoreEncoder.setPassword((String) PostgisInfo.get("passwd"));
        // 필요시 추가 설정 가능 max, min 등

        return getPublisher().createPostGISDatastore(workspace, datastoreEncoder);
    }

    public boolean publishLayer(String userId, String layerId) {
        // 피처 타입 설정
        GSFeatureTypeEncoder featureTypeEncoder = new GSFeatureTypeEncoder();
        featureTypeEncoder.setName(layerId);
        featureTypeEncoder.setTitle(layerId);
        featureTypeEncoder.setNativeName(layerId);

        // 레이어 발행 시 기본 스타일 설정 가능
        GSLayerEncoder layerEncoder = new GSLayerEncoder();
//        layerEncoder.setDefaultStyle("your_default_style");

        return getPublisher().publishDBLayer(workspace, userId, featureTypeEncoder, layerEncoder);

    }

}
