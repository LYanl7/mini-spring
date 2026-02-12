package mini.spring.AOP;

import org.aspectj.weaver.tools.PointcutExpression;

import java.lang.reflect.Method;

public class Advisor {
    private final Object aspectBean;
    private final Method adviceMethod;
    private final String adviceType;
    private final PointcutExpression pointcutExpression;

     public Advisor(Object aspectBean, Method adviceMethod, String adviceType, PointcutExpression pointcutExpression) {
         this.aspectBean = aspectBean;
         this.adviceMethod = adviceMethod;
         this.adviceType = adviceType;
         this.pointcutExpression = pointcutExpression;
     }

    public Object getAspectBean() {
        return aspectBean;
    }

    public PointcutExpression getPointcutExpression() {
        return pointcutExpression;
    }

    public Method getAdviceMethod() {
        return adviceMethod;
    }

    public String getAdviceType() {
        return adviceType;
    }
}
