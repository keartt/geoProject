package geoProject.web;

import egovframework.rte.fdl.idgnr.EgovIdGnrService;
import geoProject.service.GeoService;
import geoProject.service.ShpService;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import org.springframework.context.annotation.Description;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Base64Utils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Controller
public class ShpController {

    @Resource(name = "shpService")
    private ShpService shpService;

    @Resource(name = "geoService")
    private GeoService geoService;

    @Resource(name = "idGnrLyst")
    private EgovIdGnrService idgenStyService;

    @Description("SHP 파일 정보 확인")
    @RequestMapping("/getGeomType.do")
    public String getGeomType(@RequestParam("file") MultipartFile shpFile, Model model) {
        String[] fileProperty;
        try {
            fileProperty = shpService.getGeomType(shpFile);
            String geomType = fileProperty[0].toLowerCase();
            model.addAttribute("hasPrj", fileProperty[1]);
            model.addAttribute("styList", shpService.getStyleList(geomType));
            model.addAttribute("geomType", geomType);
        } catch (IOException | RuntimeException e) {
            model.addAttribute("error", e.toString());
        }
        return "jsonView";
    }

    @Description("레이어 파일 업로드")
    @RequestMapping("/uploadShpFile.do")
    public String uploadShpFile(MultipartHttpServletRequest multiRequest, Model model) {
        String userId = multiRequest.getParameter("user_id").toLowerCase();
        String workspace = multiRequest.getParameter("task_se_nm");
        String styId = multiRequest.getParameter("sty_id");
        String regDt = multiRequest.getParameter("reg_dt");
        regDt = regDt.length() > 14 ? regDt.substring(0, 14) : regDt;
        String lyrId = userId + "_" + regDt;

        try {
            if (!geoService.chkWorkspace(workspace)){
                throw new IOException("작업공간 생성실패.");
            }
            String schema = shpService.getSchema(workspace, userId);
            if (!shpService.chkSchema(schema)) {
                throw new IOException("스키마 생성 실패");
            }

            Iterator<String> fileNamesIterator = multiRequest.getFileNames();
            if (!fileNamesIterator.hasNext()) {
                throw new IOException("파일이 없습니다.");
            }
            MultipartFile file = multiRequest.getFile(fileNamesIterator.next());

            String storeName;
            // 관리필요한 레이어일 경우 기존 레이어 변경 로직 추가
            if(workspace.equals("scdtw")){
                storeName = workspace;
                lyrId = multiRequest.getParameter("lyr_id");
                // 기존 레이어 존재할 경우
                if (geoService.getReader().existsLayer(workspace, lyrId, true) && shpService.existTable(lyrId, schema)) {
                    // 삭제 전에 원본 스타일 아이디 get
                    String oriStyId = geoService.getReader().getLayer(workspace, lyrId).getDefaultStyle();
                    // 지오서버 삭제
                    geoService.getPublisher().removeLayer(workspace, lyrId);
                    // 삭제했으면 새로고침 한번 때려야함
                    geoService.getPublisher().reload();
                    String oldLyrNm = lyrId + "_" + regDt;
                    Map<String, Object> params = new HashMap<>();
                    params.put("ori", lyrId);
                    params.put("old", oldLyrNm);
                    params.put("user", userId);
                    params.put("schema", schema);
                    // 기존 테이블 명 변경
                    shpService.alterTblNm(params);
                    // 기존 테이블 지오서버 재발행
                    if (!geoService.publishLayer(storeName, workspace, oldLyrNm, oriStyId, null)) {
                        throw new IOException("기존 레이어 geoserver 발행 실패");
                    }
                    // 기존 파일 업로드 테이블 업데이트
                    shpService.updateFilUldLyrId(params);
                    model.addAttribute("old_lyr_id", oldLyrNm);
                }
            }
            else{
                storeName = userId;
                if (geoService.getReader().existsLayer(workspace, lyrId, true) && shpService.existTable(lyrId, schema)) {
                    throw new IOException("이미 존재하는 레이어/테이블 명입니다");
                }

                if (geoService.getReader().getDatastore(workspace, userId) == null) {
                    if (!geoService.createDataStrore(userId, workspace, schema)) {
                        throw new IOException("geoserver 저장소 생성 실패");
                    }
                }
            }

            String[] geomType = shpService.createTableByShp(file, lyrId, schema);
            if (geomType == null || geomType.length < 1) {
                throw new IOException("테이블생성실패");
            }

            if (!geoService.publishLayer(storeName, workspace, lyrId, styId, geomType[0].toLowerCase())) {
                throw new IOException("신규 레이어 geoserver 발행 실패");
            }

            // 미리보기 이미지1
            byte[] img = geomType[0].equals("point") ? null : geoService.getPreviewImg(workspace, lyrId) ;
            if(shpService.insertFileUldTable(lyrId, workspace, img, styId, userId, geomType[0].toLowerCase()) <= 0){
                throw new IOException("레이어관리 테이블 INSERT 실패");
            }
            model.addAttribute("lyr_id", lyrId);

        } catch (IOException e) {
            model.addAttribute("error", e.getMessage());
        }

        return "jsonView";
    }

