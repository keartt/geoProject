package geoProject.service;

import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;
import org.w3c.dom.Element;

import java.net.MalformedURLException;
import java.util.Map;

public interface GeoService {

    GeoServerRESTReader getReader() throws MalformedURLException;
    GeoServerRESTPublisher getPublisher();
    boolean createDataStrore(String userId, String workspace, String schema);
    boolean publishLayer(String storeName, String workspace, String layerId, String styleId, String geomType);
    byte[] getPreviewImg(String workspace, String layerId);
    String addCustomSty(String sld, String geomType, Map<String, Object> data) throws Exception;
    boolean chkWorkspace(String workspace);
    Map<String, Object> getStyColor(String styId) throws Exception;

    Element getElementBySld(String sld) throws Exception;
}
