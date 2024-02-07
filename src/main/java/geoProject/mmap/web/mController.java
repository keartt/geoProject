package geoProject.mmap.web;

import egovframework.rte.fdl.idgnr.EgovIdGnrService;
import geoProject.mmap.service.geoService;
import geoProject.mmap.service.mService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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

    @RequestMapping("/blob.do")
    public ResponseEntity<byte[]> getLayerPreviewImgBlob(@RequestParam String workspace, @RequestParam String layerName) {
        String previewUrl = "http://localhost:8088/geoserver/wms/reflect?layers=" + workspace + ":" + layerName + "&format=image/png";
        HttpClient httpClient = HttpClients.createDefault();
        byte[] pngImage = null;
        try {
            HttpGet getRequest = new HttpGet(previewUrl);
            HttpResponse response = httpClient.execute(getRequest);

            if (response.getStatusLine().getStatusCode() == HttpStatus.OK.value()) {
                pngImage = EntityUtils.toByteArray(response.getEntity());
                Map<String, Object> params = new HashMap<>();
                params.put("layerName", layerName);

                byte[] compressImage = resizeImage(pngImage);
                params.put("bdata", compressImage);
                mService.insertBlob(params);
            } else {
                System.err.println("Failed to obtain layer preview. HTTP Error Code: " + response.getStatusLine().getStatusCode());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } finally {
            // Close the HttpClient
            httpClient.getConnectionManager().shutdown();
        }

        byte[] retrievedImage = mService.getBlob(layerName);

        return ResponseEntity.ok(retrievedImage);
    }

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

        logs.put("layerId:",layerId);                  //layerId 생성
        logs.put("layerTableCreate",(result ==1));     // layer 정보
        logs.put("DeptTableInsertCount:",result2);     // layer 공개범위

        Iterator<String> fileNamesIterator = multiRequest.getFileNames();
        if (!fileNamesIterator.hasNext()) {
            errors.put("FIlE",false);
        } else {
            MultipartFile file = multiRequest.getFile(fileNamesIterator.next());
            String[] geomType = mService.getGeomType(file, layerId);
            logs.put("geomType",geomType[0]);           // 지옴타입 반환
            logs.put("createTablebyShp",geomType[1]);   // 디비생성 성공여부
            if(geomType[0].equals("Point")){
                styleName = null;
            }
            logs.put("prjFile",geomType[2]);            // prj 여부*/

            // 유저 아이디 저장소 확인 & 생성
            if (geoService.getReader().getDatastore(workspace, userId) == null) {
                if (geoService.createDataStrore(userId, workspace)) {
                    logs.put("createStore",userId);
                } else {
                    errors.put("GEO_STORE",false);
                }
            }
            // 지오서버 레이어 발행
            if (geoService.publishLayer(userId, workspace, layerId, styleName)) {
                logs.put("publishLayer",layerId);
                // 미리보기 이미지
//                logs.put("img64:",geoService.getLayerPreviewImg(workspace, layerId));
            } else {
                errors.put("GEO_LAYER_PUBLISH",false);
            }
        }
        model.addAttribute("LOG", logs);
        model.addAttribute("ERROR", errors);

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

    @Description("테이블명으로 zip 파일 다운로드")
    @RequestMapping(value = "/testD.do", produces = "application/zip")
    public void downloadShpZip(@RequestParam String layerName, HttpServletResponse response) {
        mService.returnShpZip(layerName, response);
    }

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
