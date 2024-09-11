# What to use HTMX for?

## Users page
 - Remove user: Remove the LI element
 - Add user: Form is moved to top of page, and LI elements are appended as the users are added

## Worlds page
Do the same as users page

## Profile page
 - Picture upload returns the profile component with the profile photo and username
 - Technical player returns the input[type = "checkbox"] with correct state

## Projects overview / World page
 - Move assign user to dialog, and as the form closes, the assign element will be updated with the user.
Use the event system to close the dialog.
 - Delete Project removes the LI

## Task overview / Project page
 - Assign user = Same as projects page for both task and project
 - +1 and Done buttons updates the LI
 - Delete button removes the LI
 - Edit -> Save updates the LI if possible.
 - Add button: Dropdown menu with 3 choices that open each their dialogs.

