package app.mcorg.team.presentation.rest.common.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.util.stream.IntStream;

public abstract class AbstractAspect<A extends Annotation> {
    protected <T> T getArg(JoinPoint joinPoint, String key, Class<T> tClass) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameters = signature.getParameterNames();
        return IntStream.range(0, parameters.length)
                .filter(i -> parameters[i].equals(key))
                .mapToObj(i -> joinPoint.getArgs()[i])
                .filter(tClass::isInstance)
                .map(tClass::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Misconfigured annotation. Could not find %s in %s",
                                key,
                                String.join(", ", parameters))));
    }

    protected A getAnnotation(JoinPoint joinPoint, Class<A> aClass) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getDeclaredAnnotation(aClass);
    }

    @SuppressWarnings("unused")
    public abstract void check(JoinPoint joinPoint);
}
