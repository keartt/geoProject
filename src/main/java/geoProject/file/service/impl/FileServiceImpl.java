package geoProject.file.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import egovframework.rte.psl.dataaccess.util.EgovMap;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import geoProject.file.service.FileService;
import geoProject.file.service.FileVO;
import geoProject.util.EgovFileMngUtil;

import javax.annotation.Resource;
import java.io.File;
import java.util.List;
import java.util.Map;

@Service("fileService")
public class FileServiceImpl extends EgovAbstractServiceImpl implements FileService {
	
	@Resource(name="fileDAO")
	private FileDAO fileDAO;
	
	@Resource(name="egovFileMngUtil")
	private EgovFileMngUtil egovFileMngUtil;
	
	/**
	* 간략 : 첨부파일 등록
	 */
	@Override
	public void insertFileUpload(MultipartHttpServletRequest multiRequest) throws Exception{

		//첨부파일 관련
		final Map<String, MultipartFile> files = multiRequest.getFileMap();
		
		//atchFileId 첨부파일 아이디가 있는지 없는지 확인 
		Object atchfileId = multiRequest.getParameter("atchfileId");
		if(atchfileId != null && !"".equals(atchfileId.toString())) {
			String userId = multiRequest.getParameter("userId").toString();
			String workType = multiRequest.getParameter("workType").toString();
			
			if(files.isEmpty()) {
				return;
			}
			
			List<FileVO> fileList = egovFileMngUtil.parseFileInf(files, workType, atchfileId.toString(), userId);
			fileDAO.insertFileUpload(fileList);
		}
	}
	
	/**
	* 간략 : 첨부파일 목록 조회
	 */
	@Override
	public List<EgovMap> selectFileList(FileVO fileVO) throws Exception {
		return fileDAO.selectFileList(fileVO);
	}
	
	/**
	* 간략 : 첨부파일 삭제
	 */
	@Override
	public void deleteFile(FileVO fileVO) throws Exception{
		//첨부파일 목록이 있는지 확인
		List<EgovMap> fileList = fileDAO.selectFileList(fileVO);		
		if(fileList.size() > 0){
			for(int i=0; i<fileList.size(); i++){
				fileDAO.deleteFile((EgovMap) fileList.get(i));
				
				//파일 삭제
				File file = new File(fileList.get(i).get("atchfilePath").toString(), fileList.get(i).get("atchfileNm").toString() +"."+ fileList.get(i).get("atchfileExtn").toString());
				file.delete();
			}
		}
	}	
}
