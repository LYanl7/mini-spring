package mini.spring.sub;

import mini.spring.AOP.Aspect;
import mini.spring.AOP.Before;
import mini.spring.IoC.Component;

@Aspect
@Component
public class LogAspect {

    @Before("execution(* mini.spring.sub.*.*(..))")
    public void before() {
        System.out.println("----这是一条分割线----");
    }
}
