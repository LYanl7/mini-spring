package mini.spring.web;

import java.lang.reflect.Method;

public class WebHandler {
    private final Object controllerBean;
    private final Method method;
    private final ResultType resultType;

    public WebHandler(final Object controllerBean, final Method method) {
        this.controllerBean = controllerBean;
        this.method = method;
        this.resultType = determineResultType(controllerBean, method);
    }

    private ResultType determineResultType(Object controllerBean, Method method) {
        if (method.isAnnotationPresent(RequestBody.class)) {
            return ResultType.JSON;
        } else if (method.getReturnType().equals(ModelAndView.class)) {
            return ResultType.LOCAL;
        }
        return ResultType.HTML;
    }

    public Object getControllerBean() {
        return controllerBean;
    }

    public Method getMethod() {
        return method;
    }

    public ResultType getResultType() {
        return resultType;
    }

    enum ResultType {
        JSON, HTML, LOCAL
    }

}