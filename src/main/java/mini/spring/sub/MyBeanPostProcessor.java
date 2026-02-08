package mini.spring.sub;

import mini.spring.IoC.BeanPostProcessor;
import mini.spring.IoC.Component;

@Component
public class MyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object beforeInitialization(Object bean, String beanName) {
        System.out.println(beanName + "初始化成功");
        return bean;
    }
}
