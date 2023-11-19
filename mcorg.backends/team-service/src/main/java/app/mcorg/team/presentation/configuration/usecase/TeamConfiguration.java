package app.mcorg.team.presentation.configuration.usecase;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.team.domain.api.Teams;
import app.mcorg.team.domain.model.team.Team;
import app.mcorg.team.domain.usecase.team.ChangeTeamNameUseCase;
import app.mcorg.team.domain.usecase.team.CreateTeamUseCase;
import app.mcorg.team.domain.usecase.team.DeleteTeamUseCase;
import app.mcorg.team.domain.usecase.team.GetTeamUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TeamConfiguration {

    @Bean
    public GetTeamUseCase getTeamUseCase(Teams teams) {
        return new GetTeamUseCase(teams);
    }

    @Bean
    public ChangeTeamNameUseCase changeTeamNameUseCase(GetTeamUseCase getTeamUseCase, UnitOfWork<Team> unit) {
        return new ChangeTeamNameUseCase(getTeamUseCase, unit);
    }

    @Bean
    public CreateTeamUseCase createTeamUseCase(UnitOfWork<Team> unit, UsernameProvider usernameProvider) {
        return new CreateTeamUseCase(unit, usernameProvider);
    }

    @Bean
    public DeleteTeamUseCase deleteTeamUseCase(UnitOfWork<Team> unit) {
        return new DeleteTeamUseCase(unit);
    }
}
