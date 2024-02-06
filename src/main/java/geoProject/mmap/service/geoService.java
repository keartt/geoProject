package geoProject.mmap.service;

import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;

import java.net.MalformedURLException;

public interface geoService {

    public GeoServerRESTReader getReader() throws MalformedURLException;
    public GeoServerRESTPublisher getPublisher();

    boolean createDataStrore(String userId, String workspace);

    public boolean publishLayer(String userId, String workspace, String layerId, String styleName);

    public String getLayerPreviewImg(String workspace, String layerName);

    }
