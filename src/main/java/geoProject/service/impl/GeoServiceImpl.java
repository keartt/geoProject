package geoProject.service.impl;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import geoProject.service.GeoService;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service("geoService")
public class GeoServiceImpl extends EgovAbstractServiceImpl implements GeoService {

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
    public GeoServerRESTReader getReader() throws MalformedURLException {
        return reader;
    }

    @Override
    public GeoServerRESTPublisher getPublisher() {
        return publisher;
    }

    @Override
    public boolean chkWorkspace(String workspace) {
        if (reader.existsWorkspace(workspace)){
            return true;
        }else{
            return publisher.createWorkspace(workspace);
        }
    }

    @Override
    public boolean createDataStrore(String userId, String workspace, String schema) {
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
        datastoreEncoder.setSchema(schema);
        datastoreEncoder.setUser(username);
        datastoreEncoder.setPassword(password);

        return getPublisher().createPostGISDatastore(workspace, datastoreEncoder);
    }

    @Override
    public boolean publishLayer(String storeName, String workspace, String layerId, String styleId, String geomType) {
        // 피처 타입 설정
        GSFeatureTypeEncoder featureTypeEncoder = new GSFeatureTypeEncoder();
        featureTypeEncoder.setName(layerId);
        featureTypeEncoder.setTitle(layerId);
        featureTypeEncoder.setNativeName(layerId);
        GSLayerEncoder layerEncoder = new GSLayerEncoder();

        // 스타일아이디 not null  & 지오서버에 존재
        if(styleId != null && (reader.existsStyle(styleId) || reader.existsStyle("scdtw", styleId))){
            layerEncoder.setDefaultStyle(styleId);
        }
        return getPublisher().publishDBLayer(workspace, storeName, featureTypeEncoder, layerEncoder);
    }

    @Override
    public byte[] getPreviewImg(String workspace, String layerId) {
        String geoserverBaseUrl = globalProperties.getProperty("2dmap.server.url");
        String previewUrl = geoserverBaseUrl + "/wms/reflect?layers=" + workspace + ":" + layerId + "&format=image/png";
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

    @Override
    public String addCustomSty(String sld, String geomType, Map<String, Object> data) throws Exception {
        Element rootElement = getElementBySld(sld);
        data.putIfAbsent("sty_nm", "Test") ;

        // sld name
        NodeList nameElements = rootElement.getElementsByTagName("sld:Name");
        for (int i = 0; i < nameElements.getLength(); i++) {
            Element elem = (Element) nameElements.item(i);
            elem.setTextContent((String) data.get("sty_nm"));
        }

        // sld stroke
        Node strokeNode = rootElement.getElementsByTagName("sld:Stroke").item(0);
        if (strokeNode.getNodeType() == Node.ELEMENT_NODE) {
            Element strokeElem = (Element) strokeNode;
            Map<String, Object> stroke = (Map<String, Object>) data.get("stroke");
            chkCssParameters(strokeElem, stroke);
            NodeList cssParameterList = ((Element) strokeNode).getElementsByTagName("sld:CssParameter");
            for (int j = 0; j < cssParameterList.getLength(); j++) {
                Element elem = (Element) cssParameterList.item(j);
                String name = elem.getAttribute("name");
                if (stroke.get(name) != null) {
                    if (name.equals("stroke-dasharray")){
                        elem.setTextContent((Boolean) stroke.get(name) ? "5.0 15.0" : "");
                    }else{
                        elem.setTextContent((String) stroke.get(name));
                    }
                }
            }
        }

        // sld fill
        if (!geomType.equals("multilinestring")) {
            Node fillNode = rootElement.getElementsByTagName("sld:Fill").item(0);
            if (fillNode.getNodeType() == Node.ELEMENT_NODE) {
                Element fillElem = (Element) fillNode;
                Map<String, Object> fill = (Map<String, Object>) data.get("fill");
                chkCssParameters(fillElem, fill);
                NodeList cssParameterList = fillElem.getElementsByTagName("sld:CssParameter");
                for (int i = 0; i < cssParameterList.getLength(); i++) {
                    Element elem = (Element) cssParameterList.item(i);
                    String name = elem.getAttribute("name");
                    if (fill.get(name) != null) {
                        elem.setTextContent((String) fill.get(name));
                    }
                }
            }
        }

        // 바뀐 값으로 sld 재생성
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource domSource = new DOMSource(rootElement);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        StreamResult streamResultresult = new StreamResult(byteArrayOutputStream);
        transformer.transform(domSource, streamResultresult);

        String transformedContent = byteArrayOutputStream.toString("UTF-8");

        return String.format(transformedContent);

    }

    private void chkCssParameters(Element parentElement, Map<String, Object> parameters){
        NodeList cssParameterList = parentElement.getElementsByTagName("sld:CssParameter");
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String name = entry.getKey();
            boolean found = false;
            // 요소가 있는지 확인
            for (int i = 0; i < cssParameterList.getLength(); i++) {
                Element elem = (Element) cssParameterList.item(i);
                if (elem.getAttribute("name").equals(name)) {
                    found = true;
                    break;
                }
            }

            // 없으면 요소 추가
            if (!found) {
                Element newElem = parentElement.getOwnerDocument().createElement("sld:CssParameter");
                newElem.setAttribute("name", name);
                newElem.setTextContent("");
                parentElement.appendChild(newElem);
            }
        }
    }

    @Override
    public Map<String, Object> getStyColor(String styId) throws Exception {
        String sld = reader.getSLD(styId);
        Map<String, Object> styData = new HashMap<>();
        styData.put("stroke", null);
        styData.put("fill", null);
        styData.put("stroke-width", null);
        styData.put("stroke-dasharray", false);
        styData.put("fill-opacity", null);

        Element rootElement = getElementBySld(sld);

        NodeList cssParameterList = rootElement.getElementsByTagName("sld:CssParameter");
        for (int i = 0; i < cssParameterList.getLength(); i++) {
            Element elem = (Element) cssParameterList.item(i);
            String paramName = elem.getAttribute("name");
            if (styData.containsKey(paramName)) {
                if ("stroke".equals(paramName) || "fill".equals(paramName)) {
                    String color = elem.getTextContent();
//                    if (color.isEmpty()) {
//                        color = "#FFFFFF";
//                    }
                    if (color.equals("")){
                        color = null;
                    }
                    styData.put(paramName, color);
                } else if ("fill-opacity".equals(paramName)) {
                    double percentValue = (1 - Double.parseDouble(elem.getTextContent())) * 100;
                    String percentText = String.format("%.0f%%", percentValue);
                    styData.put(paramName, percentText);
                } else if ("stroke-dasharray".equals(paramName)) {
                    if (!elem.getTextContent().equals("")){
                        styData.put(paramName, true);
                    }else{
                        styData.put(paramName, false);
                    }
                } else if ("stroke-width".equals(paramName)) {
                    styData.put(paramName, elem.getTextContent());
                }
            }
        }
        return styData;
    }

    @Override
    public Element getElementBySld(String sld) throws Exception {
        InputStream inputStream = new ByteArrayInputStream(sld.getBytes());

        // XML 파서 생성
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        // XML 파싱
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        // rootElement return
        return doc.getDocumentElement();
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
