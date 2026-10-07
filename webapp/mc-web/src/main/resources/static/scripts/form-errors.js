// Field messages from a validation failure (FieldError.kt: fieldMessages).
//
// The server sends one htmx partial per failed field, aimed at `find [data-error-for=<field>]`
// from the element that sent the request, plus an alert holding every message. htmx places the
// partials whose `find` matched. This finishes the job before anything is swapped:
//
// - a message whose `find` matched nothing goes to the nearest slot for its field above the
//   element that sent the request (an upload input inside a larger form sends from the input);
// - the alert keeps only the messages no slot took, and is dropped when every one was placed.
//
// So every message is seen once, and a field the page has no slot for still says what is wrong.

const SLOT = 'data-error-for';
const MESSAGE = 'data-field-message';
const FALLBACK = 'data-field-fallback';

function nearestSlot(source, field) {
    const selector = '[' + SLOT + '="' + CSS.escape(field) + '"]';
    for (let node = source; node && node !== document; node = node.parentElement) {
        const slot = node.querySelector(selector);
        if (slot) return slot;
    }
    return null;
}

document.addEventListener('htmx:before:swap', function (event) {
    const { ctx, tasks } = event.detail;
    const placed = new Set();
    let fallback = null;

    for (const task of tasks) {
        if (task.type !== 'partial') continue;
        if (task.fragment.querySelector('[' + FALLBACK + ']')) {
            fallback = task;
            continue;
        }
        const message = task.fragment.querySelector('[' + MESSAGE + ']');
        if (!message) continue;
        const field = message.getAttribute(MESSAGE);
        if (!task.target) task.target = nearestSlot(ctx.sourceElement, field);
        if (task.target) placed.add(field);
    }

    if (!fallback) return;
    fallback.fragment.querySelectorAll('[' + MESSAGE + ']').forEach(function (line) {
        if (placed.has(line.getAttribute(MESSAGE))) line.remove();
    });
    if (!fallback.fragment.querySelector('[' + MESSAGE + ']')) {
        tasks.splice(tasks.indexOf(fallback), 1);
    }
});

// A new attempt starts clean: a submit or edit clears the messages its form showed last time,
// so a field that is now valid does not keep its old complaint. Only requests that change
// something; an item search inside the form must not wipe what the last submit said.
document.addEventListener('htmx:before:request', function (event) {
    const ctx = event.detail.ctx;
    if (ctx.request.method === 'GET') return;
    const source = ctx.sourceElement;
    const scope = source && (source.closest('form') || source.parentElement);
    if (!scope) return;
    scope.querySelectorAll('[' + SLOT + ']').forEach(function (slot) { slot.replaceChildren(); });
});
