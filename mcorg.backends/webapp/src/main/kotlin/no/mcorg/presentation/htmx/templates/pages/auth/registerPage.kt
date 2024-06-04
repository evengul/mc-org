package no.mcorg.presentation.htmx.templates.pages.auth

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.presentation.htmx.templates.AUTH_FORM
import no.mcorg.presentation.htmx.templates.SITE_TITLE

fun registerPage(): String {
    return createHTML()
        .main {
            h1(classes = SITE_TITLE) {
                + "Register an account in MC-ORG"
            }
            form(classes = AUTH_FORM) {
                id = "register-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                label {
                    htmlFor = "register-username-input"
                    + "Username"
                }
                input {
                    id = "register-username-input"
                    name = "username"
                    required = true
                    minLength = "3"
                    maxLength = "120"
                }
                label {
                    htmlFor = "register-password-input"
                    + "Password"
                }
                input {
                    id = "register-password-input"
                    name = "password"
                    required = true
                    minLength = "5"
                    maxLength = "120"
                    type = InputType.password
                }
                button {
                    id = "register-submit-button"
                    type = ButtonType.submit
                    + "Create your account"
                }
            }
            a {
                id = "signin-link"
                href = "/signin"
                + "Do you already have an account? Sign in here."
            }
        }
}