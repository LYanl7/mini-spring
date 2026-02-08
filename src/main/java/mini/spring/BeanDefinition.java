package mini.spring;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BeanDefinition {
    private String name;
    private Constructor<?> constructor;
    private Method postConstructMethod;
    private List<Field> autowiredFields;
    private Class<?> beanType;

    public BeanDefinition(Class<?> type) {
        Component component = type.getAnnotation(Component.class);
        this.name = component.name().isEmpty() ? type.getSimpleName() : component.name();
        this.beanType = type;
        try {
            this.autowiredFields =
                    Arrays.stream(type.getDeclaredFields())
                            .filter(field -> field.isAnnotationPresent(Autowired.class))
                            .toList();
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

    public List<Field> getAutowiredFields() {
        return this.autowiredFields;
    }

    public Class<?> getBeanType() {
        return beanType;
    }
}
