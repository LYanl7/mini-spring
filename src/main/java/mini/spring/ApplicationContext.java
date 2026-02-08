package mini.spring;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationContext {

    private Map<String, Object> beanMap = new HashMap<>();
    private Map<String, Object> loadingBeanMap = new HashMap<>();
    private Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

    public ApplicationContext(String packageName) throws Exception {
        initApplicationContext(packageName);
    }

    private void initApplicationContext(String packageName) throws Exception {
        this.scanPackage(packageName).stream()
                .filter(this::scanCreate)
                .forEach(this::wrapper);
        this.beanDefinitionMap.values().forEach(this::createBean);
    }

    private List<Class<?>> scanPackage(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String packagePath = packageName.replace('.', File.separatorChar);
        URL resource = this.getClass().getClassLoader().getResource(packagePath);
        Path path = Paths.get(resource.toURI());
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path absoulutePath = file.toAbsolutePath();
                if (absoulutePath.toString().endsWith(".class")) {
                    String pathStr = absoulutePath.toString().replace(File.separatorChar, '.');
                    int packageIndex = pathStr.lastIndexOf(packageName);
                    String className = pathStr.substring(packageIndex, pathStr.length() - ".class".length());
                    try {
                        classes.add(Class.forName(className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return classes;
    }

    protected boolean scanCreate(Class<?> type) {
        return type.isAnnotationPresent(Component.class);
    }

    protected BeanDefinition wrapper(Class<?> type) {
        BeanDefinition beanDefinition = new BeanDefinition(type);
        if (this.beanDefinitionMap.containsKey(beanDefinition.getName())) {
            throw new RuntimeException("重复的 Bean 名字");
        }
        this.beanDefinitionMap.put(beanDefinition.getName(), beanDefinition);
        return beanDefinition;
    }

    protected Object createBean(BeanDefinition beanDefinition) {
        String beanName = beanDefinition.getName();
        if (this.beanMap.containsKey(beanName)) {
            return this.beanMap.get(beanName);
        }
        if (this.loadingBeanMap.containsKey(beanName)) {
            return this.loadingBeanMap.get(beanName);
        }
        return doCreateBean(beanDefinition);
    }

    protected Object doCreateBean(BeanDefinition beanDefinition) {
        Constructor<?> constructor = beanDefinition.getConstructor();
        Method postConstructMethod = beanDefinition.getPostConstructMethod();
        String beanName = beanDefinition.getName();
        Object bean = null;
        try {
            bean = constructor.newInstance();
            this.loadingBeanMap.put(beanDefinition.getName(), bean);
            autowireBean(bean, beanDefinition);
            if (postConstructMethod != null) {
                postConstructMethod.invoke(bean);
            }
            this.beanMap.put(beanName, this.loadingBeanMap.remove(beanName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return bean;
    }

    private void autowireBean(Object bean, BeanDefinition beanDefinition) throws IllegalAccessException {
        for (Field field : beanDefinition.getAutowiredFields()) {
            field.setAccessible(true);
            field.set(bean, this.getBean(field.getType()));
        }
    }

    public Object getBean(String name) {
        if (name == null) {
            return null;
        }
        Object bean = beanMap.get(name);
        if (bean != null) {
            return bean;
        }
        BeanDefinition beanDefinition = beanDefinitionMap.get(name);
        if (beanDefinition != null) {
            return createBean(beanDefinition);
        }
        return null;
    }

    public <T> T getBean(Class<T> beanType) {
        String beanName = this.beanDefinitionMap.values().stream()
                .filter(beanDefinition -> beanType.isAssignableFrom(beanDefinition.getBeanType()))
                .map(BeanDefinition::getName)
                .findFirst()
                .orElse(null);
        return (T) this.getBean(beanName);
    }

    public <T> List<T> getBeans(Class<T> beanType) {
        return this.beanDefinitionMap.values().stream()
                .filter(beanDefinition->beanType.isAssignableFrom(beanDefinition.getBeanType()))
                .map(BeanDefinition::getName)
                .map(this::getBean)
                .map(beanDefinition -> (T) beanDefinition)
                .toList();
    }
}
