package app.mcorg.project.presentation.rest.common.aspect;

import app.mcorg.project.domain.exceptions.ArchivedException;
import app.mcorg.project.domain.usecase.project.IsProjectArchivedUseCase;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class CheckArchivedAspect extends AbstractAspect<CheckArchived> {

    private final IsProjectArchivedUseCase useCase;

    @Before("@annotation(CheckArchived)")
    public void check(JoinPoint point) {
        CheckArchived annotation = getAnnotation(point, CheckArchived.class);
        String id = getArg(point, annotation.value(), String.class);

        boolean archived = useCase.execute(new IsProjectArchivedUseCase.InputValues(id))
                                  .isArchived();
        if (archived) {
            throw new ArchivedException(id);
        }
    }
}
