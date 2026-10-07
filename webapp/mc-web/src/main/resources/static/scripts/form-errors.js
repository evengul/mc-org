// Field messages from a validation failure (FieldError.kt: fieldMessages).
//
// The server sends one htmx partial per failed field, aimed at `find [data-error-for=<field>]`
// from the element that sent the request, plus an alert holding every message. htmx places the
// partials whose `find` matched. This finishes the job before anything is swapped:
//
// - a message whose `find` matched nothing goes to the nearest slot for its field above the
//   element that sent the request, within its form (an upload input inside a larger form sends
//   from the input);
// - the alert keeps only the messages no slot took, and is dropped when every one was placed.
//
// So every message is seen once, and a field the page has no slot for still says what is wrong.

const SLOT = 'data-error-for';
const MESSAGE = 'data-field-message';
const FALLBACK = 'data-field-fallback';

// Never past the form (or dialog) the request came from: a slot of the same name in another
// form, a closed dialog's say, is not this field's, and the message belongs in the alert instead.
function nearestSlot(source, field) {
    const selector = '[' + SLOT + '="' + CSS.escape(field) + '"]';
    const limit = source.closest('form, dialog') || source.parentElement;
    for (let node = source; node; node = node.parentElement) {
        const slot = node.querySelector(selector);
        if (slot) return slot;
        if (node === limit) break;
    }
    return null;
}

// The slots a request from `source` answers for: the whole form when the form is what sent it,
// otherwise the sender's own surroundings (an upload input inside a larger form).
function slotsOf(source) {
    const scope = source.matches('form') ? source : source.parentElement;
    return scope ? scope.querySelectorAll('[' + SLOT + ']') : [];
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

// A new attempt starts clean: a submit or edit clears the messages it showed last time, so a
// field that is now valid does not keep its old complaint. Only requests that change something;
// an item search inside the form must not wipe what the last submit said.
document.addEventListener('htmx:before:request', function (event) {
    const ctx = event.detail.ctx;
    if (ctx.request.method === 'GET' || !ctx.sourceElement) return;
    slotsOf(ctx.sourceElement).forEach(function (slot) { slot.replaceChildren(); });
});
