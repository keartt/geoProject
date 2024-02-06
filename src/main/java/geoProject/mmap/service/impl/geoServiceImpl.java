package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;
import it.geosolutions.geoserver.rest.encoder.GSLayerEncoder;
import it.geosolutions.geoserver.rest.encoder.GSPostGISDatastoreEncoder;
import it.geosolutions.geoserver.rest.encoder.feature.GSFeatureTypeEncoder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import geoProject.mmap.service.geoService;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

import static geoProject.mmap.service.myUtil.*;

@Service("geoService")
public class geoServiceImpl extends EgovAbstractServiceImpl implements geoService {
    private GeoServerRESTReader reader;
    private GeoServerRESTPublisher publisher;

    @Resource(name = "globalProperties")
    Properties globalProperties;

    @PostConstruct
    private void initializeProperties() throws MalformedURLException {
        String url = globalProperties.getProperty("2dmap.server.url");
        String user = "admin";
        String password = "geoserver";
        this.reader = new GeoServerRESTReader(url, user, password);
        this.publisher = new GeoServerRESTPublisher(url, user, password);
    }

    @Override
    public GeoServerRESTReader getReader(){
        return reader;
    }

    @Override
    public GeoServerRESTPublisher getPublisher() {
        return publisher;
    }

    // 유저 아이디 저장소 확인 및 생성
    @Override
    public boolean createDataStrore(String userId, String workspace) {
        GSPostGISDatastoreEncoder datastoreEncoder = new GSPostGISDatastoreEncoder();
        datastoreEncoder.setName(userId);
        Map<String, Object> PostgisInfo = getPostgisInfo(globalProperties);

        datastoreEncoder.setHost((String) PostgisInfo.get("host"));
        // when use docker geoserver
        datastoreEncoder.setHost("host.docker.internal");
        datastoreEncoder.setPort((Integer) PostgisInfo.get("port"));
        datastoreEncoder.setDatabase((String) PostgisInfo.get("database"));
        datastoreEncoder.setSchema((String) PostgisInfo.get("schema"));
        datastoreEncoder.setUser((String) PostgisInfo.get("user"));
        datastoreEncoder.setPassword((String) PostgisInfo.get("passwd"));
        // 필요시 추가 설정 가능 max, min 등

        return getPublisher().createPostGISDatastore(workspace, datastoreEncoder);
    }

    // db -> geoserver layer publish
    @Override
    public boolean publishLayer(String userId, String workspace, String layerId, String styleName) {
        // 피처 타입 설정
        GSFeatureTypeEncoder featureTypeEncoder = new GSFeatureTypeEncoder();
        featureTypeEncoder.setName(layerId);
        featureTypeEncoder.setTitle(layerId);
        featureTypeEncoder.setNativeName(layerId);

        // 레이어 발행 시 기본 스타일 설정 가능
        GSLayerEncoder layerEncoder = new GSLayerEncoder();
        layerEncoder.setDefaultStyle(styleName);

        return getPublisher().publishDBLayer(workspace, userId, featureTypeEncoder, layerEncoder);
    }

    // layer preview png to base 64 string
    @Override
    public String getLayerPreviewImg(String workspace, String layerName) {
        String geoserverBaseUrl = globalProperties.getProperty("2dmap.server.url");
        String previewUrl = geoserverBaseUrl + "/wms/reflect?layers=" + workspace + ":" + layerName + "&format=image/png";
        HttpClient httpClient = HttpClients.createDefault();
        String base64Image = null;
        try {
            HttpGet getRequest = new HttpGet(previewUrl);
            HttpResponse response = httpClient.execute(getRequest);

            if (response.getStatusLine().getStatusCode() == 200) {
                byte[] pngImage = EntityUtils.toByteArray(response.getEntity());
                base64Image = Base64.getEncoder().encodeToString(pngImage);

            } else {
                System.err.println("Failed to obtain layer preview. HTTP Error Code: " + response.getStatusLine().getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Close the HttpClient
            httpClient.getConnectionManager().shutdown();
        }
        return base64Image;
    }

}

