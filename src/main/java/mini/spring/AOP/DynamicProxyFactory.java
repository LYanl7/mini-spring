package mini.spring.AOP;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DynamicProxyFactory {

    @FunctionalInterface
    public interface MethodInterceptor {
        Object invoke(MethodInvocation invocation) throws Throwable;
    }

    public interface MethodInvocation {
        Object getProxy();

        Object getTarget();

        Method getMethod();

        Object[] getArguments();

        Object proceed() throws Throwable;
    }

    private static final Object[] NO_ARGS = new Object[0];
    private static final ConcurrentMap<Class<?>, Class<?>> JDK_ORIGINAL_CLASS_TOKEN_CACHE = new ConcurrentHashMap<>();
    private static final String JDK_ORIGINAL_CLASS_TOKEN_SUFFIX = "$JdkOriginalClass$";
    private static final String JDK_ORIGINAL_CLASS_TOKEN_METHOD = "originalClass";

    private DynamicProxyFactory() {
    }

    public static Class<?> getOriginalClass(Object proxy) {
        Objects.requireNonNull(proxy, "proxy must not be null");
        return getOriginalClass(proxy.getClass());
    }

    public static Class<?> getOriginalClass(Class<?> proxyClass) {
        Objects.requireNonNull(proxyClass, "proxyClass must not be null");

        Class<?> current = proxyClass;
        while (isByteBuddySubclassProxy(current)) {
            Class<?> superClass = current.getSuperclass();
            if (superClass == null || superClass == Object.class) {
                break;
            }
            current = superClass;
        }

        if (!Proxy.isProxyClass(current)) {
            return current;
        }

        Class<?> original = resolveOriginalClassFromJdkProxy(current);
        return (original != null ? original : current);
    }

    public static <T> T createProxy(T target, MethodInterceptor interceptor) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(interceptor, "interceptor must not be null");

        Class<?> targetClass = target.getClass();
        if (isSubclassProxyable(targetClass)) {
            return createSubclassProxy(target, interceptor);
        }
        if (hasAnyInterface(targetClass)) {
            return createJdkProxy(target, interceptor);
        }
        throw new IllegalArgumentException("Cannot create proxy for class without interfaces: " + targetClass.getName());
    }

    @SuppressWarnings("unchecked")
    public static <T> T createJdkProxy(T target, MethodInterceptor interceptor) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(interceptor, "interceptor must not be null");

        Class<?> targetClass = target.getClass();
        Class<?> originalClass = getOriginalClass(targetClass);
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?>[] targetInterfaces = getAllInterfaces(targetClass);
        if (targetInterfaces.length == 0) {
            throw new IllegalArgumentException("JDK proxy requires at least one interface: " + targetClass.getName());
        }

        for (Class<?> ifc : targetInterfaces) {
            interfaces.add(ifc);
        }
        maybeAddJdkOriginalClassTokenInterface(interfaces, originalClass);
        Class<?>[] interfaceArray = interfaces.toArray(Class<?>[]::new);

        Object proxy = Proxy.newProxyInstance(
                targetClass.getClassLoader(),
                interfaceArray,
                new JdkProxyInvocationHandler(target, targetClass, interceptor)
        );
        return (T) proxy;
    }

    @SuppressWarnings("unchecked")
    public static <T> T createSubclassProxy(T target, MethodInterceptor interceptor) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(interceptor, "interceptor must not be null");

        Class<?> targetClass = target.getClass();
        if (!isSubclassProxyable(targetClass)) {
            throw new IllegalArgumentException("Class is not subclass-proxyable: " + targetClass.getName());
        }

        Class<?> proxyClass = new ByteBuddy()
                .subclass(targetClass)
                .method(ElementMatchers.isVirtual()
                        .and(ElementMatchers.not(ElementMatchers.isFinal()))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))))
                .intercept(MethodDelegation.to(new SubclassProxyInterceptor(interceptor)))
                .make()
                .load(targetClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        Object proxyInstance;
        try {
            proxyInstance = proxyClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate proxy class: " + proxyClass.getName(), e);
        }

        copyInstanceFields(target, proxyInstance, targetClass);
        return (T) proxyInstance;
    }

    private static boolean isSubclassProxyable(Class<?> targetClass) {
        if (targetClass.isInterface()) {
            return false;
        }
        if (targetClass.isPrimitive() || targetClass.isArray()) {
            return false;
        }
        return !Modifier.isFinal(targetClass.getModifiers());
    }

    private static boolean hasAnyInterface(Class<?> targetClass) {
        return getAllInterfaces(targetClass).length > 0;
    }

    private static Class<?>[] getAllInterfaces(Class<?> targetClass) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?> current = targetClass;
        while (current != null && current != Object.class) {
            for (Class<?> ifc : current.getInterfaces()) {
                collectInterfaces(ifc, interfaces);
            }
            current = current.getSuperclass();
        }
        return interfaces.toArray(Class<?>[]::new);
    }

    private static void collectInterfaces(Class<?> ifc, Set<Class<?>> interfaces) {
        if (interfaces.add(ifc)) {
            for (Class<?> parent : ifc.getInterfaces()) {
                collectInterfaces(parent, interfaces);
            }
        }
    }

    private static boolean isByteBuddySubclassProxy(Class<?> candidate) {
        return candidate != null && candidate.getName().contains("$ByteBuddy$");
    }

    private static Class<?> resolveOriginalClassFromJdkProxy(Class<?> proxyClass) {
        for (Class<?> ifc : proxyClass.getInterfaces()) {
            if (!isJdkOriginalClassTokenInterface(ifc)) {
                continue;
            }
            try {
                Method method = ifc.getMethod(JDK_ORIGINAL_CLASS_TOKEN_METHOD);
                Object value = method.invoke(null);
                return (Class<?>) value;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to resolve original class from JDK proxy: " + proxyClass.getName(), e);
            }
        }
        return null;
    }

    private static boolean isJdkOriginalClassTokenInterface(Class<?> candidate) {
        return candidate != null
                && candidate.isInterface()
                && candidate.getName().startsWith(DynamicProxyFactory.class.getName() + JDK_ORIGINAL_CLASS_TOKEN_SUFFIX);
    }

    private static void maybeAddJdkOriginalClassTokenInterface(Set<Class<?>> interfaces, Class<?> originalClass) {
        ClassLoader loader = originalClass.getClassLoader();
        if (loader == null) {
            return;
        }

        try {
            interfaces.add(getOrCreateJdkOriginalClassTokenInterface(originalClass, loader));
        } catch (RuntimeException ignored) {
        }
    }

    private static Class<?> getOrCreateJdkOriginalClassTokenInterface(Class<?> originalClass, ClassLoader loader) {
        return JDK_ORIGINAL_CLASS_TOKEN_CACHE.computeIfAbsent(originalClass, key -> {
            String tokenName = DynamicProxyFactory.class.getName()
                    + JDK_ORIGINAL_CLASS_TOKEN_SUFFIX
                    + toSafeTypeSuffix(key.getName());

            try {
                return Class.forName(tokenName, false, loader);
            } catch (ClassNotFoundException ignored) {
            }

            return new ByteBuddy()
                    .makeInterface()
                    .name(tokenName)
                    .defineMethod(JDK_ORIGINAL_CLASS_TOKEN_METHOD, Class.class, Modifier.PUBLIC | Modifier.STATIC)
                    .intercept(FixedValue.value(key))
                    .make()
                    .load(loader, ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();
        });
    }

    private static String toSafeTypeSuffix(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private static Method resolveMethod(Class<?> targetClass, Method method) {
        try {
            return targetClass.getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException ignored) {
            try {
                Method declared = targetClass.getDeclaredMethod(method.getName(), method.getParameterTypes());
                declared.setAccessible(true);
                return declared;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Failed to resolve method " + method + " on " + targetClass.getName(), e);
            }
        }
    }

    private static Object invokeReflectively(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private static void copyInstanceFields(Object source, Object destination, Class<?> declaringType) {
        Class<?> current = declaringType;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(source);
                    field.set(destination, value);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Failed to copy field: " + field, e);
                }
            }
            current = current.getSuperclass();
        }
    }

    private static final class ReflectiveMethodInvocation implements MethodInvocation {
        private final Object proxy;
        private final Object target;
        private final Method method;
        private final Object[] arguments;

        private ReflectiveMethodInvocation(Object proxy, Object target, Method method, Object[] arguments) {
            this.proxy = proxy;
            this.target = target;
            this.method = method;
            this.arguments = arguments;
        }

        @Override
        public Object getProxy() {
            return proxy;
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public Object proceed() throws Throwable {
            return invokeReflectively(target, method, arguments);
        }
    }

    public static final class SubclassProxyInterceptor {
        private final MethodInterceptor interceptor;

        private SubclassProxyInterceptor(MethodInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @RuntimeType
        public Object intercept(
                @This Object proxy,
                @Origin Method method,
                @AllArguments Object[] args,
                @SuperCall Callable<?> superCall
        ) throws Throwable {
            Object[] arguments = (args != null ? args : NO_ARGS);
            return interceptor.invoke(new SuperCallMethodInvocation(proxy, method, arguments, superCall));
        }
    }

    private static final class JdkProxyInvocationHandler implements InvocationHandler {
        private final Object target;
        private final Class<?> targetClass;
        private final MethodInterceptor interceptor;

        private JdkProxyInvocationHandler(Object target, Class<?> targetClass, MethodInterceptor interceptor) {
            this.target = target;
            this.targetClass = targetClass;
            this.interceptor = interceptor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object[] arguments = (args != null ? args : NO_ARGS);
            if (method.getDeclaringClass() == Object.class) {
                return invokeReflectively(target, method, arguments);
            }
            Method targetMethod = resolveMethod(targetClass, method);
            return interceptor.invoke(new ReflectiveMethodInvocation(proxy, target, targetMethod, arguments));
        }
    }

    private static final class SuperCallMethodInvocation implements MethodInvocation {
        private final Object proxy;
        private final Method method;
        private final Object[] arguments;
        private final Callable<?> superCall;

        private SuperCallMethodInvocation(Object proxy, Method method, Object[] arguments, Callable<?> superCall) {
            this.proxy = proxy;
            this.method = method;
            this.arguments = arguments;
            this.superCall = superCall;
        }

        @Override
        public Object getProxy() {
            return proxy;
        }

        @Override
        public Object getTarget() {
            return proxy;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public Object proceed() throws Throwable {
            try {
                return superCall.call();
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }
}
