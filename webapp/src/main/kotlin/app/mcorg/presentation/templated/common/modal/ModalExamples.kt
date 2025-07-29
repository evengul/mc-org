package app.mcorg.presentation.templated.common.modal

import kotlinx.html.Tag

/**
 * Example usage of the redesigned modal system
 *
 * This file demonstrates various ways to use the new modal components:
 * - Basic modals with custom content
 * - Form modals with HTMX integration
 * - Confirmation modals for destructive actions
 * - Info modals for notifications
 * - Utility functions for programmatic control
 */

// Example 1: Basic modal with text content and sections
fun <T : Tag> T.exampleBasicModal() {
    modal(
        modalId = "basic-example",
        title = "Basic Modal Example",
        description = "This demonstrates a basic modal with custom content",
        openButtonBlock = {
            addClass("btn-action")
            + "Open Basic Modal"
        }
    ) {
        // Add text content using the utility extension
        addTextContent("This is some basic text content in the modal.")

        // Add a section with custom styling
        addSection(setOf("custom-section", "highlighted")) {
            addTextContent("This text is inside a highlighted section.")
        }
    }
}

// Example 2: Form modal for creating a new user
fun <T : Tag> T.exampleCreateUserFormModal() {
    formModal(
        modalId = "create-user-modal",
        title = "Create New User",
        description = "Fill out the form to create a new user account",
        saveText = "Create User",
        hxValues = FormModalHxValues(
            hxTarget = "#user-list",
            method = FormModalHttpMethod.POST,
            href = "/api/users"
        ),
        openButtonBlock = {
            addClass("btn-success")
            + "Add User"
        }
    ) {
        // Add form field components as children
        // addComponent(TextInputComponent("username", "Username", required = true))
        // addComponent(EmailInputComponent("email", "Email Address", required = true))
        // addComponent(SelectComponent("role", "User Role", options = listOf("User", "Admin")))

        // Or use custom form content for more control
        formContent {
            // Custom form fields can be added here
            // This gives you direct access to the FORM element
        }
    }
}

// Example 3: Confirmation modal for destructive actions
fun <T : Tag> T.exampleDeleteConfirmationModal(itemId: String, itemName: String) {
    confirmationModal(
        modalId = "delete-item-$itemId",
        title = "Delete Item",
        description = "Are you sure you want to delete '$itemName'? This action cannot be undone.",
        confirmText = "Delete",
        cancelText = "Cancel",
        onConfirm = "deleteItem('$itemId')", // JavaScript function to call
        openButtonBlock = {
            addClass("btn-danger")
            + "Delete"
        }
    ) {
        // Add additional warning content
        addTextContent(
            "This will permanently remove the item and all associated data.",
            setOf("warning-text", "text-danger")
        )
    }
}

// Example 4: Info modal for notifications
fun <T : Tag> T.exampleSuccessNotificationModal(message: String) {
    infoModal(
        modalId = "success-notification",
        title = "Success!",
        description = message,
        okText = "Got it!",
        openButtonBlock = {
            addClass("btn-info")
            + "Show Success"
        }
    )
}

// Example 5: Complex modal with multiple sections and custom styling
fun <T : Tag> T.exampleComplexModal() {
    modal(
        modalId = "complex-example",
        title = "Project Settings",
        description = "Configure your project settings and preferences",
        openButtonBlock = {
            addClass("btn-neutral")
            + "Open Settings"
        }
    ) {
        // General settings section
        addSection(setOf("settings-section")) {
            addTextContent("General Settings", setOf("section-title"))
            // Add form components or custom content here
        }

        // Advanced settings section
        addSection(setOf("settings-section", "advanced")) {
            addTextContent("Advanced Settings", setOf("section-title"))
            addTextContent(
                "These settings are for advanced users only. Please be careful when modifying these options.",
                setOf("warning-text")
            )
        }
    }
}

// Example 6: Programmatic modal control
@Suppress("unused")
fun handleUserAction(action: String, itemId: String) {
    when (action) {
        "edit" -> {
            // Open edit modal programmatically
            val openScript = openModal("edit-item-$itemId")
            // Use this script in your JavaScript or HTMX responses
        }
        "delete" -> {
            // Open confirmation modal
            val openScript = openModal("delete-confirm-$itemId")
            // Could be used in onclick handlers
        }
        "success" -> {
            // Auto-close modal after success
            val closeScript = closeModal("form-modal")
            // Use in HTMX success responses
        }
    }
}
