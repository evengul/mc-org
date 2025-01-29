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
        if (pathRequirement(e.detail)) {
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

function clearProjectDialog() {
    document.getElementById("project-add-name-input").value = ""
    document.getElementById("project-add-dimension-input").value = "OVERWORLD"
    document.getElementById("project-add-priority-input").value = "LOW"
    const perimeter = document.getElementById("project-add-requires-perimeter-input")
    if (perimeter) perimeter.checked = false
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
    addCloseListener("edit-task-dialog", detail => detail.xhr.responseURL.includes("/requirements") && detail.requestConfig.verb === "patch")
    addCloseListener("add-project-dialog", detail => detail.xhr.responseURL.includes("/projects") && detail.requestConfig.verb === "post", clearProjectDialog)
    addCloseListener("task-filters-dialog", detail => detail.xhr.responseURL.includes("/projects/") && detail.requestConfig.verb === "post")
    addCloseListener("add-task-doable-dialog", detail => detail.xhr.responseURL.includes("/tasks/doable") && detail.requestConfig.verb === "post", clearTaskDialogs)
    addCloseListener("add-task-countable-dialog", detail => detail.xhr.responseURL.includes("/tasks/countable") && detail.requestConfig.verb === "post", clearTaskDialogs)
    addCloseListener("add-task-litematica-dialog", detail => detail.xhr.responseURL.includes("/tasks/litematica") && detail.requestConfig.verb === "post", clearTaskDialogs)
})

