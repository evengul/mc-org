function showDialog(id) {
    const dialog = document.getElementById(id)
    dialog.showModal()
}

function hideDialog(id) {
    const dialog = document.getElementById(id)
    dialog.close()
}

function addCloseListener(id, pathRequirement) {
    document.addEventListener("htmx:afterOnLoad", (e) => {
        if (pathRequirement(e.detail.pathInfo.responsePath)) {
            hideDialog(id)
        }
    })
}

function editTask(target) {
    const id = target.attributes.getNamedItem("id").value
    const needed = target.attributes.getNamedItem("needed").value
    const done = target.attributes.getNamedItem("done").value

    const form = document.getElementById("edit-countable-task-form")
    const doneInput = document.getElementById("edit-task-done-input");
    const neededInput = document.getElementById("edit-task-needed-input");
    const idInput = document.getElementById("edit-task-id-input");

    doneInput.value = done;
    neededInput.value = needed;
    idInput.value = id;

    form.setAttribute("hx-target", `#task-${id}`)

    doneInput.max = needed;

    neededInput.onchange = function (e) {
        const value = e.target.value;
        if (!Number.isNaN(value)) {
            doneInput.max = e.target.value;
        }
    }

    showDialog("edit-task-dialog")
}

window.onload = () => {
    addCloseListener("edit-task-dialog", path => path.includes("/requirements"))
}

