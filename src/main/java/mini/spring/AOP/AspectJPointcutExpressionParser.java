package mini.spring.AOP;

import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParameter;
import org.aspectj.weaver.tools.PointcutParser;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.aspectj.weaver.tools.ShadowMatch;

import java.lang.reflect.Method;
import java.util.Set;

public final class AspectJPointcutExpressionParser {

    private static final Set<PointcutPrimitive> SUPPORTED_PRIMITIVES = Set.of(
            PointcutPrimitive.EXECUTION,
            PointcutPrimitive.ARGS,
            PointcutPrimitive.REFERENCE,
            PointcutPrimitive.THIS,
            PointcutPrimitive.TARGET,
            PointcutPrimitive.WITHIN,
            PointcutPrimitive.AT_ANNOTATION,
            PointcutPrimitive.AT_WITHIN,
            PointcutPrimitive.AT_ARGS,
            PointcutPrimitive.AT_TARGET
    );

    private static final PointcutParameter[] NO_PARAMETERS = new PointcutParameter[0];

    private AspectJPointcutExpressionParser() {
    }

    public static PointcutExpression parse(String expression) {
        return parse(expression, null, null);
    }

    public static PointcutExpression parse(String expression, Class<?> pointcutDeclarationScope) {
        return parse(expression, pointcutDeclarationScope, null);
    }

    public static PointcutExpression parse(String expression, Class<?> pointcutDeclarationScope, ClassLoader classLoader) {
        String normalizedExpression = normalize(expression);
        ClassLoader resolutionClassLoader = (classLoader != null ? classLoader : defaultClassLoader());
        PointcutParser parser = PointcutParser.getPointcutParserSupportingSpecifiedPrimitivesAndUsingSpecifiedClassLoaderForResolution(
                SUPPORTED_PRIMITIVES,
                resolutionClassLoader
        );

        if (pointcutDeclarationScope == null) {
            return parser.parsePointcutExpression(normalizedExpression);
        }
        return parser.parsePointcutExpression(normalizedExpression, pointcutDeclarationScope, NO_PARAMETERS);
    }

    public static boolean couldMatchJoinPointsInType(PointcutExpression pointcutExpression, Class<?> targetType) {
        if (pointcutExpression == null || targetType == null) {
            return false;
        }
        return pointcutExpression.couldMatchJoinPointsInType(targetType);
    }

    public static boolean matchesMethodExecution(PointcutExpression pointcutExpression, Method method) {
        if (pointcutExpression == null || method == null) {
            return false;
        }
        ShadowMatch shadowMatch = pointcutExpression.matchesMethodExecution(method);
        return shadowMatch.alwaysMatches() || shadowMatch.maybeMatches();
    }

    private static String normalize(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("Pointcut expression must not be null");
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Pointcut expression must not be empty");
        }
        return trimmed;
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return (classLoader != null ? classLoader : AspectJPointcutExpressionParser.class.getClassLoader());
    }
}

