package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun registerPage(): String {
    return createHTML()
        .main {
            h1 {
                + "Register an account in MC-ORG"
            }
            form {
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
                    type = ButtonType.submit
                    + "Create your account"
                }
            }
            a {
                href = "/signin"
                + "Do you already have an account? Sign in here."
            }
        }
}