package mini.spring.IoC;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ReflectionUtil {
    public static Class<?> getFieldGenericType(Field field) {
        // 1. 获取字段的泛型类型 (返回 Type 接口)
        Type genericType = field.getGenericType();

        // 2. 判断是否是参数化类型 (即带有 < > 的类型)
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;

            // 3. 获取实际类型参数数组 (例如 <PaymentService> 或 <String, User>)
            Type[] actualTypeArguments = pt.getActualTypeArguments();

            // 4. 确保至少有一个泛型参数，并取出第一个
            if (actualTypeArguments.length > 0) {
                Type actualType = actualTypeArguments[0];

                // 5. 确保是 Class 类型 (排除嵌套泛型等复杂情况)
                if (actualType instanceof Class) {
                    return (Class<?>) actualType;
                }
            }
        }

        // 如果不是泛型字段，返回 null 或 Object.class
        return null;
    }
}
