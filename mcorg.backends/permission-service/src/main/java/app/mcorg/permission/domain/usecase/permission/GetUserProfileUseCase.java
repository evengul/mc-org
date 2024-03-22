package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.permission.domain.api.MyProfile;
import app.mcorg.permission.domain.model.permission.Profile;
import app.mcorg.permission.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetUserProfileUseCase extends UseCase<GetUserProfileUseCase.InputValues, GetUserProfileUseCase.OutputValues> {

    private final MyProfile myProfile;

    public OutputValues execute(InputValues input) {

        Profile profile = myProfile.get();

        return new OutputValues(profile);
    }

    public record InputValues() implements UseCase.InputValues {
    }

    public record OutputValues(Profile profile) implements UseCase.OutputValues {
    }
}