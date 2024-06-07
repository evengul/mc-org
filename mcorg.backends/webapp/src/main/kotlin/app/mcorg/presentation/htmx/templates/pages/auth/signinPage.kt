package app.mcorg.presentation.htmx.templates.pages.auth

import app.mcorg.presentation.htmx.hxExtension
import app.mcorg.presentation.htmx.hxPost
import app.mcorg.presentation.htmx.hxTarget
import app.mcorg.presentation.htmx.hxTargetError
import kotlinx.html.*
import app.mcorg.presentation.htmx.templates.AUTH_FORM
import app.mcorg.presentation.htmx.templates.SITE_TITLE
import app.mcorg.presentation.htmx.templates.baseTemplate

fun signinPage(): String {
    return baseTemplate {
        main(classes = "auth-container") {
            script {
                src = "/static/response-targets.js"
                defer = true
            }
            h1(classes = SITE_TITLE) {
                id = "signin-site-title"
                +"Sign in to MC-ORG"
            }
            form(classes = AUTH_FORM) {
                id = "signin-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                hxExtension("response-targets")
                hxPost("/signin")
                hxTargetError("#signin-error")
                label {
                    htmlFor = "signin-username-input"
                    + "Username"
                }
                input {
                    id = "signin-username-input"
                    name = "username"
                    required = true
                }
                label {
                    htmlFor = "signin-password-input"
                    + "Password"
                }
                input {
                    id = "signin-password-input"
                    name = "password"
                    required = true
                    type = InputType.password
                }
                p(classes = "text-error") {
                    id = "signin-error"
                }
                button {
                    id = "signin-submit-button"
                    type = ButtonType.submit
                    + "Sign in"
                }
            }
            a {
                id = "register-link"
                href = "/register"
                + "Create a new account"
            }
        }
    }
}
