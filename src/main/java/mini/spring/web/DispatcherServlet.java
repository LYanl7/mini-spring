package mini.spring.web;

import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        Class<?> targetClass = getTargetClass(bean);
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
                    // 获取bean实际类中的方法（如果是代理，需要获取代理类的方法）
                    Method beanMethod = getMethodFromBean(bean, method);
                    if (handlerMap.put(path, new WebHandler(bean, beanMethod)) != null) {
                        throw new RuntimeException("controller 定义重复" + path);
                    }
                });
        return bean;
    }

    /**
     * 从bean实例中获取对应的方法
     * 如果bean是代理对象，需要从代理类中获取方法
     */
    private Method getMethodFromBean(Object bean, Method targetMethod) {
        try {
            return bean.getClass().getMethod(targetMethod.getName(), targetMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            // 如果找不到，返回原方法
            return targetMethod;
        }
    }

    /**
     * 获取目标类，处理代理对象的情况
     * 如果bean是代理对象（通过AOP生成），则返回其父类（原始类）
     */
    private Class<?> getTargetClass(Object bean) {
        Class<?> clazz = bean.getClass();
        // 检查是否是ByteBuddy生成的代理类或其他代理类
        // 代理类通常会有特殊的类名或者继承自原始类
        if (clazz.getName().contains("$ByteBuddy$") ||
            clazz.getName().contains("$$") ||
            clazz.getName().contains("$Proxy")) {
            // 返回父类（原始类）
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return superClass;
            }
        }
        return clazz;
    }

}
