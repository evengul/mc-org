function editTask(target) {
    const id = target.attributes.getNamedItem("id").value
    const needed = target.attributes.getNamedItem("needed").value
    const done = target.attributes.getNamedItem("done").value

    const doneInput = document.getElementById("edit-task-done-input");
    const neededInput = document.getElementById("edit-task-needed-input");
    const idInput = document.getElementById("edit-task-id-input");

    doneInput.value = done;
    neededInput.value = needed;
    idInput.value = id;

    doneInput.max = needed;

    neededInput.onchange = function (e) {
        const value = e.target.value;
        if (!Number.isNaN(value)) {
            doneInput.max = e.target.value;
        }
    }

    const dialog = document.getElementById("edit-task-dialog")

    dialog.showModal()
}

function cancelDialog() {
    const dialog = document.getElementById("edit-task-dialog")

    dialog.close()
}