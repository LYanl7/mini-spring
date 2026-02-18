package mini.spring.web;

import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mini.spring.AOP.DynamicProxyFactory;
import mini.spring.IoC.Autowired;
import mini.spring.IoC.BeanPostProcessor;
import mini.spring.IoC.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@Component
public class DispatcherServlet extends HttpServlet implements BeanPostProcessor {

    @Autowired
    private List<HandlerInterceptor> interceptors = new ArrayList<>();
    private Map<String, WebHandler> handlerMap = new HashMap<>();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        WebHandler handler = findHandler(req);
        if (handler == null) {
            resp.setContentType("text/html;charset=UTF-8");
            resp.getWriter().write("404 Not Found");
            return;
        }
        Exception ex = null;

        try {
            for (HandlerInterceptor interceptor : interceptors) {
                if (!interceptor.preHandle(req, resp, handler)) {
                    return;
                }
            }

            Object controllerBean = handler.getControllerBean();
            Method method = handler.getMethod();
            Object[] args = resolveArgs(req, method);
            Object result = method.invoke(controllerBean, args);

            for (HandlerInterceptor interceptor : interceptors) {
                interceptor.postHandle(req, resp, handler, result);
            }

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
                    ModelAndView modelAndView = (ModelAndView) result;
                    String view = modelAndView.getView();
                    InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(view);
                    try(inputStream) {
                        String html = new String(inputStream.readAllBytes());
                        resp.setContentType("text/html;charset=UTF-8");
                        resp.getWriter().write(html);
                    }
                }
            }
        } catch (Exception e) {
            ex = e;
            throw new ServletException(e);
        } finally {
            for (HandlerInterceptor interceptor : interceptors) {
                try {
                    interceptor.afterCompletion(req, resp, handler, ex);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private Object[] resolveArgs(HttpServletRequest req, Method method) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Param param = parameter.getAnnotation(Param.class);
            String val;
            if (param != null) {
                val = req.getParameter(param.value());
            } else {
                val = req.getParameter(parameter.getName());
            }

            Class<?> paramType = parameter.getType();
            if (String.class.isAssignableFrom(paramType)) {
                args[i] = val;
            } else if (Integer.class.isAssignableFrom(paramType)) {
                args[i] = Integer.parseInt(val);
            } else {
                args[i] = null;
            }
        }

        return args;
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
