package geoProject.pmap.web;

import egovframework.rte.fdl.cmmn.exception.FdlException;
import egovframework.rte.fdl.idgnr.EgovIdGnrService;
import org.springframework.context.annotation.Description;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import geoProject.pmap.service.GeoService;
import geoProject.pmap.service.PMapService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Controller
@RequestMapping("pmap")
public class PMapController {

    @Resource(name = "pmapService")
    private PMapService pMapService;

    @Resource(name = "geoService")
    private GeoService geoService;

    @Resource(name = "idGnrLyst")
    private EgovIdGnrService idgenStyService;

    @Description("SHP 파일 정보 확인")
    @RequestMapping("/getGeomType.do")
    public String getGeomType(@RequestParam("file") MultipartFile shpFile, Model model) {
        String[] fileProperty;
        try {
            fileProperty = pMapService.getGeomType(shpFile);
            String geomType = fileProperty[0];
            model.addAttribute("hasPrj", fileProperty[1]);
            model.addAttribute("styList", pMapService.getStyleList(geomType));
            model.addAttribute("geomType", geomType);
        } catch (IOException | RuntimeException e) {
            model.addAttribute("error", e.toString());
        }

        return "jsonView";
    }

    @Description("레이어 파일 업로드")
    @RequestMapping("/uploadShpFile.do")
    public String uploadShpFile(MultipartHttpServletRequest multiRequest, Model model) {
        String user_id = multiRequest.getParameter("user_id");
        String workspace = multiRequest.getParameter("task_se_nm");
        String sty_id = multiRequest.getParameter("sty_id");

        String reg_dt;
        if (workspace.equals("pmap")) {
            reg_dt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        } else {
            reg_dt = multiRequest.getParameter("reg_dt");
        }
        reg_dt = reg_dt.length() > 14 ? reg_dt.substring(0, 14) : reg_dt;

        String lyr_id = user_id + "_" + reg_dt;

        try {
            if (geoService.getReader().existsLayer(workspace, lyr_id, true) || pMapService.existTable(lyr_id)) {
                throw new IOException("이미 존재하는 레이어/테이블 명입니다:");
            }
            model.addAttribute("lyr_id", lyr_id);
            if (geoService.getReader().getDatastore(workspace, user_id) == null) {
                if (!geoService.createDataStrore(user_id, workspace)) {
                    throw new IOException("geoserver 저장소 생성 실패:");
                }
            }

            Iterator<String> fileNamesIterator = multiRequest.getFileNames();
            MultipartFile file = multiRequest.getFile(fileNamesIterator.next());


            String createTableByShp = pMapService.createTableByShp(file, lyr_id);
            if (createTableByShp != null) {
                throw new IOException("테이블생성실패:" + createTableByShp);
            }


            if (!geoService.publishLayer(user_id, workspace, lyr_id, sty_id)) {
                throw new IOException("geoserver 레이어 발행 실패:");
            }

            // 미리보기 이미지1
            byte[] img = geoService.getPreviewImg(workspace, lyr_id);

            try {
                pMapService.insertFileUldTable(multiRequest, lyr_id, img);
            } catch (ParseException e) {
                throw new IOException("레이어관리 테이블 INSERT 실패:");
            }
        } catch (IOException e) {
            model.addAttribute("error", e.toString());
        }

        return "jsonView";
    }

