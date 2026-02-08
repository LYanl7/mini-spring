package mini.spring;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
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
    private Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

    public ApplicationContext(String packageName) throws Exception {
        initApplicationContext(packageName);
    }

    private void initApplicationContext(String packageName) throws Exception {
        this.scanPackage(packageName).stream()
                .filter(this::scanCreate)
                .map(this::wrapper)
                .forEach(this::createBean);
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
        if (beanDefinitionMap.containsKey(beanDefinition.getName())) {
            throw new RuntimeException("重复的 Bean 名字");
        }
        beanDefinitionMap.put(beanDefinition.getName(), beanDefinition);
        return beanDefinition;
    }

    protected void createBean(BeanDefinition beanDefinition) {
        String beanName = beanDefinition.getName();
        if (beanMap.containsKey(beanName)) {
            return;
        }
        doCreateBean(beanDefinition);
    }

    protected void doCreateBean(BeanDefinition beanDefinition) {
        Constructor<?> constructor = beanDefinition.getConstructor();
        Method postConstructMethod = beanDefinition.getPostConstructMethod();
        try {
            Object bean = constructor.newInstance();
            beanMap.put(beanDefinition.getName(), bean);
            if (postConstructMethod != null) {
                postConstructMethod.invoke(bean);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object getBean(String name) {
        return beanMap.get(name);
    }

    public <T> T getBean(Class<T> beanType) {
        return this.beanMap.values().stream()
                .filter(bean->beanType.isAssignableFrom(bean.getClass()))
                .map(bean -> (T) bean)
                .findAny()
                .orElseGet(null);
    }

    public <T> List<T> getBeans(Class<T> beanType) {
        return this.beanMap.values().stream()
                .filter(bean->beanType.isAssignableFrom(bean.getClass()))
                .map(bean -> (T) bean)
                .toList();
    }
}
