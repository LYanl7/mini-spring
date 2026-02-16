package mini.spring.IoC;

@FunctionalInterface
public interface ObjectFactory<T> {
    T getObject();
}
