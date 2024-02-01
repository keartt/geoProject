package geoProject.mmap.web;

import egovframework.rte.fdl.idgnr.EgovIdGnrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import geoProject.file.service.FileService;
import geoProject.file.service.FileVO;
import geoProject.file.service.impl.FileDAO;
import geoProject.mmap.service.geoService;
import geoProject.mmap.service.mService;
import geoProject.util.EgovFileMngUtil;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.*;

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

    @RequestMapping("/test2.do")
    public String test2(Model model, MultipartHttpServletRequest multiRequest){
        String userId = multiRequest.getParameter("id");
        boolean isSave = Boolean.parseBoolean(multiRequest.getParameter("isSave"));
        System.out.println(userId);
        System.out.println(isSave);
        String test = multiRequest.getParameter("test");
        if(test != null){
            System.out.println(test);
        }
        return "jsonView";
    }

    @RequestMapping("/testAll.do")
    public String testAll(Model model, MultipartHttpServletRequest multiRequest) throws Exception {
        Map<String, Object> params = extractParams(multiRequest);
        String layerId = idGen.getNextStringId();           model.addAttribute("layerId", layerId);                     //layerId 생성

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

            // 파일 저장
            String userId = multiRequest.getParameter("userId");
            Map<String, MultipartFile> files = multiRequest.getFileMap();
            List<FileVO> fileList = egovFileMngUtil.parseFileInf(files, geoService.getWorkSpace(), layerId, userId);
            fileDAO.insertFileUpload(fileList);

            // 유저 아이디 저장소 확인 & 생성
            if (geoService.getReader().getDatastore(geoService.getWorkSpace(), userId) != null) {
                model.addAttribute("userNameStore", "EXIST-" + userId);
            } else {
                if (geoService.createDataStrore(userId)) {
                    model.addAttribute("createStore as username", "SUCCESS-" + userId);
                } else {
                    model.addAttribute("createStore as username", "FAIL");
                    return "jsonView";
                }
            }
            // 지오서버 레이어 발행
            if (geoService.publishLayer(userId, layerId)) {
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
    public String chkGeoStore(@RequestParam(value = "userId", defaultValue = "1111") String userId, Model model) throws MalformedURLException, URISyntaxException {
        if (geoService.getReader().getDatastore(geoService.getWorkSpace(), userId) != null) {
            model.addAttribute("store", "exist");
        } else {
            model.addAttribute("store", "nope");
            if (geoService.createDataStrore(userId)) {
                model.addAttribute("createStore", "success");
            } else {
                model.addAttribute("createStore", "fail");
            }
        }
        return "jsonView";
    }

    @Description("사용자 저장소에 PostGis 테이블에서 가져온 레이어 발행")
    @RequestMapping("/publishLayer.do")
    public String pbLayer(@RequestParam("userId") String userId, @RequestParam("layerId") String layerId, Model model) {
        // 레이어아이디로 > posgis 디비 만들어진 다음에
        // 사용자명으로 geoserver 저장소 만들어진 다음에
        if (geoService.publishLayer(userId, layerId)) {
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
