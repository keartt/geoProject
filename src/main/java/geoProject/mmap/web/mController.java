package geoProject.mmap.web;

import egovframework.rte.fdl.idgnr.EgovIdGnrService;
import it.geosolutions.geoserver.rest.decoder.RESTFeatureType;
import it.geosolutions.geoserver.rest.decoder.RESTLayer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.postgis.PostgisNGJNDIDataStoreFactory;
import org.geotools.data.shapefile.ShapefileDumper;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Description;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import geoProject.file.service.FileService;
import geoProject.file.service.FileVO;
import geoProject.file.service.impl.FileDAO;
import geoProject.mmap.service.geoService;
import geoProject.mmap.service.mService;
import geoProject.util.EgovFileMngUtil;

import javax.annotation.Resource;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static geoProject.mmap.service.myUtil.getPostgisInfo;

@Controller
public class mController {
    @Resource(name = "mService")
    private mService mService;

    @Resource(name = "fileService")
    private FileService fileService;

    @Resource(name = "geoService")
    private geoService geoService;

    @Resource(name = "idGnrMmap")
    private EgovIdGnrService idGen;

    @Resource(name = "fileDAO")
    private FileDAO fileDAO;

    @Resource(name = "egovFileMngUtil")
    private EgovFileMngUtil egovFileMngUtil;
    private static final Logger LOGGER = LoggerFactory.getLogger(mController.class);

    @Resource(name = "globalProperties")
    Properties globalProperties;


    @RequestMapping("/testP.do")
    public String getLayerPreview(Model model, @RequestParam String layerName, @RequestParam String workspace) {
        String base64Image = geoService.getLayerPreviewImg(workspace, layerName);
        model.addAttribute("img64", base64Image);
        return "jsonView";
    }

