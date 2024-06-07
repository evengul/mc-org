package app.mcorg.presentation.htmx.handlers

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.htmx.hxOutOfBands
import app.mcorg.presentation.htmx.routing.*
import app.mcorg.presentation.htmx.templates.pages.auth.signinPage
import app.mcorg.presentation.htmx.templates.pages.profilePage
import app.mcorg.presentation.security.createSignedJwtToken
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.respondProfile() {
    val (id, username) = getUser()!!

    respondHtml(profilePage(username, id))
}

suspend fun ApplicationCall.handlePostRegister() {
    val data = receiveMultipart().readAllParts()

    val username = (data.find { it.name == "username" } as PartData.FormItem?)?.value
    val email = (data.find { it.name == "email" } as PartData.FormItem?)?.value
    val emailConfirm = (data.find { it.name == "email-confirm" } as PartData.FormItem?)?.value
    val password = (data.find { it.name == "password" } as PartData.FormItem?)?.value
    val passwordConfirmed = (data.find { it.name == "password-confirm" } as PartData.FormItem?)?.value

    val errors: MutableList<Pair<String, String>> = mutableListOf()

    if (username == null) {
        errors.add("register-username-error" to "Please enter a username")
    } else if (username.length < 5) {
        errors.add("register-username-error" to "Username must be longer than 4 characters")
    } else if (username.length > 250) {
        errors.add("register-username-error" to "Username must be longer than 250 characters")
    } else if (usersApi().usernameExists(username)) {
        errors.add("register-username-error" to "Username already exists")
    } else {
        errors.add("register-username-error" to "")
    }

    if (email == null) {
        errors.add("register-email-error" to "Please enter an email")
    } else if (email.length > 250) {
        errors.add("register-email-error" to "Email must be shorter than 250 characters")
    } else if (emailConfirm == email && usersApi().emailExists(email)) {
        errors.add("register-email-error" to "Email already registered")
    } else {
        errors.add("register-email-error" to "")
    }

    if (emailConfirm != email) {
        errors.add("register-email-confirm-error" to "Email must match")
    } else {
        errors.add("register-email-confirm-error" to "")
    }

    if (password == null) {
        errors.add("register-password-error" to "Please enter a password")
    } else if (password.length < 5) {
        errors.add("register-password-error" to "Password must be at least 5 characters")
    } else if (password.length > 250) {
        errors.add("register-password-error" to "Password must be shorter than 250 characters")
    } else {
        errors.add("register-password-error" to "")
    }

    if (password != passwordConfirmed) {
        errors.add("register-password-confirm-error" to "Passwords do not match")
    } else {
        errors.add("register-password-confirm-error" to "")
    }

    if (errors.all { it.second.trim().isBlank() }) {
        val userId = usersApi()
            .createUser(username!!, email!!, password!!)

        signIn(createSignedJwtToken(usersApi().getUser(userId)!!))

        clientRedirect("/signin")
    } else {
        var combinedError = createHTML().p {
            id = "#register-error"
            + "An error occurred. See above for details."
        }

        errors.forEach {
            combinedError += "\n" + createHTML().p {
                id = it.first
                hxOutOfBands(it.first)
                + it.second
            }
        }
        htmlBadRequest(combinedError)
    }
}

suspend fun ApplicationCall.respondSignIn() {
    val userId = getUserId()
    if (userId == null || !usersApi().userExists(userId)) {
        signOut()
        respondHtml(signinPage())
    } else {
        respondRedirect("/")
    }
}

suspend fun ApplicationCall.handlePostSignin() {
    val data = receiveMultipart().readAllParts()

    val username = (data.find { it.name == "username" } as PartData.FormItem?)?.value
    val password = (data.find { it.name == "password" } as PartData.FormItem?)?.value

    if (username == null) {
        htmlBadRequest(createHTML().p {
            id = "#signin-error"
            + "Username is required"
        })
    } else if (password == null) {
        htmlBadRequest(createHTML().p {
            id = "#signin-error"
            + "Password is required"
        })
    } else {
        val user = usersApi()
            .getUserByUsernameIfPasswordMatches(username, password)
        if (user == null) {
            htmlUnauthorized(createHTML().p {
                id = "#signin-error"
                + "Username and password does not match"
            })
        } else {
            signIn(createSignedJwtToken(user))
            clientRedirect("/")
        }
    }
}

suspend fun ApplicationCall.respondSignOut() {
    signOut()
    respondRedirect("/")
}