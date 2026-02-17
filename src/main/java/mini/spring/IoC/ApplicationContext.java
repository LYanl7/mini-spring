package mini.spring.IoC;

import mini.spring.AOP.AutoProxyCreator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
    private Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>();

    private Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();
    private List<BeanPostProcessor> processors = new ArrayList<>();

    public ApplicationContext(String packageName) throws Exception {
        initApplicationContext(packageName);
    }

    private void initApplicationContext(String packageName) throws Exception {
        this.scanPackage(packageName).stream()
                .filter(this::canCreate)
                .forEach(this::wrapper);
        initBeanPostProcessors();
        this.beanDefinitionMap.values().forEach(this::createBean);
    }

    private void initBeanPostProcessors() {
        this.beanDefinitionMap.values().stream()
                .filter(beanDefinition -> BeanPostProcessor.class.isAssignableFrom(beanDefinition.getBeanType()))
                .map(this::createBean)
                .map((bean) -> (BeanPostProcessor) bean)
                .forEach(this.processors::add);
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

    protected boolean canCreate(Class<?> type) {
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

    protected Object getEarlyBean(Object bean, BeanDefinition beanDefinition) {
        for (BeanPostProcessor processor : this.processors) {
            if (processor instanceof  AutoProxyCreator) {
                bean = processor.afterInitialization(bean, beanDefinition.getName());
            }
        }

        return bean;
    }

    protected Object doCreateBean(BeanDefinition beanDefinition) {
        Constructor<?> constructor = beanDefinition.getConstructor();
        String beanName = beanDefinition.getName();
        Object bean;
        try {
            bean = constructor.newInstance();
            Object finalBean = bean;
            this.singletonFactories.put(beanName, () -> getEarlyBean(finalBean, beanDefinition));

            autowireBean(bean, beanDefinition);
            bean = initializeBean(bean, beanDefinition);
            this.loadingBeanMap.remove(beanName);
            this.singletonFactories.remove(beanName);
            this.beanMap.put(beanName, bean);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return bean;
    }

    private Object initializeBean(Object bean, BeanDefinition beanDefinition) throws Exception {
        for (BeanPostProcessor processor : this.processors) {
            bean = processor.beforeInitialization(bean, beanDefinition.getName());
        }

        Method postConstructMethod = beanDefinition.getPostConstructMethod();
        if (postConstructMethod != null) {
            postConstructMethod.invoke(bean);
        }

        for (BeanPostProcessor processor : this.processors) {
            bean = processor.afterInitialization(bean, beanDefinition.getName());
        }

        return bean;
    }

    private void autowireBean(Object bean, BeanDefinition beanDefinition) throws IllegalAccessException {
        for (Field field : beanDefinition.getAutowiredFields()) {
            field.setAccessible(true);
            Object val = this.getBean(field.getType());
            if (val != null) {
                field.set(bean, val);
            } else {
                Autowired autowired = field.getAnnotation(Autowired.class);
                if (autowired.required()) {
                    throw new RuntimeException("找不到对应的 Bean");
                } else {
                    field.set(bean, null);
                }
            }
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

        Object loadingBean = loadingBeanMap.get(name);
        if (loadingBean != null) {
            return loadingBean;
        }

        ObjectFactory<?> objectFactory = singletonFactories.get(name);
        if (objectFactory != null) {
            bean = objectFactory.getObject();
            this.loadingBeanMap.put(name, bean);
            this.singletonFactories.remove(name);
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
