package geoProject.file.service;

import java.io.Serializable;

/**
 * @Class Name : FileVO.java
 * @Description : 파일정보 처리를 위한 VO 클래스
 * @Modification Information
 *
 *    수정일       수정자         수정내용
 *    -------        -------     -------------------
 *    2009. 03. 25.		이삼섭
 *
 * @author 공통 서비스 개발팀 이삼섭
 * @since 2009. 3. 25.
 * @version
 * @see
 *
 */
@SuppressWarnings("serial")
public class FileVO implements Serializable {
	/**
     * 첨부파일 아이디
     */
    public String atchfileId = "";
    /**
     * 파일연번
     */
    public String atchfileSn = "";
    /**
     * 저장파일명
     */
    public String atchfileNm = "";
    /**
     * 원파일명
     */
    public String atchfileOrgnlNm = "";
    /**
     * 파일저장경로
     */
    public String atchfilePath = "";
    /**
     * 파일 인코딩
     */
    public String atchfileEncd = "";
    /**
     * 파일확장자
     */
    public String atchfileExtn = "";
    /**
     * 파일크기
     */
    public String atchfileSz = "";
    /**
     * 사용자 아이디
     */
    public String userId = "";
    
	public String getAtchfileId() {
		return atchfileId;
	}
	public void setAtchfileId(String atchfileId) {
		this.atchfileId = atchfileId;
	}
	
	public String getAtchfileSn() {
		return atchfileSn;
	}
	public void setAtchfileSn(String atchfileSn) {
		this.atchfileSn = atchfileSn;
	}
	
	public String getAtchfileNm() {
		return atchfileNm;
	}
	public void setAtchfileNm(String atchfileNm) {
		this.atchfileNm = atchfileNm;
	}
	
	public String getAtchfileOrgnlNm() {
		return atchfileOrgnlNm;
	}
	public void setAtchfileOrgnlNm(String atchfileOrgnlNm) {
		this.atchfileOrgnlNm = atchfileOrgnlNm;
	}
	
	public String getAtchfilePath() {
		return atchfilePath;
	}
	public void setAtchfilePath(String atchfilePath) {
		this.atchfilePath = atchfilePath;
	}
	
	public String getAtchfileEncd() {
		return atchfileEncd;
	}
	public void setAtchfileEncd(String atchfileEncd) {
		this.atchfileEncd = atchfileEncd;
	}
	
	public String getAtchfileExtn() {
		return atchfileExtn;
	}
	public void setAtchfileExtn(String atchfileExtn) {
		this.atchfileExtn = atchfileExtn;
	}
	
	public String getAtchfileSz() {
		return atchfileSz;
	}
	public void setAtchfileSz(String atchfileSz) {
		this.atchfileSz = atchfileSz;
	}
	
	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	
}
