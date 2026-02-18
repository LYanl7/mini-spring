package mini.spring.sub;

import mini.spring.IoC.Component;
import mini.spring.web.Controller;
import mini.spring.web.ModelAndView;
import mini.spring.web.Param;
import mini.spring.web.RequestMapping;

@Component
@Controller
@RequestMapping("/hello")
public class HelloController {

    @RequestMapping("/world")
    public String helloWorld() {
        return "Hello, World!";
    }

    @RequestMapping("/a")
    public String helloA(@Param("name") String name) {
        return "Hello, " + name + "!";
    }

    @RequestMapping("/html")
    public ModelAndView helloHtml() {
        ModelAndView mav = new ModelAndView();
        mav.setView("index.html");
        return mav;
    }
}