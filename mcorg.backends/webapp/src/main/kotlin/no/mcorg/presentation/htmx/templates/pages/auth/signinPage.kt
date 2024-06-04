package no.mcorg.presentation.htmx.templates.pages.auth

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.presentation.htmx.templates.AUTH_FORM
import no.mcorg.presentation.htmx.templates.SITE_TITLE

fun signinPage(): String {
    return createHTML()
        .main {
            h1(classes = SITE_TITLE) {
                id = "signin-site-title"
                +"Sign in to MC-ORG"
            }
            form(classes = AUTH_FORM) {
                id = "signin-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                label {
                    htmlFor = "signin-username-input"
                    + "Username"
                }
                input {
                    id = "signin-username-input"
                    name = "username"
                    required = true
                    minLength = "3"
                    maxLength = "120"
                }
                label {
                    htmlFor = "signin-password-input"
                    + "Password"
                }
                input {
                    id = "signin-password-input"
                    name = "password"
                    required = true
                    minLength = "5"
                    maxLength = "120"
                    type = InputType.password
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
