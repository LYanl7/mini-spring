package mini.spring;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

public class BeanDefinition {
    private String name;
    private Constructor<?> constructor;
    private Method postConstructMethod;

    public BeanDefinition(Class<?> type) {
        Component component = type.getAnnotation(Component.class);
        this.name = component.name().isEmpty() ? type.getSimpleName() : component.name();
        try {
            this.constructor = type.getConstructor();
            this.postConstructMethod =
                    Arrays.stream(type.getDeclaredMethods())
                            .filter(method -> method.isAnnotationPresent(PostConstruct.class))
                            .findFirst()
                            .orElse(null);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return this.name;
    }

    public Constructor<?> getConstructor() {
        return this.constructor;
    }

    public Method getPostConstructMethod() {
        return this.postConstructMethod;
    }
}
