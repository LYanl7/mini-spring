package mini.spring;

import mini.spring.IoC.ApplicationContext;
import mini.spring.sub.HelloController;
import mini.spring.sub.HelloInterceptor;

public class Main {
    public static void main(String[] args) throws Exception {
        ApplicationContext context = new ApplicationContext("mini.spring");
    }
}
