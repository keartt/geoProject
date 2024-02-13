package geoProject.mmap.web;

import com.sun.org.apache.xpath.internal.operations.Mod;
import egovframework.rte.fdl.idgnr.EgovIdGnrService;
import geoProject.mmap.service.geoService;
import geoProject.mmap.service.mService;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.FileNameMap;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static geoProject.mmap.service.myUtil.extractParams;

@Controller
public class mController {
    @Resource(name = "mService")
    private mService mService;

    @Resource(name = "geoService")
    private geoService geoService;

    @Resource(name = "idGnrMmap")
    private EgovIdGnrService idGen;

    private static final Logger LOGGER = LoggerFactory.getLogger(mController.class);

    @PostMapping("/publishStyles.do")
    public String publishStyles(@RequestParam("file") MultipartFile file, Model model) {
        if (file.isEmpty()) {
            model.addAttribute("error","File is empty");
        }

        List<String> successResults = new ArrayList<>();
        List<String> failureResults = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                String entryName = zipEntry.getName();
                if (!zipEntry.isDirectory() && entryName.endsWith(".sld") && !entryName.startsWith("._")) {
                    String originalFileName = new File(entryName).getName();

                    File tempFile = File.createTempFile(originalFileName, ".sld");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        int len;
                        byte[] buffer = new byte[1024];
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    boolean publishResult = geoService.getPublisher().publishStyle(tempFile, originalFileName);
                    if (publishResult) {
                        successResults.add(originalFileName);
                    } else {
                        failureResults.add(originalFileName);
                    }
                    // 임시 파일 삭제
                    tempFile.delete();
                }
                zis.closeEntry();
            }
            // 결과를 배열로 변환
            String[] successArray = successResults.toArray(new String[0]);
            String[] failureArray = failureResults.toArray(new String[0]);

