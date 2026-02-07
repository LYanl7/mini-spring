package mini.spring;

public class Main {
    public static void main(String[] args) throws Exception {
        ApplicationContext context = new ApplicationContext("mini.spring");
        System.out.print(context.getBean("Cat"));
    }

}
