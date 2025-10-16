package app.mcorg.pipeline.idea

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.presentation.templated.idea.ideaListItem
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.li
import kotlinx.html.stream.createHTML

/**
 * Handles the creation of a new idea.
 * Validates input, creates idea in database, and returns HTML fragment.
 */
suspend fun ApplicationCall.handleCreateIdea() {
    val user = getUser()
    val parameters = receiveParameters()

    // Execute pipeline
    val pipeline = Pipeline.create<CreateIdeaFailures, Parameters>()
        .pipe(ValidateIdeaInputStep)
        .pipe(InjectIdeaContextStep(user.id))
        .pipe(CreateIdeaStep)
        .pipe(GetCreatedIdeaStep)

    when (val result = pipeline.execute(parameters)) {
        is Result.Success -> {
            val idea = result.value
            // Return HTML fragment that will be prepended to the ideas list
            respondHtml(createHTML().li {
                ideaListItem(idea)
            })
        }
        is Result.Failure -> {
            when (result.error) {
                is CreateIdeaFailures.ValidationError -> {
                    val errors = result.error.errors
                    val errorMessage = errors.joinToString("; ") { error ->
                        when (error) {
                            is app.mcorg.pipeline.failure.ValidationFailure.MissingParameter ->
                                "${error.parameterName} is required"
                            is app.mcorg.pipeline.failure.ValidationFailure.InvalidLength ->
                                "${error.parameterName} must be between ${error.minLength ?: 0} and ${error.maxLength} characters"
                            is app.mcorg.pipeline.failure.ValidationFailure.InvalidFormat ->
                                "${error.parameterName}: ${error.message}"
                            else -> error.toString()
                        }
                    }
                    respondBadRequest("Validation failed: $errorMessage")
                }
                CreateIdeaFailures.InsufficientPermissions -> {
                    respondBadRequest("You don't have permission to create ideas")
                }
                CreateIdeaFailures.DatabaseError -> {
                    respondBadRequest("Failed to create idea. Please try again.")
                }
            }
        }
    }
}

