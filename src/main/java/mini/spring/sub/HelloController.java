package mini.spring.sub;

import mini.spring.IoC.Component;
import mini.spring.web.Controller;
import mini.spring.web.RequestMapping;

@Component
@Controller
@RequestMapping("/hello")
public class HelloController {

    @RequestMapping("/world")
    public String helloWorld() {
        return "Hello, World!";
    }
}
