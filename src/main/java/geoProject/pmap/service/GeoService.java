package geoProject.pmap.service;

import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;

import java.net.MalformedURLException;

public interface GeoService {

    GeoServerRESTReader getReader() throws MalformedURLException;
    GeoServerRESTPublisher getPublisher();
    boolean createDataStrore(String userId, String workspace);
    boolean publishLayer(String userId, String workspace, String layerId, String styleName);
    byte[] getPreviewImg(String workspace, String lyr_id);

}
