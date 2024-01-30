package geoProject.file.service.impl;

import egovframework.rte.psl.dataaccess.EgovAbstractMapper;
import egovframework.rte.psl.dataaccess.util.EgovMap;
import org.springframework.stereotype.Repository;
import geoProject.file.service.FileVO;

import java.util.Iterator;
import java.util.List;

@Repository("fileDAO")
public class FileDAO extends EgovAbstractMapper {

	/**
	 * 
	* <PRE>
	* 간략 : 첨부파일 등록
	* 상세 : .
	* <PRE>
	* @param fileList
	* @return
	* @throws Exception
	 */
	public void insertFileUpload(List<FileVO> fileList) throws Exception {
		Iterator<?> iter = fileList.iterator();
		while (iter.hasNext()) {
			FileVO fileVO = (FileVO) iter.next();
			insert("fileDAO.insertFileUpload", fileVO);
		}
	}

	/**
	 * 
	* <PRE>
	* 간략 : 첨부파일 목록 조회
	* 상세 : .
	* <PRE>
	* @param fileList
	* @return
	* @throws Exception
	 */
	public List<EgovMap> selectFileList(FileVO fileVO) throws Exception {
		return selectList("fileDAO.selectFileList", fileVO);
	}
	
	/**
	 * 
	* <PRE>
	* 간략 : 첨부파일 삭제
	* 상세 : .
	* <PRE>
	* @param fileVO
	* @return
	* @throws Exception
	 */
	public int deleteFile(EgovMap params) throws Exception {
		return (int)update("fileDAO.deleteFile", params);
	}
	
}