package mini.spring.web;

import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mini.spring.AOP.DynamicProxyFactory;
import mini.spring.IoC.BeanPostProcessor;
import mini.spring.IoC.Component;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
public class DispatcherServlet extends HttpServlet implements BeanPostProcessor {

    private Map<String, WebHandler> handlerMap = new HashMap<>();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        WebHandler handler = findHandler(req);
        if (handler == null) {
            resp.setContentType("text/html;charset=UTF-8");
            resp.getWriter().write("404 Not Found");
            return;
        }
        try {
            Object controllerBean = handler.getControllerBean();
            Method method = handler.getMethod();
            Object result = method.invoke(controllerBean);
            switch (handler.getResultType()) {
                case JSON -> {
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write(JSONObject.toJSONString(result));
                }
                case HTML -> {
                    resp.setContentType("text/html;charset=UTF-8");
                    resp.getWriter().write(result.toString());
                }
                case LOCAL -> {

                }
            }

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private WebHandler findHandler(HttpServletRequest req) {
        String path = req.getRequestURI();
        return handlerMap.get(path);
    }

    @Override
    public Object afterInitialization(Object bean, String beanName) {
        Class<?> targetClass = DynamicProxyFactory.getOriginalClass(bean);
        if (!targetClass.isAnnotationPresent(Controller.class)) {
            return bean;
        }
        RequestMapping classRm = targetClass.getAnnotation(RequestMapping.class);
        String basePath = classRm == null ? "" : classRm.value();
        Arrays.stream(targetClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(RequestMapping.class))
                .forEach(method -> {
                    RequestMapping methodRm = method.getAnnotation(RequestMapping.class);
                    String path = basePath + methodRm.value();
                    if (handlerMap.put(path, new WebHandler(bean, method)) != null) {
                        throw new RuntimeException("controller 定义重复" + path);
                    }
                });
        return bean;
    }
}
