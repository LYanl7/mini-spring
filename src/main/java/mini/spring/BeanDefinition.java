package mini.spring;

import java.lang.reflect.Constructor;

public class BeanDefinition {
    private String name;
    private Constructor<?> constructor;

    public BeanDefinition(Class<?> type) {
        Component component = type.getAnnotation(Component.class);
        name = component.name().isEmpty() ? type.getSimpleName() : component.name();
        try {
            constructor = type.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }

    public Constructor<?> getConstructor() {
        return constructor;
    }
}
