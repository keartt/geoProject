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

}

