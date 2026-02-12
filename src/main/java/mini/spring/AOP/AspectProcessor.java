package mini.spring.AOP;

import mini.spring.IoC.Autowired;
import mini.spring.IoC.BeanPostProcessor;
import mini.spring.IoC.Component;
import org.aspectj.weaver.tools.PointcutExpression;

import java.util.Arrays;
import java.util.List;

@Component
public class AspectProcessor implements BeanPostProcessor {
    @Autowired
    private AutoProxyCreator autoProxyCreator;

    @Override
    public Object beforeInitialization(Object bean, String beanName) {
        return BeanPostProcessor.super.beforeInitialization(bean, beanName);
    }

    @Override
    public Object afterInitialization(Object bean, String beanName) {
        if (!bean.getClass().isAnnotationPresent(Aspect.class)) return bean;

        List<Advisor> advisors = makeAdvisors(bean);
        autoProxyCreator.addAdvisors(advisors);

        return bean;
    }

    private List<Advisor> makeAdvisors(Object bean) {
        Class<?> aspectType = bean.getClass();
        return Arrays.stream(aspectType.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Before.class)
                        || method.isAnnotationPresent(After.class)
                        || method.isAnnotationPresent(Around.class))
                .map(method -> {
                    String adviceType;
                    PointcutExpression pointcutExpression;
                    if (method.isAnnotationPresent(Before.class)) {
                        Before before = method.getAnnotation(Before.class);
                        pointcutExpression = AspectJPointcutExpressionParser.parse(before.value(), aspectType);
                        adviceType = "before";
                    } else if (method.isAnnotationPresent(After.class)) {
                        After after = method.getAnnotation(After.class);
                        pointcutExpression = AspectJPointcutExpressionParser.parse(after.value(), aspectType);
                        adviceType = "after";
                    } else {
                        Around around = method.getAnnotation(Around.class);
                        pointcutExpression = AspectJPointcutExpressionParser.parse(around.value(), aspectType);
                        adviceType = "around";
                    }

                    return new Advisor(bean, method, adviceType, pointcutExpression);
                })
                .toList();
    }


}