            // 결과를 모델에 추가
            model.addAttribute("successResults", successArray);
            // 스타일 테이블에 추가
            mService.insertSty(successArray);
            model.addAttribute("failureResults", failureArray);
        } catch (IOException e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }
        return "jsonView";
    }

    @RequestMapping("/testAll.do")
    public String testAll(Model model, MultipartHttpServletRequest multiRequest) throws Exception {
        Map<String, Object> params = extractParams(multiRequest);
        String layerId = idGen.getNextStringId();
        params.put("layerId", layerId);

        Map<String, Object> logs = new HashMap<>();
        Map<String, Object> errors = new HashMap<>();

        String userId = multiRequest.getParameter("userId");
        String workspace = multiRequest.getParameter("workType");
        String styleName = multiRequest.getParameter("styleName");

        int result = mService.insertLayerInfo(params);
        int result2 = mService.insertPublicDept(params);

        logs.put("layerId:", layerId);                  //layerId 생성
        logs.put("layerTableCreate", (result == 1));     // layer 정보
        logs.put("DeptTableInsertCount:", result2);     // layer 공개범위

        Iterator<String> fileNamesIterator = multiRequest.getFileNames();
        if (!fileNamesIterator.hasNext()) {
            errors.put("FIlE", false);
        } else {
            MultipartFile file = multiRequest.getFile(fileNamesIterator.next());
            String[] geomType = mService.getGeomType(file, layerId);
            logs.put("geomType", geomType[0]);           // 지옴타입 반환
            logs.put("createTablebyShp", geomType[1]);   // 디비생성 성공여부
            if (geomType[0].equals("Point")) {
                styleName = null;
            }
            logs.put("prjFile", geomType[2]);            // prj 여부*/

            // 유저 아이디 저장소 확인 & 생성
            if (geoService.getReader().getDatastore(workspace, userId) == null) {
                if (geoService.createDataStrore(userId, workspace)) {
                    logs.put("createStore", userId);
                } else {
                    errors.put("GEO_STORE", false);
                }
            }
            // 지오서버 레이어 발행
            if (geoService.publishLayer(userId, workspace, layerId, styleName)) {
                logs.put("publishLayer", layerId);
                // 미리보기 이미지
//                logs.put("img64:",geoService.getLayerPreviewImg(workspace, layerId));
            } else {
                errors.put("GEO_LAYER_PUBLISH", false);
            }
        }
        model.addAttribute("logs", logs)
                .addAttribute("errors", errors);

        return "jsonView";
     }


    @Description("레이어 파일 읽고 타입과 스타일 목록 반환")
    @RequestMapping("/readShpFile.do")
    public String readShpFile(@RequestParam("shpFile") MultipartFile shpFile, Model model) throws IOException {
        String layerId = "testDBnameLayerId";
        String[] geomType = mService.getGeomType(shpFile, layerId);
        model.addAttribute("geomType", geomType[0]);
        if (geomType[0] != null){
            // 지옴 타입에 맞춰서 스타일 조회 후 반환 ㅇㄹㅇㄹㅇ ㅇ라어림ㅇ러 ㅇㄴㄹㅇ니ㅏㄹ

            model.addAttribute("styleList", null);
        }
//        model.addAttribute("ctResult", geomType[1]);
        return "jsonView";
    }

    @Description("shp 파일 업로드")
    @RequestMapping("/uploadShpFile.do")
    public String uploadShpFile(Model model, MultipartHttpServletRequest multiRequest) throws IOException {
//        1. 파일 읽어서 shp 파일 테이블 생성 (userId + reg_dt)
//        2. 유저 아이디 저장소 확인
//        3. 지오서버 레이어 발행
//        4. 지오서버 미리보기 이미지 반환 'img'
//        5. 업무 타입에 따라 'param' select
//            5-1. 행정지도일 경우  dept table 에 insert 하고 user_id + 현재시간 + 제목,내용
//            5-2. 다른 테이블일 경우 user_id, reg_dt
//        7. 레이어 테이블 생성
//            isnert 레이어아이디 + 'param' + 'img' + user_id + 업무코드 + 스타일명
//        8. 성공, 실패 각 내용들 map 으로 담아서

        // 성공 실패 로그 담아서 출력
        Map<String, Object> logs = new HashMap<>();
        Map<String, Object> errors = new HashMap<>();

        String userId = multiRequest.getParameter("user_id");
        String workspace = multiRequest.getParameter("code");
        String layerId = userId + multiRequest.getParameter("reg_dt");
        String styleName = multiRequest.getParameter("sty_id");

        Iterator<String> fileNamesIterator = multiRequest.getFileNames();
        if (!fileNamesIterator.hasNext()) {
            errors.put("FIlE", false);
        } else {
            MultipartFile file = multiRequest.getFile(fileNamesIterator.next());
            String[] geomType = mService.getGeomType(file, layerId);
            logs.put("createTablebyShp", geomType[1]);   // 디비생성 성공여부
            if (geomType[0].equals("Point")) {
                styleName = null;
            }

            // 유저 아이디 저장소 확인 & 생성
            if (geoService.getReader().getDatastore(workspace, userId) == null) {
                if (geoService.createDataStrore(userId, workspace)) {
                    logs.put("createStore", userId);
                } else {
                    errors.put("GEO_STORE", false);
                    return "jsonView";
                }
            }
            // 지오서버 레이어 발행
            if (!geoService.publishLayer(userId, workspace, layerId, styleName)) {
                errors.put("GEO_LAYER_PUBLISH", false);
            } else {
                logs.put("publishLayer", layerId);
                logs.put("previewImg", geoService.insertPreviewImg(workspace, layerId));
                if (workspace.equals("mmap")) {
                    Map<String, Object> params = extractParams(multiRequest);
                    logs.put("DeptTableInsertCount:", mService.insertPublicDept(params));     // layer 공개범위
                }
                // scdtw_file_uld_lyr_mng 테이블 insert

            }
        }
        model.addAttribute("logs", logs);
        model.addAttribute("errors", errors);
        return "jsonView";
    }


    @Description("레이어 미리보기 이미지 List")
    @RequestMapping("/getLayerPreviewImgList.do")
    public String getLayerPreviewImgList(Model model, @RequestBody Map<String, Object> data) {

        // select 아이디, 블롭 from 레이어테이블 where 아이디 in
        // <foreach item="item" index="index" collection="data" open="(" separator="," close=")">
        // #{item} </foreach>

        model.addAttribute("imgList", null);
        return "jsonView";
    }

    @Description("테이블명으로 zip 파일 다운로드")
    @RequestMapping(value = "/testD.do", produces = "application/zip")
    public void downloadShpZip(@RequestParam String layerName, HttpServletResponse response) {
        mService.returnShpZip(layerName, response);
    }

