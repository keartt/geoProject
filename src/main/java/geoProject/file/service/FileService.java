package geoProject.file.service;

import egovframework.rte.psl.dataaccess.util.EgovMap;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;

public interface FileService {
	
	/**
	 * 
	* <PRE>
	* 간략 : 첨부파일 등록
	* 상세 : .
	* <PRE>
	* @param multiRequest
	* @param userId
	* @throws Exception
	 */
	public void insertFileUpload(MultipartHttpServletRequest multiRequest) throws Exception;
	
	/**
	 * 
	* <PRE>
	* 간략 : 첨부파일 목록 조회
	* 상세 : .
	* <PRE>
	* @param fileVO
	* @return
	* @throws Exception
	 */
	public List<EgovMap> selectFileList(FileVO fileVO) throws Exception;

	/**
	 * 
	* <PRE>
	* 간략 : 첨부파일 삭제
	* 상세 : .
	* <PRE>
	* @param fileVO
	* @throws Exception
	 */
	public void deleteFile(FileVO fileVO) throws Exception;
	
}
