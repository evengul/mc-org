/**
 * farm-modal.js — "record an existing farm" modal (MCO-298)
 *
 * The project does not exist yet, so produced items cannot be posted to the
 * productions endpoint the way the project-detail panel does. They are staged here as
 * `productions[<itemId>]` hidden inputs and submitted with the form; the server
 * resolves each id against the world's item catalog.
 */
(function () {
    'use strict';

    function el(id) {
        return document.getElementById(id);
    }

    /**
     * Called by the /items/search result rows (they render onclick="selectSearchedItem(this)").
     * This page has exactly one item search — the farm modal's.
     */
    window.selectSearchedItem = function (option) {
        var idField = el('record-farm-selected-item-id');
        var label = el('record-farm-selected-item-label');
        var input = el('record-farm-item-input');
        var results = el('record-farm-item-results');
        if (!idField || !label || !input) return;

        idField.value = option.dataset.itemId;
        label.textContent = option.dataset.itemName;
        input.value = option.dataset.itemName;
        if (results) results.innerHTML = '';
    };

    window.addFarmProduction = function () {
        var idField = el('record-farm-selected-item-id');
        var label = el('record-farm-selected-item-label');
        var rateField = el('record-farm-rate');
        var list = el('farm-production-list');
        if (!idField || !list) return;

        var itemId = idField.value.trim();
        if (!itemId) return;
        var itemName = (label && label.textContent.trim()) || itemId;
        var rate = rateField && rateField.value.trim() !== '' ? parseInt(rateField.value, 10) : 0;
        if (isNaN(rate) || rate < 0) rate = 0;

        // Re-adding an item replaces its row: the server upserts on (project_id, item_id),
        // so two rows for one item would silently collapse to the last rate anyway.
        var rowId = 'farm-production-' + itemId;
        var existing = el(rowId);
        if (existing) existing.remove();

        var row = document.createElement('div');
        row.id = rowId;
        row.className = 'farm-production-row';

        var name = document.createElement('span');
        name.className = 'farm-production-row__name';
        name.textContent = itemName;
        row.appendChild(name);

        var rateLabel = document.createElement('span');
        rateLabel.className = 'farm-production-row__rate';
        rateLabel.textContent = rate > 0 ? rate + '/hr' : 'rate unknown';
        row.appendChild(rateLabel);

        var hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = 'productions[' + itemId + ']';
        hidden.value = String(rate);
        row.appendChild(hidden);

        var remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'btn btn--ghost btn--sm farm-production-row__remove';
        remove.setAttribute('aria-label', 'Remove ' + itemName);
        remove.textContent = '×';
        remove.addEventListener('click', function () {
            row.remove();
        });
        row.appendChild(remove);

        list.appendChild(row);

        idField.value = '';
        if (label) label.textContent = '';
        var input = el('record-farm-item-input');
        if (input) input.value = '';
        if (rateField) rateField.value = '';
    };

    /**
     * form.reset() does not remove the staged rows (they are not form defaults), so the
     * next farm would inherit the previous one's items.
     */
    window.resetFarmModal = function (form) {
        if (form) form.reset();
        var list = el('farm-production-list');
        if (list) list.innerHTML = '';
        var idField = el('record-farm-selected-item-id');
        if (idField) idField.value = '';
        var label = el('record-farm-selected-item-label');
        if (label) label.textContent = '';
        var results = el('record-farm-item-results');
        if (results) results.innerHTML = '';
        document.querySelectorAll('#record-farm-form .validation-error-message').forEach(function (error) {
            error.textContent = '';
        });
        var dialog = el('record-farm-modal');
        if (dialog && dialog.open) dialog.close();
    };
})();
