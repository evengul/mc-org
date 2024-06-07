package app.mcorg.presentation.htmx.templates.pages.auth

import app.mcorg.presentation.htmx.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.presentation.htmx.templates.AUTH_FORM
import app.mcorg.presentation.htmx.templates.SITE_TITLE
import app.mcorg.presentation.htmx.templates.baseTemplate

fun registerPage(): String {
    return baseTemplate {
        main(classes = "auth-container") {
            script {
                src = "/static/response-targets.js"
                defer = true
            }
            h1(classes = SITE_TITLE) {
                + "Register an account in MC-ORG"
            }
            form(classes = AUTH_FORM) {
                id = "register-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                hxExtension("response-targets")
                hxPost("/register")
                hxTargetError("#register-error")
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
                p(classes = "text-error") {
                    id = "register-username-error"
                }
                label {
                    htmlFor = "register-email-input"
                    + "Email"
                }
                input {
                    id = "register-email-input"
                    type = InputType.email
                    name = "email"
                    required = true
                    maxLength = "120"
                }
                p(classes = "text-error") {
                    id = "register-email-error"
                }
                label {
                    htmlFor = "register-email-confirm-input"
                    + "Confirm email"
                }
                input {
                    id = "register-email-confirm-input"
                    type = InputType.email
                    name = "email-confirm"
                    required = true
                    maxLength = "120"
                }
                p(classes = "text-error") {
                    id = "register-email-confirm-error"
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
                p(classes = "text-error") {
                    id = "register-password-error"
                }
                label {
                    htmlFor = "register-password-confirm-input"
                    + "Confirm password"
                }
                input {
                    id = "register-password-confirm-input"
                    name = "password-confirm"
                    required = true
                    minLength = "5"
                    maxLength = "120"
                    type = InputType.password
                }
                p(classes = "text-error") {
                    id = "register-password-confirm-error"
                }
                p(classes = "text-error") {
                    id = "register-error"
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
}