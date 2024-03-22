package app.mcorg.permission.presentation.configuration.usecase;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.permission.domain.api.MyProfile;
import app.mcorg.permission.domain.api.Permissions;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.domain.usecase.permission.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {

    @Bean
    public GetUserPermissionsUseCase getUserPermissionsUseCase(Permissions permissions,
                                                               UnitOfWork<UserPermissions> unit) {
        return new GetUserPermissionsUseCase(permissions, unit);
    }

    @Bean
    public AddAuthorityUseCase authorityUseCase(GetUserPermissionsUseCase useCase, UnitOfWork<UserPermissions> unit) {
        return new AddAuthorityUseCase(useCase, unit);
    }

    @Bean
    public ChangeAuthorityUseCase changeAuthorityUseCase(GetUserPermissionsUseCase useCase,
                                                         UnitOfWork<UserPermissions> unit) {
        return new ChangeAuthorityUseCase(useCase, unit);
    }

    @Bean
    public RemoveAuthorityUseCase removeAuthorityUseCase(GetUserPermissionsUseCase useCase,
                                                         UnitOfWork<UserPermissions> unit) {
        return new RemoveAuthorityUseCase(useCase, unit);
    }

    @Bean
    public GetUserProfileUseCase getUserProfileUseCase(MyProfile myProfile) {
        return new GetUserProfileUseCase(myProfile);
    }

}
