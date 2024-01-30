package geoProject.file.web;

import org.springframework.context.annotation.Description;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springmodules.validation.commons.DefaultBeanValidator;
import geoProject.file.service.FileService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

@Controller
public class FileController {

	/** Validator */
	@Resource(name = "beanValidator")
	protected DefaultBeanValidator beanValidator;

	@Resource(name="globalProperties")
	Properties globalProperties;	
	
	@Resource(name="fileService")
    private FileService fileService;

	@Description("첨부파일 업로드")
	@RequestMapping("/file/insertFileUpload.do")
	public String insertFileUpload(MultipartHttpServletRequest multiRequest, HttpServletRequest request){
		try{
			fileService.insertFileUpload(multiRequest);
		}catch(Exception e){
			e.printStackTrace();
		}
		return "jsonView";
	}

	@Description("이미지 파일 미리보기")
	@RequestMapping("/file/previewImgFile.do")
	@ResponseBody
	public ResponseEntity<byte[]> previewImgFile(String atchfilePath, String atchfileNm) {
		File file = new File(atchfilePath + File.separatorChar, atchfileNm);
	    ResponseEntity<byte[]> result = null;
	    try {
	        HttpHeaders headers = new HttpHeaders();
	        headers.add("Content-Type", Files.probeContentType(file.toPath()));
	        result = new ResponseEntity<>(FileCopyUtils.copyToByteArray(file), headers, HttpStatus.OK);
	    }catch (IOException e) {
	        e.printStackTrace();
	    }
	    return result;
	}
	
}
