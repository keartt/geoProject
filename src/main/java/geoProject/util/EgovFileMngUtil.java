package geoProject.util;

import geoProject.file.service.FileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * @Class Name  : EgovFileMngUtil.java
 * @Description : 메시지 처리 관련 유틸리티
 * @Modification Information
 *
 *     수정일         수정자                   수정내용
 *     -------          --------        ---------------------------
 *   2009.02.13       이삼섭                  최초 생성
 *   2011.08.09       서준식                  utl.fcc패키지와 Dependency제거를 위해 getTimeStamp()메서드 추가
 *   2017.03.03 	     조성원 	            시큐어코딩(ES)-부적절한 예외 처리[CWE-253, CWE-440, CWE-754]
 * @author 공통 서비스 개발팀 이삼섭
 * @since 2009. 02. 13
 * @version 1.0
 * @see
 *
 */
@Component("egovFileMngUtil")
public class EgovFileMngUtil {

	private static final Logger LOGGER = LoggerFactory.getLogger(EgovFileMngUtil.class);
	
	public static final int BUFF_SIZE = 2048;

	/**
	 * 첨부파일에 대한 목록 정보를 취득한다.
	 *
	 * @param files
	 * @return
	 * @throws Exception
	 */
	public List<FileVO> parseFileInf(Map<String, MultipartFile> files, String workType, String atchfileId, String userId) throws Exception {
		// 톰캣 위치한 곳 상위폴더
		String tomcatUpPath = Paths.get("").toAbsolutePath().getParent().getParent().toString();
		String atchfilePath = tomcatUpPath + "/" + workType + "/" + userId; //경로
		File saveFolder = new File(EgovWebUtil.filePathBlackList(atchfilePath));
		if (!saveFolder.exists() || saveFolder.isFile()) {
			//2017.03.03 	조성원 	시큐어코딩(ES)-부적절한 예외 처리[CWE-253, CWE-440, CWE-754]
			if (saveFolder.mkdirs()){
				LOGGER.debug("[file.mkdirs] saveFolder : Creation Success ");
			}else{
				LOGGER.error("[file.mkdirs] saveFolder : Creation Fail ");
			}
		}	

		Iterator<Entry<String, MultipartFile>> itr = files.entrySet().iterator();
		MultipartFile file;
		String filePath = "";
		FileVO fileVO;
		List<FileVO> result = new ArrayList<FileVO>();
		
		while (itr.hasNext()) {
			Entry<String, MultipartFile> entry = itr.next();
			file = entry.getValue();
			String orginFileName = file.getOriginalFilename();

			//--------------------------------------
			// 원 파일명이 없는 경우 처리
			// (첨부가 되지 않은 input file type)
			//--------------------------------------
			if ("".equals(orginFileName)) {
				continue;
			}
			////------------------------------------

			int index = orginFileName.lastIndexOf(".");
			String atchfileOrgnlNm = orginFileName.substring(0, index);
			String atchfileExtn = orginFileName.substring(index + 1);
			String atchfileNm = getTimeStamp();
			long atchfileSz = file.getSize();
						
			//파일경로 지정
			if (!"".equals(orginFileName)) {
				filePath = atchfilePath + "/" + atchfileNm + "." + atchfileExtn;
				file.transferTo(new File(filePath)); //물리경로 저장
			}
			
			fileVO = new FileVO();
			fileVO.setAtchfileId(atchfileId);
			fileVO.setAtchfileNm(atchfileNm);
			fileVO.setAtchfileOrgnlNm(atchfileOrgnlNm);
			fileVO.setAtchfilePath(atchfilePath);
			fileVO.setAtchfileExtn(atchfileExtn);
			fileVO.setAtchfileSz(Long.toString(atchfileSz));
			fileVO.setUserId(userId);
			result.add(fileVO);
		}
		return result;
	}

	/**
	 * 공통 컴포넌트 utl.fcc 패키지와 Dependency제거를 위해 내부 메서드로 추가 정의함
	 * 응용어플리케이션에서 고유값을 사용하기 위해 시스템에서17자리의TIMESTAMP값을 구하는 기능
	 *
	 * @param
	 * @return Timestamp 값
	 * @see
	 */
	private static String getTimeStamp() {
		String rtnStr = null;
		// 문자열로 변환하기 위한 패턴 설정(년도-월-일 시:분:초:초(자정이후 초))
		String pattern = "yyyyMMddhhmmssSSS";
		SimpleDateFormat sdfCurrent = new SimpleDateFormat(pattern, Locale.KOREA);
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		rtnStr = sdfCurrent.format(ts.getTime());
		return rtnStr;
	}
}