    @RequestMapping("/testD.do")
    public ResponseEntity<byte[]> downloadShpZip(@RequestParam String layerName) {

        // Database connection parameters
        Map<String, Object> params = new HashMap<>();
        params.put(PostgisNGJNDIDataStoreFactory.DBTYPE.key, "postgis");
        params.put(PostgisNGJNDIDataStoreFactory.HOST.key, "localhost");
        params.put(PostgisNGJNDIDataStoreFactory.PORT.key, 5432);
        params.put(PostgisNGJNDIDataStoreFactory.SCHEMA.key, "public");
        params.put(PostgisNGJNDIDataStoreFactory.DATABASE.key, "postgis");
        params.put(PostgisNGJNDIDataStoreFactory.USER.key, "postgres");
        params.put(PostgisNGJNDIDataStoreFactory.PASSWD.key, "1234");
//        Map<String, Object> params = getPostgisInfo(globalProperties);
        try {
            // Getting DataStore
            DataStore dataStore = DataStoreFinder.getDataStore(params);

            // Define the table to download
            FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(layerName);

            // Create directory if it doesn't exist
            Path directoryPath = Paths.get("D:\\scdtw\\" + layerName);
            Files.createDirectories(directoryPath);

            // ShapefileDumper 설정
            ShapefileDumper dumper = new ShapefileDumper(directoryPath.toFile());
            dumper.setCharset(Charset.forName("EUC-KR"));
            int maxSize = 100 * 1024 * 1024;
            dumper.setMaxDbfSize(maxSize);

            // 데이터 덤프
            SimpleFeatureCollection fc = (SimpleFeatureCollection) source.getFeatures();
            dumper.dump(fc);

            // ZIP 파일로 압축
            Path zipFilePath = Paths.get("D:\\scdtw\\" + layerName + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
                Files.walk(directoryPath)
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            try (FileInputStream fis = new FileInputStream(file.toFile())) {
                                ZipEntry zipEntry = new ZipEntry(directoryPath.relativize(file).toString());
                                zos.putNextEntry(zipEntry);
                                byte[] bytes = new byte[1024];
                                int length;
                                while ((length = fis.read(bytes)) >= 0) {
                                    zos.write(bytes, 0, length);
                                }
                                zos.closeEntry();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }

            // HTTP 응답 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", layerName + "_output.zip");

            return new ResponseEntity<>(Files.readAllBytes(zipFilePath), headers, HttpStatus.OK);

        } catch (IOException e) {
            e.printStackTrace();
            // 예외 처리 로직 추가
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping("/testAll.do")
    public String testAll(Model model, MultipartHttpServletRequest multiRequest) throws Exception {
        Map<String, Object> params = extractParams(multiRequest);
        String layerId = idGen.getNextStringId();           model.addAttribute("layerId", layerId);                     //layerId 생성
        String workspace = "mmap"; // multiRequest.getParameter("workspace")

        params.put("layerId", layerId);
        int result = mService.insertLayerInfo(params);      model.addAttribute("layer Table Create", result);           // layer 정보
        int result2 = mService.insertPublicDept(params);    model.addAttribute("Dept Table Insert Count", result2);     // layer 공개범위

        Iterator<String> fileNamesIterator = multiRequest.getFileNames();
        if (!fileNamesIterator.hasNext()) {
            model.addAttribute("error", "file is none");
        } else {
            MultipartFile file = multiRequest.getFile(fileNamesIterator.next());
            String[] geomType = mService.getGeomType(file, layerId);
            model.addAttribute("Shp File geomType", geomType[0]);    // 지옴타입 반환
            model.addAttribute("create Table by Shp", geomType[1]);    // 디비생성 성공여부

            String userId = multiRequest.getParameter("userId");
            /*// 파일 저장
            Map<String, MultipartFile> files = multiRequest.getFileMap();
            List<FileVO> fileList = egovFileMngUtil.parseFileInf(files, workspace, layerId, userId);
            fileDAO.insertFileUpload(fileList);*/

            // 유저 아이디 저장소 확인 & 생성
            if (geoService.getReader().getDatastore(workspace, userId) != null) {
                model.addAttribute("userNameStore", "EXIST-" + userId);
            } else {
                if (geoService.createDataStrore(userId, workspace)) {
                    model.addAttribute("createStore as username", "SUCCESS-" + userId);
                } else {
                    model.addAttribute("createStore as username", "FAIL");
                    return "jsonView";
                }
            }
            // 지오서버 레이어 발행
            if (geoService.publishLayer(userId, layerId, workspace)) {
                model.addAttribute("publishLayer by Table", "SUCCESS");
            } else {
                model.addAttribute("publishLayer by Table", "FAIL");
            }
        }

        return "jsonView";
    }

    public Map<String, Object> extractParams(MultipartHttpServletRequest multiRequest) {
        Map<String, Object> params = new HashMap<>();

        Enumeration<String> paramNames = multiRequest.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            // 값이 배열 형태이면 모든 값을 배열로 가져옴
            String[] paramValues = multiRequest.getParameterValues(paramName);

            // 값이 배열 형태일 경우 배열 그대로, 아니면 첫 번째 값만 저장
            Object paramValue = (paramValues != null && paramValues.length > 1) ? paramValues : (paramValues != null && paramValues.length == 1) ? paramValues[0] : null;

            params.put(paramName, paramValue);
        }
        return params;
    }

    @Description("사용자 레이어 등록 및 공개 부서 설정")
    @RequestMapping("/insertShpLayer.do")
    public String insertLayerInfo(Model model, @RequestBody Map<String, Object> params) {
        try {
            String layerId = idGen.getNextStringId();

            params.put("layerId", layerId);
            int result = mService.insertLayerInfo(params);          // layer 정보
            int result2 = mService.insertPublicDept(params);        // layer 공개범위
            System.out.println("result1 : " + result + ", result2: " + result2);
            model.addAttribute("layerId", layerId);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

        return "jsonView";
    }

    @Description("사용자 레이어 파일 읽고 타입 반환")
    @RequestMapping("/readShpFile.do")
    public String readShpFile(@RequestParam("shpFile") MultipartFile shpFile, Model model) throws IOException {
        String layerId = "testDBnameLayerId";
        String[] geomType = mService.getGeomType(shpFile, layerId);
        model.addAttribute("geomType", geomType[0]);
        model.addAttribute("ctResult", geomType[1]);
        return "jsonView";
    }

    @Description("SHP ZIP 파일 저장 및 DB 생성")
    @RequestMapping("/insertFileUpload.do")
    public String insertFileUpload(Model model, MultipartHttpServletRequest multiRequest) {
        try {
            fileService.insertFileUpload(multiRequest);         // zip file save in directory

            String layerId = multiRequest.getParameter("atchfileId");
            String userId = multiRequest.getParameter("userId");

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
        return "jsonView";
    }


    @Description("사용자명 지오서버 저장소 확인 및 없으면 PostGis 저장소 생성")
    @RequestMapping("/chkGeoStore.do")
    public String chkGeoStore(@RequestParam(value = "userId", defaultValue = "1111") String userId, @RequestParam(value = "workspace", defaultValue = "mmap") String workspace, Model model) throws MalformedURLException, URISyntaxException {
        if (geoService.getReader().getDatastore(workspace, userId) != null) {
            model.addAttribute("store", "exist");
        } else {
            model.addAttribute("store", "nope");
            if (geoService.createDataStrore(userId, workspace)) {
                model.addAttribute("createStore", "success");
            } else {
                model.addAttribute("createStore", "fail");
            }
        }
        return "jsonView";
    }

    @Description("사용자 저장소에 PostGis 테이블에서 가져온 레이어 발행")
    @RequestMapping("/publishLayer.do")
    public String pbLayer(@RequestParam("userId") String userId, @RequestParam("layerId") String layerId, @RequestParam(value = "workspace", defaultValue = "mmap") String workspace, Model model) {
        // 레이어아이디로 > posgis 디비 만들어진 다음에
        // 사용자명으로 geoserver 저장소 만들어진 다음에
        if (geoService.publishLayer(userId, workspace, layerId)) {
            model.addAttribute("publishLayer", "success");
        } else {
            model.addAttribute("publishLayer", "fail");
        }
        return "jsonView";
    }


    @Description("사용자 좌표 받아서 테이블 생성 ")
    @RequestMapping("/createTableCoord.do")
    public String createTableCoord(@RequestBody Map<String, Object> params) {
        return "jsonView";
    }


}
