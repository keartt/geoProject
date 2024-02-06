package geoProject.mmap.web;

import egovframework.rte.fdl.idgnr.EgovIdGnrService;
import geoProject.file.service.FileService;
import geoProject.mmap.service.geoService;
import geoProject.mmap.service.mService;
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

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;

import static geoProject.mmap.service.myUtil.extractParams;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(mController.class);

    @Description("레이어 미리보기 png -> base64 String")
    @RequestMapping("/testP.do")
    public String getLayerPreview(Model model, @RequestParam String layerName, @RequestParam String workspace) {
        String base64Image = geoService.getLayerPreviewImg(workspace, layerName);
        model.addAttribute("img64", base64Image);
        return "jsonView";
    }

    @Description("테이블명으로 zip 파일 다운로드")
    @RequestMapping(value = "/testD.do", produces = "application/zip")
    public void downloadShpZip(@RequestParam String layerName, HttpServletResponse response) {
        mService.returnShpZip(layerName, response);
    }

    @RequestMapping("/testAll.do")
    public String testAll(Model model, MultipartHttpServletRequest multiRequest) throws Exception {
        Map<String, Object> params = extractParams(multiRequest);
        String layerId = idGen.getNextStringId();
        model.addAttribute("layerId", layerId);                     //layerId 생성
        String workspace = "mmap"; // multiRequest.getParameter("workspace")

        params.put("layerId", layerId);
        int result = mService.insertLayerInfo(params);
        model.addAttribute("layer Table Create", result);           // layer 정보
        int result2 = mService.insertPublicDept(params);
        model.addAttribute("Dept Table Insert Count", result2);     // layer 공개범위

        Iterator<String> fileNamesIterator = multiRequest.getFileNames();
        if (!fileNamesIterator.hasNext()) {
            model.addAttribute("error", "file is none");
        } else {
            MultipartFile file = multiRequest.getFile(fileNamesIterator.next());
            String[] geomType = mService.getGeomType(file, layerId);
            model.addAttribute("Shp File geomType", geomType[0]);    // 지옴타입 반환
            model.addAttribute("create Table by Shp", geomType[1]);    // 디비생성 성공여부
            model.addAttribute("has .prj file", geomType[2]);    // prj 여부

            String userId = multiRequest.getParameter("userId");
            String styleName = multiRequest.getParameter("styleName");
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
            if (geoService.publishLayer(userId, workspace, layerId, styleName)) {
                model.addAttribute("publishLayer by Table", "SUCCESS");
            } else {
                model.addAttribute("publishLayer by Table", "FAIL");
            }
        }

        return "jsonView";
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
    public String pbLayer(@RequestParam("userId") String userId, @RequestParam("layerId") String layerId,
                          @RequestParam(value = "workspace", defaultValue = "mmap") String workspace,
                          @RequestParam("styleName") String styleName, Model model) {
        // 레이어아이디로 > posgis 디비 만들어진 다음에
        // 사용자명으로 geoserver 저장소 만들어진 다음에
        if (geoService.publishLayer(userId, workspace, layerId, styleName)) {
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