    @Description("레이어 파일 다운로드")
    @RequestMapping(value = "/downloadShpFile.do", produces = "application/zip")
    public ResponseEntity<String> downloadShpFile(@RequestParam("lyr_id") String lyr_id, HttpServletResponse response) {
        try {
            String result = pMapService.downloadShpFile(lyr_id, response);
            if (result != null) {
                throw new IOException(result);
            }
            return ResponseEntity.ok("Download successful");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.toString());
        }
    }

    @Description("SHP 레이어 삭제")
    @RequestMapping("/deleteLyr.do")
    public String deleteLyr(@RequestParam String lyr_id, @RequestParam("task_se_nm") String workspace,
                            HttpSession httpSession, Model model) throws MalformedURLException {
        String user_id = (String) httpSession.getAttribute("LOGIN_ID");
        String reg_id = lyr_id.substring(0, lyr_id.indexOf('_'));
        if(user_id.equals(reg_id)){
            if (geoService.getReader().existsLayer(workspace, lyr_id, true)) {
                model.addAttribute("geoserver", geoService.getPublisher().removeLayer(workspace, lyr_id));
            }
            if (pMapService.existTable(lyr_id)) {
                model.addAttribute("database", pMapService.dropShpTable(lyr_id, user_id));
            }
        }else{
            model.addAttribute("error", "삭제권한 없음");
        }

        return "jsonView";
    }

    //TODO [KSH] 사용자 스타일 생성 (수정)
    @RequestMapping("/addCustomSty.do")
    public String addCustomSty(Model model, @RequestBody Map<String, Object> data, HttpSession session) throws MalformedURLException {
        String oriStyId = (String) data.get("sty_id");
        String sldXml = geoService.getReader().getSLD(oriStyId);
        String sldType;

        int styId = Integer.parseInt(oriStyId.substring(4));
        if ( styId <= 22) {
            sldType = "MultiLineString";
        } else if ( styId <= 44) {
            sldType = "MultiPolygon";
        } else {
            sldType = "Point";
        }

        try {
            // XML 문자열을 InputStream으로 변환
            InputStream inputStream = new ByteArrayInputStream(sldXml.getBytes());

            // XML 파서 생성
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            // XML 파싱
            Document doc = dBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();

            // 루트 요소 가져오기
            Element rootElement = doc.getDocumentElement();

            data.putIfAbsent("sty_nm", "newSty");
            NodeList nameElements = rootElement.getElementsByTagName("sld:Name");
            for (int i = 0; i < nameElements.getLength(); i++) {
                Element elem = (Element) nameElements.item(i);
                elem.setTextContent((String) data.get("sty_nm"));
            }

            Node strokeNode = rootElement.getElementsByTagName("sld:Stroke").item(0);
            if (strokeNode.getNodeType() == Node.ELEMENT_NODE) {
                NodeList cssParameterList = ((Element) strokeNode).getElementsByTagName("sld:CssParameter");
                for (int j = 0; j < cssParameterList.getLength(); j++) {
                    Element strokeElem = (Element) cssParameterList.item(j);
                    String name = strokeElem.getAttribute("name");

                    if(data.get(name) != null){
                        strokeElem.setTextContent((String) data.get(name));
                    }

                    if(name.equals("stroke-dasharray") && data.get("dasharray") != null){
                        if((boolean) data.get("dasharray")){
                            strokeElem.setTextContent("");
                        }else{
                            strokeElem.setTextContent("2.0 7.0");
                        }
                    }
                }
            }

            if(!sldType.equals("MultiLineString")){
                Node fillNode = rootElement.getElementsByTagName("sld:Fill").item(0);
                if (fillNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element fillElement = (Element) fillNode;
                    boolean hasOpacity = false;
                    NodeList cssParameterList = fillElement.getElementsByTagName("sld:CssParameter");
                    for (int i = 0; i < cssParameterList.getLength(); i++) {
                        Element elem = (Element) cssParameterList.item(i);
                        String name = elem.getAttribute("name");
                        if (data.get(name) != null){
                            elem.setTextContent((String) data.get(name));
                        }
                        if(name.equals("opacity")){
                            hasOpacity = true;
                        }
                    }
                    if(!hasOpacity && data.get("opacity") != null) {
                        Element opacity = fillElement.getOwnerDocument().createElement("sld:CssParameter");
                        opacity.setAttribute("name", "opacity");
                        opacity.setTextContent((String) data.get("opacity"));
                        fillElement.appendChild(opacity);
                    }
                }
            }

            /*if(!sldType.equals("MultiLineString")){
                Node fillNode = rootElement.getElementsByTagName("sld:Fill").item(0);
                if (fillNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element fillElement = (Element) fillNode;
                    if(data.get("fill") != null){
                        Element elem = (Element) fillElement.getElementsByTagName("sld:CssParameter").item(0);
                        elem.setTextContent((String) data.get("fill"));
                    }

                    if(data.get("opacity") != null) {
                        Element opacity = fillElement.getOwnerDocument().createElement("sld:CssParameter");
                        opacity.setAttribute("name", "opacity");
                        opacity.setTextContent((String) data.get("opacity"));
                        fillElement.appendChild(opacity);
                    }
                }
            }*/

            if (sldType.equals("Point")){
                if(data.get("wellKnownName") != null){
                    Element wellKnownNameElement = (Element) rootElement.getElementsByTagName("sld:WellKnownName").item(0);
                    wellKnownNameElement.setTextContent((String) data.get("wellKnownName"));
                }
                if(data.get("size")!= null){
                    Element sldSize = (Element) rootElement.getElementsByTagName("sld:Size").item(0);
                    sldSize.setTextContent((String) data.get("size"));
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

            String finalSldString = String.format(transformedContent);
            // db insert
            String sty_id = idgenStyService.getNextStringId();
            data.put("sty_id", sty_id);
            data.put("reg_id", session.getAttribute("LOGIN_ID"));
            data.put("sty_type_nm", sldType);

            if(!geoService.getPublisher().publishStyle(finalSldString, sty_id)){
                throw new Exception("geoserver style create fail");
            }

            model.addAttribute("sty_id", sty_id);
            pMapService.insertNewSty(data);
        } catch (Exception e) {
            model.addAttribute("error", e.toString());
        }

        return "jsonView";
    }

    //TODO [KSH] 레이어 피쳐 수정

    //TODO [KSH] 사용자 입력값 포인트 테이블 생성

    @RequestMapping("/testsld.do")
    public String test (Model model) throws MalformedURLException {
        model.addAttribute("sld", geoService.getReader().getSLD("LYST000025"));
        return "jsonView";
    }

    @Description("기본 스타일 sld 파일 다운로드(.zip)")
    @RequestMapping(value = "/downloadDefaultStyles.do", produces = "application/zip")
    public void downloadDefaultStyles( HttpServletResponse response ) throws IOException {
        // ZIP 파일 생성을 위한 ByteArrayOutputStream 생성
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // ZIP 파일에 대한 출력 스트림 생성
        ZipOutputStream zipOut = new ZipOutputStream(baos);

        // 84개의 SLD 파일 생성
        for (int i = 1; i <= 84; i++) {
            String sldContent = geoService.getReader().getSLD(String.format("LYST%06d", i));
            String fileName = String.format("LYST%06d.sld", i);
            // SLD 파일 추가
            addFileToZip(zipOut, fileName, sldContent.getBytes());
        }

        // ZIP 파일 닫기
        zipOut.close();
        // ByteArrayOutputStream의 내용을 byte 배열로 변환
        byte[] zipBytes = baos.toByteArray();

        // HTTP 응답 헤더 설정
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=defaultStyle.zip");
        response.setContentLength(zipBytes.length);

        // ZIP 파일을 브라우저로 전송
        response.getOutputStream().write(zipBytes);
        response.flushBuffer();
    }

    private void addFileToZip(ZipOutputStream zipOut, String fileName, byte[] content) throws IOException {
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        zipOut.write(content);
        zipOut.closeEntry();
    }

    @Description("기본 스타일 sld 파일 업로드(.zip)")
    @RequestMapping("/uploadDefaultStyles.do")
    public String uploadDefaultStyles(@RequestParam("file") MultipartFile file, Model model) throws FdlException {
        // sld 파일 버전 1.0 이어야함
        List<Map<String, Object>> styList = new ArrayList<>();
        List<String> failureResults = new ArrayList<>();

        // 작업 디렉토리 생성
        File workDir = new File(System.getProperty("java.io.tmpdir"), "styles");
        if (!workDir.exists() && !workDir.mkdirs()) {
            model.addAttribute("error", "작업 디렉토리를 생성할 수 없습니다.");
            return "jsonView";
        }

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                String entryName = zipEntry.getName();
                if (!zipEntry.isDirectory() && entryName.endsWith(".sld") && !entryName.startsWith("._")) {
                    File outFile = new File(workDir, new File(entryName).getName());
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    Map<String, Object> styData = new HashMap<>();
                    String sty_id = idgenStyService.getNextStringId();
                    if (geoService.getPublisher().publishStyle(outFile, sty_id)) {
                        styData.put("sty_id", sty_id);
                        Pattern pattern = Pattern.compile("^\\d+_(.*?)_(.*?)\\.sld$");
                        Matcher matcher = pattern.matcher(outFile.getName());
                        if (matcher.find()) {
                            styData.put("sty_type_nm", matcher.group(1));
                            styData.put("sty_nm", matcher.group(2));
                        }
                        styList.add(styData);
                    } else {
                        failureResults.add(outFile.getName());
                    }
                }
                zis.closeEntry();
            }
            model.addAttribute("successCnt", pMapService.insertStyList(styList));
            model.addAttribute("success", styList);
            model.addAttribute("fail", failureResults.toArray(new String[0]));
        } catch (IOException e) {
            model.addAttribute("error", e.toString());
        } finally {
            // 작업 디렉토리 삭제
            deleteDirectory(workDir);
        }

        return "jsonView";
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}

