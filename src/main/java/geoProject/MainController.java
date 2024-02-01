package geoProject;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class MainController {

	// mapping by index.jsp:forward
	@RequestMapping("/main.do")
	public String mainPage() {
		return "main";
	}

	@RequestMapping("/shp2geoJS.do")
	public String shp2geoJs() {
		return "shp2geoJS";
	}

}

