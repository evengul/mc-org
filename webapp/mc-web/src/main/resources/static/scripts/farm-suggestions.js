/**
 * farm-suggestions.js — one design per demand in the "Worth a farm" panel (MCO-483).
 *
 * Designs covering the same demand are rendered as one choice: a recommendation plus a fold of
 * alternatives, all inside an element carrying `data-farm-choice`. They are checkboxes rather
 * than radios because the batch form (MCO-459) submits every choice on the page under one field
 * name, and radios would need a field name per group — a wire-format change for a rule the DOM
 * can carry. So the exclusivity is here: ticking one design in a choice unticks the others.
 *
 * Delegated from the document, so it survives every HTMX swap of the plan (the panel is
 * re-rendered whole by the detail-content, chain and dismissal responses) with no re-init.
 * Guarded against double registration for the same reason.
 */
(function () {
    'use strict';

    if (document.body && document.body.dataset.farmChoiceInitialised) return;
    if (document.body) document.body.dataset.farmChoiceInitialised = 'true';

    document.addEventListener('change', function (event) {
        var box = event.target;
        if (!box || !box.classList || !box.classList.contains('plan-farm-scale__select-box')) return;
        if (!box.checked) return;

        var choice = box.closest('[data-farm-choice]');
        if (!choice) return;

        var boxes = choice.querySelectorAll('.plan-farm-scale__select-box');
        for (var i = 0; i < boxes.length; i++) {
            if (boxes[i] !== box) boxes[i].checked = false;
        }
    });
})();
