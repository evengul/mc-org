document.addEventListener('htmx:afterRequest', function(event) {
    if (event.detail.isError) return;
    if (event.detail.xhr.responseURL.includes("/projects")) {

        if (event.detail.requestConfig.verb === "post") {
            hide("empty-project-state")
            show("projects-filter")
        } else if (event.detail.requestConfig.verb === "delete" && document.getElementsByClassName("project-header").length === 0) {
            show("empty-project-state")
            hide("projects-filter")
        }
    }
})

const hide = (id) => document.getElementById(id).style.display = 'none'
const show = (id, display = 'block') => document.getElementById(id).style.display = display