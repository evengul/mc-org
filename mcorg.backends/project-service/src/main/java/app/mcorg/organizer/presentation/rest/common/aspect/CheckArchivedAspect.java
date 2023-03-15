package app.mcorg.organizer.presentation.rest.common.aspect;

import app.mcorg.organizer.domain.model.exceptions.ArchivedException;
import app.mcorg.organizer.domain.usecase.project.IsProjectArchivedUseCase;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
public class CheckArchivedAspect {

    private final IsProjectArchivedUseCase useCase;

    private Map<String, Object> argMap(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] argNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < argNames.length; i++)
            map.put(argNames[i], args[i]);
        return map;
    }

    private CheckArchived getAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getDeclaredAnnotation(CheckArchived.class);
    }

    @Before("@annotation(CheckArchived)")
    public void check(JoinPoint point) {
        Map<String, Object> args = argMap(point);
        String argName = getAnnotation(point).value();

        Optional.ofNullable(args.get(argName)).map(String.class::cast).ifPresentOrElse(id -> {
            boolean archived = useCase.execute(new IsProjectArchivedUseCase.InputValues(id)).isArchived();
            if (archived) throw new ArchivedException(id);
        }, () -> {
            throw new IllegalArgumentException("Misconfigured annotation. Could not find \"" + argName + "\" argument");
        });
    }
}
