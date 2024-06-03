package no.mcorg.presentation.htmx.templates.pages.auth

import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun signinPage(): String {
    return createHTML()
        .main {
            h1 {
                +"Sign in to MC-ORG"
            }
            form {
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
                    type = ButtonType.submit
                    + "Sign in"
                }
            }
            a {
                href = "/register"
                + "Create a new account"
            }
        }
}
