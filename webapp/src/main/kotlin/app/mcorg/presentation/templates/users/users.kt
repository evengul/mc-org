package app.mcorg.presentation.templates.users

import app.mcorg.domain.model.users.User
import app.mcorg.presentation.*
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun users(worldId: Int, currentUser: User, users: List<User>, isAdmin: Boolean): String = mainPageTemplate(
    selectedPage = MainPage.USERS,
    worldId = worldId,
    title = "Users"
) {
    classes = setOf("page-list-with-add-form")
    if (isAdmin) {
        form {
            id = "user-add"
            encType = FormEncType.applicationXWwwFormUrlEncoded
            method = FormMethod.post
            autoComplete = false
            hxPost("/app/worlds/$worldId/users")
            hxTarget("#users-list-current-user")
            hxErrorTarget("#error-message", "4xx")
            hxSwap("afterend")
            label {
                htmlFor = "add-user-username-input"
                + "Username"
            }
            input {
                id = "add-user-username-input"
                type = InputType.text
                name = "newUser"
                attributes["autocomplete"] = "off"
                required = true
            }
            p {
                classes = setOf("error")
                id = "error-message"
            }
            p {
                + "Must be an exact match, and must have signed in to MC-ORG before"
            }
            button {
                id = "add-user-submit-button"
                type = ButtonType.submit
                + "Add user"
            }
        }
    }
    ul {
        id = "users-list"
        li {
            id = "users-list-current-user"
            span {
                classes = setOf("selected", "icon-row")
                span {
                    classes = setOf("icon", "icon-user")
                }
                h2 {
                    + currentUser.username
                }
            }
        }
        for (user in users) {
            li {
                user(worldId, user, isAdmin)
            }
        }
    }
}