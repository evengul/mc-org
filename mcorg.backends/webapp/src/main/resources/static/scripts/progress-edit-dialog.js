function editClick() {
    const dialog = document.getElementById("edit-task-dialog")
    dialog.showModal()
}

function cancelDialog() {
    const dialog = document.getElementById("edit-task-dialog")

    dialog.close()
}