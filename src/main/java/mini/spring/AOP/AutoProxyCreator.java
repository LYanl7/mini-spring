package mini.spring.AOP;

import mini.spring.IoC.BeanPostProcessor;
import mini.spring.IoC.Component;
import org.aspectj.weaver.tools.PointcutExpression;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Component
public class AutoProxyCreator implements BeanPostProcessor {
    private final List<Advisor> advisors = new ArrayList<>();

    @Override
    public Object beforeInitialization(Object bean, String beanName) {
        return BeanPostProcessor.super.beforeInitialization(bean, beanName);
    }

    @Override
    public Object afterInitialization(Object bean, String beanName) {
        for (Method declaredMethod : bean.getClass().getDeclaredMethods()) {
            for (Advisor advisor : advisors) {
                PointcutExpression pointcutExpression = advisor.getPointcutExpression();
                boolean hit = !bean.getClass().isAnnotationPresent(Aspect.class) &&
                        AspectJPointcutExpressionParser.matchesMethodExecution(pointcutExpression, declaredMethod);
                Method adviceMethod = advisor.getAdviceMethod();

                if (!hit) {
                    continue;
                }

                Object aspectBean = advisor.getAspectBean();
                bean = DynamicProxyFactory.createProxy(bean, invocation -> {
                    adviceMethod.setAccessible(true);
                    try {
                        return switch (advisor.getAdviceType()) {
                            case "before" -> { adviceMethod.invoke(aspectBean); yield invocation.proceed(); }
                            case "after"  -> { try { yield invocation.proceed(); } finally { adviceMethod.invoke(aspectBean); } }
                            case "around" -> adviceMethod.invoke(aspectBean, invocation);
                            default       -> invocation.proceed();
                        };
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
            }
        }

        return bean;
    }

    public void addAdvisors(List<Advisor> advisors) {
        this.advisors.addAll(advisors);
    }

}