//    // 레이어 미리보기 이미지 크기 줄인 후 blob 으로 저장 / base 64 로 리턴
//    @RequestMapping("/blob.do")
//    public String getLayerPreviewImgBlob(@RequestParam String workspace, @RequestParam String layerName, Model model) {
//        String previewUrl = "http://localhost:8088/geoserver/wms/reflect?layers=" + workspace + ":" + layerName + "&format=image/png";
//        HttpClient httpClient = HttpClients.createDefault();
//        byte[] pngImage = null;
//        try {
//            HttpGet getRequest = new HttpGet(previewUrl);
//            HttpResponse response = httpClient.execute(getRequest);
//
//            if (response.getStatusLine().getStatusCode() == HttpStatus.OK.value()) {
//                pngImage = EntityUtils.toByteArray(response.getEntity());
//                Map<String, Object> params = new HashMap<>();
//                params.put("layerName", layerName);
//
//                byte[] compressImage = resizeImage(pngImage);
//                params.put("bdata", compressImage);
//                mService.insertBlob(params);
//            } else {
//                System.err.println("Failed to obtain layer preview. HTTP Error Code: " + response.getStatusLine().getStatusCode());
//                return "Failed to obtain layer preview. HTTP Error Code: " + response.getStatusLine().getStatusCode();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            return "Internal Server Error";
//        } finally {
//            // Close the HttpClient
//            httpClient.getConnectionManager().shutdown();
//        }
//
//        byte[] retrievedImage = mService.getBlob(layerName);
//        model.addAttribute("img", retrievedImage);
//
//        return "jsonView";
//    }

//    /**
//     * 사용자명 지오서버 저장소 확인 및 없으면 PostGis 저장소 생성
//     */
//    @RequestMapping("/chkGeoStore.do")
//    public String chkGeoStore(@RequestParam(value = "userId", defaultValue = "1111") String userId, @RequestParam(value = "workspace", defaultValue = "mmap") String workspace, Model model) throws MalformedURLException, URISyntaxException {
//        if (geoService.getReader().getDatastore(workspace, userId) != null) {
//            model.addAttribute("store", "exist");
//        } else {
//            model.addAttribute("store", "nope");
//            if (geoService.createDataStrore(userId, workspace)) {
//                model.addAttribute("createStore", "success");
//            } else {
//                model.addAttribute("createStore", "fail");
//            }
//        }
//        return "jsonView";
//    }
//
//    /**
//     * 사용자 저장소에 PostGis 테이블에서 가져온 레이어 발행
//     */
//    @RequestMapping("/publishLayer.do")
//    public String pbLayer(@RequestParam("userId") String userId, @RequestParam("layerId") String layerId,
//                          @RequestParam(value = "workspace", defaultValue = "mmap") String workspace,
//                          @RequestParam("styleName") String styleName, Model model) {
//        // 레이어아이디로 > posgis 디비 만들어진 다음에
//        // 사용자명으로 geoserver 저장소 만들어진 다음에
//        if (geoService.publishLayer(userId, workspace, layerId, styleName)) {
//            model.addAttribute("publishLayer", "success");
//        } else {
//            model.addAttribute("publishLayer", "fail");
//        }
//        return "jsonView";
//    }
//
//    /**
//     * 레이어 미리보기 png -> base64 String
//     */
//    @RequestMapping("/testP.do")
//    public String getLayerPreview(Model model, @RequestParam String layerName, @RequestParam String workspace) {
//        String base64Image = geoService.getLayerPreviewImg(workspace, layerName);
//        model.addAttribute("img64", base64Image);
//        return "jsonView";
//    }

    @Description("사용자 좌표 받아서 테이블 생성 ")
    @RequestMapping("/createTableCoord.do")
    public String createTableCoord(@RequestBody Map<String, Object> params) {
        return "jsonView";
    }


}
