package mini.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface HandlerInterceptor {
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) throws Exception;
    public void postHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Object result) throws Exception;
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Exception ex) throws Exception;
}
