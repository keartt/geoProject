package geoProject.pmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;
import it.geosolutions.geoserver.rest.encoder.GSLayerEncoder;
import it.geosolutions.geoserver.rest.encoder.GSPostGISDatastoreEncoder;
import it.geosolutions.geoserver.rest.encoder.feature.GSFeatureTypeEncoder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import geoProject.pmap.service.GeoService;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Properties;

@Service("geoService")
public class GeoServiceImpl extends EgovAbstractServiceImpl implements GeoService {

    private GeoServerRESTReader reader;
    private GeoServerRESTPublisher publisher;

    @Resource(name = "globalProperties")
    Properties globalProperties;

    @Resource(name = "pmapDAO")
    private PMapDAO pMapDAO;

    @PostConstruct
    private void initializeProperties() throws MalformedURLException {
        String url = globalProperties.getProperty("2dmap.server.url");
        String user = "admin";
        String password = "geoserver";
        this.reader = new GeoServerRESTReader(url, user, password);
        this.publisher = new GeoServerRESTPublisher(url, user, password);
    }

    @Override
    public GeoServerRESTReader getReader() throws MalformedURLException {
        return reader;
    }

    @Override
    public GeoServerRESTPublisher getPublisher() {
        return publisher;
    }

    @Override
    public boolean createDataStrore(String userId, String workspace) {
        String jdbcUrl = globalProperties.getProperty("Globals.postgresql.url");
        String username = globalProperties.getProperty("Globals.postgresql.username");
        String password = globalProperties.getProperty("Globals.postgresql.password");

        int protocolIndex = jdbcUrl.indexOf("://");
        int slashIndex = jdbcUrl.indexOf("/", protocolIndex + 3);
        String[] hostPortArray = jdbcUrl.substring(protocolIndex + 3, slashIndex).split(":");

        GSPostGISDatastoreEncoder datastoreEncoder = new GSPostGISDatastoreEncoder();
        datastoreEncoder.setName(userId);
        datastoreEncoder.setHost(hostPortArray[0]);
        datastoreEncoder.setPort(Integer.parseInt(hostPortArray[1]));
        datastoreEncoder.setDatabase(jdbcUrl.substring(slashIndex + 1));
        datastoreEncoder.setSchema("public");
        datastoreEncoder.setUser(username);
        datastoreEncoder.setPassword(password);

        return getPublisher().createPostGISDatastore(workspace, datastoreEncoder);
    }

    @Override
    public boolean publishLayer(String user_id, String workspace, String lyr_id, String sty_id) {
        // 피처 타입 설정
        GSFeatureTypeEncoder featureTypeEncoder = new GSFeatureTypeEncoder();
        featureTypeEncoder.setName(lyr_id);
        featureTypeEncoder.setTitle(lyr_id);
        featureTypeEncoder.setNativeName(lyr_id);
        GSLayerEncoder layerEncoder = new GSLayerEncoder();

        if(sty_id != null && reader.existsStyle(sty_id) || pMapDAO.pmaIsGeomTypeSame(lyr_id, sty_id)){
            // 스타일아이디 not null  & 지오서버에 존재 & 레이어와 스타일의 geomType 이 같을때
            layerEncoder.setDefaultStyle(sty_id);
        }
        return getPublisher().publishDBLayer(workspace, user_id, featureTypeEncoder, layerEncoder);
    }

    @Override
    public byte[] getPreviewImg(String workspace, String lyr_id) {
        String geoserverBaseUrl = globalProperties.getProperty("2dmap.server.url");
        String previewUrl = geoserverBaseUrl + "/wms/reflect?layers=" + workspace + ":" + lyr_id + "&format=image/png";
        HttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet getRequest = new HttpGet(previewUrl);
            HttpResponse response = httpClient.execute(getRequest);
            if (response.getStatusLine().getStatusCode() == 200) {
                return resizeImage(EntityUtils.toByteArray(response.getEntity()));
            } else {
                throw new HttpResponseException(response.getStatusLine().getStatusCode(), "FAIL GET geoserverImg");
            }
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    //resize img 1/4
    private byte[] resizeImage(byte[] originalImage) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(originalImage);
        BufferedImage bufferedImage = ImageIO.read(bis);

        int newWidth = bufferedImage.getWidth() / 4;
        int newHeight = bufferedImage.getHeight() / 4;
        Image scaledImage = bufferedImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "png", bos);
        byte[] compressedImage = bos.toByteArray();
        bos.close();
        return compressedImage;
    }
}
