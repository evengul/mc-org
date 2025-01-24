function showDialog(id) {
    const dialog = document.getElementById(id)
    dialog.showModal()
}

function hideDialog(id) {
    const dialog = document.getElementById(id)
    dialog.close()
}

function addCloseListener(id, pathRequirement, onClose)  {
    document.addEventListener("htmx:afterOnLoad", (e) => {
        if (pathRequirement(e.detail.pathInfo.responsePath)) {
            hideDialog(id)
            if (onClose) onClose()
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

function clearTaskDialogs() {
    const doableNameInput = document.getElementById("add-doable-task-name-input")
    doableNameInput.value = ""

    const countableNameInput = document.getElementById("add-countable-task-name-input")
    const countableAmountInput = document.getElementById("add-countable-task-amount-input")
    countableNameInput.value = ""
    countableAmountInput.value = ""

    const litematicaFileInput = document.getElementById("tasks-add-litematica-file-input")
    litematicaFileInput.value = ""
}

window.addEventListener('load', () => {
    addCloseListener("edit-task-dialog", path => path.includes("/requirements"))
    addCloseListener("add-project-dialog", path => path.includes("/projects"))
    addCloseListener("task-filters-dialog", path => path.includes("/projects/"))
    addCloseListener("add-task-doable-dialog", path => path.includes("/tasks/doable"), clearTaskDialogs)
    addCloseListener("add-task-countable-dialog", path => path.includes("/tasks/countable"), clearTaskDialogs)
    addCloseListener("add-task-litematica-dialog", path => path.includes("/tasks/litematica"), clearTaskDialogs)
})