    @Description("레이어 파일 다운로드")
    @RequestMapping(value = "/downloadShpFile.do", produces = "application/zip")
    public ResponseEntity<String> downloadShpFile(@RequestParam("lyr_id") String lyrId, HttpServletResponse response) {
        try {
            String result = shpService.downloadShpFile(lyrId, response, shpService.getSchema(lyrId));
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
    public String deleteLyr(@RequestParam("lyr_id") String lyrId,
                            HttpSession session, Model model) throws MalformedURLException{
        // 레이어 등록자 or 관리자 페이지 접속자일경우 삭제 가능
        if(shpService.chkAuth(session, lyrId) || session.getAttribute("admin") != null){
            Map<String, Object> data = shpService.selectUldLyrOne(lyrId);
            if (data == null){
                model.addAttribute("error", "이미 삭제된 테이블입니다.");
                return "jsonView";
            }
            String regId = (String) data.get("reg_id");
            String workspace = (String) data.get("task_se_nm");
            String schema = shpService.getSchema(workspace, regId);
            data.put("schema", schema);

            model.addAttribute("delete", shpService.deleteUldLyr(data));
            String styId = shpService.getStyIdByLyr(lyrId); // 기본제공 아닌 스타일만 get
            if( styId!= null && shpService.isStyDefault(styId)){
                model.addAttribute("style", shpService.deleteSty(styId) && geoService.getPublisher().removeStyle(styId));
            }
            if (geoService.getReader().existsLayer(workspace, lyrId, true)) {
                model.addAttribute("geoserver", geoService.getPublisher().removeLayer(workspace, lyrId));
            }
            if (shpService.existTable(lyrId, schema)) {
                model.addAttribute("drop", shpService.dropUldLyr(data));
            }
            // 최종 삭제 후 지오서버 (저장소 새로고침)
            geoService.getPublisher().reloadStore(workspace, regId, GeoServerRESTPublisher.StoreType.DATASTORES);
        }else {
            model.addAttribute("error", "권한이 없습니다");
        }
        return "jsonView";
    }

    @Description("업로드 레이어 정보보기 (컬럼명 & 내용)")
    @RequestMapping("/selectUldLyrOne.do")
    public String selectUldLyrOne (Model model, @RequestParam("lyr_id") String lyrId){
        // 데이터 들어있는 테이블 컬럼명 for input
        model.addAttribute("columList", shpService.selectLyrColums(lyrId));
        // 레이어 관리 테이블 속 데이터 내용
        model.addAttribute("data", shpService.selectUldLyrOne(lyrId));
        return "jsonView";
    }

    @Description("행정지도 사용자 입력 테이블 생성")
    @RequestMapping("/createUserLyr.do")
    public String createUserLyr (Model model, @RequestBody Map<String, Object> data, HttpSession session){
        String regDt =  (String) data.get("reg_dt");
        String regId = ((String) session.getAttribute("LOGIN_ID")).toLowerCase();
        String lyrId = (regId + "_"+  regDt);
        data.put("lyr_id", lyrId);
        data.put("reg_id", regId );

        if (shpService.insertFileUldTable(lyrId, "pmap", null,  (String) data.get("sty_id"), regId, "point") > 0) {
            if(shpService.createUserLyr(data)){
                model.addAttribute("success", lyrId);
            } else{
                model.addAttribute("error", "fail createTable");
            }
        } else{
            model.addAttribute("error", "fail inserTable");
        }
        return "jsonView";
    }

    @Description("행정지도 사용자 레이어 포인트 추가")
    @RequestMapping("/insertUserLyrOne.do")
    public String insertUserLyrOne (Model model, @RequestBody Map<String, Object> data, HttpSession session){
        if(shpService.chkAuth(session, (String) data.get("lyr_file_id"))){
            data.put("user_id",((String) session.getAttribute("LOGIN_ID")).toLowerCase());
            model.addAttribute("insert", shpService.insertUserLyrOne(data) > 0);
        }else {
            model.addAttribute("error", "권한이 없습니다");
        }
        return "jsonView";
    }


    @Description("사용자 스타일 생성 / 수정")
    @RequestMapping("/addCustomSty.do")
    public String CustomSty(Model model, @RequestBody Map<String, Object> data, HttpSession session) throws MalformedURLException {
        String styId = (String) data.get("sty_id");
        String geomType = ((String) data.get("geomType")).toLowerCase();
        String lyrId = null;
        if(data.get("lyr_id")!= null){
            // lyrId 가 null 이 아닐경우 수정상황임
            lyrId = (String) data.get("lyr_id");
        }
        String regId = ((String) session.getAttribute("LOGIN_ID")).toLowerCase();
        data.put("reg_id",regId);

        String sld = geoService.getReader().getSLD(styId);
        try {
            String convertSld = geoService.addCustomSty(sld, geomType, data);
            if (lyrId != null){
                String lyrStyId = shpService.getStyIdByLyr(lyrId);
                // 기본 스타일이 아닐때
                if (!shpService.isStyDefault(lyrStyId)){
                    styId = lyrStyId;
                    data.put("sty_id", styId);
                    shpService.updateSty(data);
                    geoService.getPublisher().updateStyle(convertSld, styId);
                }else{
                    styId = idgenStyService.getNextStringId();
                    data.put("sty_id", styId);
                    data.put("lyr_type_nm", geomType);
                    geoService.getPublisher().publishStyle(convertSld, styId);
                    shpService.insertNewSty(data);
                }
                String workspace = (String) data.get("workspace");
                shpService.updateLyrPrvwImg(lyrId, regId, geoService.getPreviewImg(workspace, lyrId));
            }else{
                styId = idgenStyService.getNextStringId();
                data.put("sty_id", styId);
                data.put("lyr_type_nm", geomType);
                geoService.getPublisher().publishStyle(convertSld, styId);
                shpService.insertNewSty(data);
            }
            model.addAttribute("sty_id", styId);
        } catch (Exception e) {
            model.addAttribute("error", e.toString());
        }
        return "jsonView";
    }

    @Description("스타일 색상 get")
    @RequestMapping("/getStyData.do")
    public String getStyColor (Model model, @RequestParam("id") String styId) throws MalformedURLException {
        if (!geoService.getReader().existsStyle(styId)) {
            model.addAttribute("error", "스타일이 존재하지 않습니다");
        } else {
            try {
                model.addAttribute("styData", geoService.getStyColor(styId));
            } catch (Exception e) {
                model.addAttribute("error", "스타일이 파싱 실패" + e.toString());
            }
        }
        return "jsonView";
    }

    @Description("레이어 이미지 가져오기")
    @RequestMapping("/getLyrImg.do")
    public String getLyrImg (Model model, @RequestParam("lyr_id") String lyrId){
        String styId = shpService.getStyIdByLyr(lyrId);
        if (styId != null){
            if (!shpService.isStyDefault(styId)) {
                Map<String, Object> lyrImg = shpService.getLyrImg(lyrId);
                if (lyrImg != null){
                    String img = Base64Utils.encodeToString((byte[]) lyrImg.get("lyr_prvw_img_blob"));
                    model.addAttribute("img", img);
                }else{
                    model.addAttribute("error", "스타일 미리보기가 없습니다");
                }
            }else{
                model.addAttribute("sty", styId);
            }
        }else{
            model.addAttribute("error", "스타일이 없습니다.");
        }
        return "jsonView";
    }

    @Description("shp 파일 포인트 레이어 정보 조회")
    @RequestMapping("/selectUldPointLyrOne.do")
    public String selectUldPointLyrOne(Model model, @RequestParam("lyr_id") String lyrId, @RequestParam(value = "rprs_col_nm" , required = false) String column) {
        model.addAttribute("dataList", shpService.selectUldPointLyrOne(lyrId, column));
        return "jsonView";
    }

}
