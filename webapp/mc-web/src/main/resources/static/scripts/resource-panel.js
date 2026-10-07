/**
 * resource-panel.js — plan view resource detail panel wiring
 *
 * - Row click in the plan table opens a <dialog> slide-over panel with that resource's details
 * - Same row click toggles the panel closed
 * - Different row click swaps the panel's inner content in place
 * - Panel closes on: Escape (native <dialog> cancel), backdrop click, back/X button,
 *   view toggle (#project-content swap, unless the panel itself asked for it), row delete
 * - Inline qty edit inside the panel mirrors plan-view.js behaviour
 */
(function () {
    'use strict';

    var currentResourceId = null;

    function getDialog() {
        return document.getElementById('resource-panel');
    }

    function getContent() {
        return document.getElementById('resource-panel-content');
    }

    function getWorldIdFromUrl() {
        var m = window.location.pathname.match(/\/worlds\/(\d+)/);
        return m ? m[1] : null;
    }

    function getProjectIdFromUrl() {
        var m = window.location.pathname.match(/\/projects\/(\d+)/);
        return m ? m[1] : null;
    }

    function openPanel(resourceId) {
        var dialog = getDialog();
        var content = getContent();
        if (!dialog || !content) return;

        var worldId = getWorldIdFromUrl();
        var projectId = getProjectIdFromUrl();
        if (!worldId || !projectId) return;

        var url = '/worlds/' + worldId + '/projects/' + projectId +
                  '/resources/gathering/' + resourceId + '/detail-panel';

        htmx.ajax('GET', url, { target: '#resource-panel-content', swap: 'innerHTML' })
            .then(function () {
                if (!dialog.open) dialog.showModal();
                currentResourceId = String(resourceId);
            });
    }

    function closePanel() {
        var dialog = getDialog();
        if (dialog && dialog.open) dialog.close();
    }

    // -------------------------------------------------------------------------
    // Row click delegation on the plan table
    // -------------------------------------------------------------------------

    function initRowClicks() {
        // The area, not #plan-resource-table: the folded single-item tail is a table of its own
        // beside it, and its rows open the panel like any other.
        var table = document.getElementById('plan-resources-area');
        if (!table) return;
        if (table.dataset.panelInitialized) return;
        table.dataset.panelInitialized = 'true';

        table.addEventListener('click', function (e) {
            // The action cell (ignore, delete) and the qty cell (plan-view.js) are controls of
            // their own. A click on ⊘ that also opened the panel had it open and then shut as
            // the ignore's re-render landed.
            if (e.target.closest('.plan-resource-table__action')) return;
            if (e.target.closest('.plan-resource-table__qty')) return;

            var tr = e.target.closest('tr[data-resource-id]');
            if (!tr) return;
            // An ignored row's one action is Un-ignore; it has no panel.
            if (tr.closest('#plan-ignored-section')) return;

            var resourceId = tr.dataset.resourceId;
            if (!resourceId) return;

            if (currentResourceId === resourceId) {
                closePanel();
            } else {
                openPanel(resourceId);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Dialog close controls (back, X, backdrop, cancel)
    // -------------------------------------------------------------------------

    function initDialog() {
        var dialog = getDialog();
        if (!dialog) return;
        if (dialog.dataset.panelInitialized) return;
        dialog.dataset.panelInitialized = 'true';

        // Backdrop click (showModal gives us a ::backdrop; a click on the dialog itself
        // whose target is the dialog element — not a child — came from the backdrop)
        dialog.addEventListener('click', function (e) {
            if (e.target === dialog) dialog.close();
        });

        // Delegated close buttons inside the panel content
        dialog.addEventListener('click', function (e) {
            if (e.target.closest('[data-resource-panel-close]')) {
                dialog.close();
            }
        });

        // Escape key fires native 'cancel' event on <dialog>
        dialog.addEventListener('cancel', function () {
            // Let close fire naturally
        });

        dialog.addEventListener('close', function () {
            currentResourceId = null;
            var content = getContent();
            if (content) content.innerHTML = '';
        });
    }

    // -------------------------------------------------------------------------
    // Close panel when switching to a different view (project-content swap)
    // -------------------------------------------------------------------------

    function initViewToggleCleanup() {
        if (document.body.dataset.resourcePanelToggleInitialized) return;
        document.body.dataset.resourcePanelToggleInitialized = 'true';

        document.body.addEventListener('htmx:after:settle', function (e) {
            if (!e.target) return;
            if (e.target.id !== 'project-content') return;
            // A change made in the panel (source, quantity, variant) brings the plan along out of
            // band (MCO-585), marked data-out-of-band. Closing on that would shut the panel the
            // user is working in; only a swap that replaces the view closes it.
            if (e.target.dataset && e.target.dataset.outOfBand === 'true') return;
            closePanel();
        });

        // A new panel body (another row, or a variant picked from the chips) starts at its top,
        // where its title is — not wherever the chip list had been scrolled to.
        document.body.addEventListener('htmx:afterSwap', function (e) {
            if (!e.target || e.target.id !== 'resource-panel-content') return;
            var dialog = getDialog();
            if (dialog) dialog.scrollTop = 0;
        });
    }

    // -------------------------------------------------------------------------
    // Inline qty edit inside the panel (mirrors plan-view.js)
    // -------------------------------------------------------------------------

    function initPanelQtyEdit() {
        var dialog = getDialog();
        if (!dialog) return;
        if (dialog.dataset.qtyInitialized) return;
        dialog.dataset.qtyInitialized = 'true';

        dialog.addEventListener('click', function (e) {
            var cell = e.target.closest('.resource-panel__qty');
            if (!cell) return;
            if (cell.classList.contains('resource-panel__qty--editing')) return;
            var input = cell.querySelector('.resource-panel__qty-input');
            if (!input) return;
            cell.classList.add('resource-panel__qty--editing');
            input.focus();
            input.select();
        });

        dialog.addEventListener('keydown', function (e) {
            var input = e.target;
            if (!input.classList || !input.classList.contains('resource-panel__qty-input')) return;
            var cell = input.closest('.resource-panel__qty');
            if (!cell) return;
            if (e.key === 'Enter') {
                e.preventDefault();
                submitPanelQty(cell, input);
            } else if (e.key === 'Escape') {
                revertPanelQty(cell, input);
            }
        });

        dialog.addEventListener('blur', function (e) {
            var input = e.target;
            if (!input.classList || !input.classList.contains('resource-panel__qty-input')) return;
            var cell = input.closest('.resource-panel__qty');
            if (!cell) return;
            submitPanelQty(cell, input);
        }, true);

        // The edit's response is the plan, out of band; nothing re-renders the panel. So the
        // panel takes the saved number itself — on success only, so a rejected one isn't shown.
        dialog.addEventListener('htmx:afterRequest', function (e) {
            var input = e.target;
            if (!input.classList || !input.classList.contains('resource-panel__qty-input')) return;
            if (!e.detail.successful) return;
            var cell = input.closest('.resource-panel__qty');
            if (!cell) return;
            // The number the server stored ("007" is 7), not what was typed.
            var saved = String(parseInt(input.value, 10));
            input.value = saved;
            cell.dataset.currentQty = saved;
            var display = cell.querySelector('.resource-panel__qty-display');
            if (display) display.textContent = saved;
        });
    }

    function submitPanelQty(cell, input) {
        // Enter submits and leaves editing; the blur that follows must not submit again.
        if (!cell.classList.contains('resource-panel__qty--editing')) return;
        cell.classList.remove('resource-panel__qty--editing');
        var val = parseInt(input.value, 10);
        // Unchanged is not an edit: each one re-derives the whole plan.
        if (isNaN(val) || val < 1 || String(val) === cell.dataset.currentQty) {
            revertPanelQty(cell, input);
            return;
        }
        htmx.trigger(input, 'qty-commit');
    }

    function revertPanelQty(cell, input) {
        cell.classList.remove('resource-panel__qty--editing');
        var original = cell.dataset.currentQty || '1';
        input.value = original;
        var display = cell.querySelector('.resource-panel__qty-display');
        if (display) display.textContent = original;
    }

    // -------------------------------------------------------------------------
    // "Replace item" search selection (MCO-246)
    //
    // The /items/search results reused in the panel's "Replace item" section carry a
    // hardcoded inline onclick="selectSearchedItem(this)" — but the page-global
    // selectSearchedItem (plan-view.js) targets the ADD-resource form's fields, which is
    // wrong here. We intercept clicks on options inside #resource-panel-variant-results in
    // the CAPTURE phase and stopPropagation, which prevents the event from reaching the
    // option's own inline onclick, then perform the swap PATCH ourselves. The swap URL is
    // read from the results container's data-swap-url (rendered by resourcePanelVariantSection).
    // -------------------------------------------------------------------------

    function initVariantSearch() {
        var dialog = getDialog();
        if (!dialog) return;
        if (dialog.dataset.variantSearchInitialized) return;
        dialog.dataset.variantSearchInitialized = 'true';

        dialog.addEventListener('click', function (e) {
            var results = e.target.closest('#resource-panel-variant-results');
            if (!results) return;
            var option = e.target.closest('.item-search-option');
            if (!option) return;

            // Stop the option's inline onclick (plan-view's selectSearchedItem) from firing.
            e.stopPropagation();
            e.preventDefault();

            var itemId = option.dataset.itemId;
            var swapUrl = results.dataset.swapUrl;
            if (!itemId || !swapUrl) return;

            // The panel is the main swap; the plan comes back out of band (MCO-585).
            htmx.ajax('PATCH', swapUrl, {
                target: '#resource-panel-content',
                swap: 'innerHTML',
                values: { itemId: itemId }
            });
        }, true); // capture phase — runs before the option's own inline handler
    }

    // -------------------------------------------------------------------------
    // Produces panel (MCO-297)
    //
    // The meta-row chip (and its empty-state "+ Produces" variant) opens the shared
    // <dialog> with the productions editor. The add-item search reuses /items/search
    // like the variant search above, with its own results container.
    // -------------------------------------------------------------------------

    function initProductionPanel() {
        if (!document.body.dataset.productionChipInitialized) {
            document.body.dataset.productionChipInitialized = 'true';
            document.body.addEventListener('click', function (e) {
                var trigger = e.target.closest('[data-production-panel-url]');
                if (!trigger) return;
                var dialog = getDialog();
                var content = getContent();
                if (!dialog || !content) return;
                htmx.ajax('GET', trigger.dataset.productionPanelUrl, {
                    target: '#resource-panel-content',
                    swap: 'innerHTML'
                }).then(function () {
                    if (!dialog.open) dialog.showModal();
                    currentResourceId = null;
                });
            });
        }

        var dialog = getDialog();
        if (!dialog) return;
        if (dialog.dataset.productionSearchInitialized) return;
        dialog.dataset.productionSearchInitialized = 'true';

        dialog.addEventListener('click', function (e) {
            var results = e.target.closest('#production-panel-item-results');
            if (!results) return;
            var option = e.target.closest('.item-search-option');
            if (!option) return;

            e.stopPropagation();
            e.preventDefault();

            var itemId = option.dataset.itemId;
            var addUrl = results.dataset.productionAddUrl;
            if (!itemId || !addUrl) return;
            var rateInput = document.getElementById('production-panel-rate');
            // Only a farm with runtime modes renders the picker; the server requires it there and
            // refuses it anywhere else (MCO-413).
            var modeSelect = document.getElementById('production-panel-mode');
            var values = { itemId: itemId, ratePerHour: (rateInput && rateInput.value) || '0' };
            if (modeSelect) values.modeId = modeSelect.value;

            htmx.ajax('POST', addUrl, {
                target: '#resource-panel-content',
                swap: 'innerHTML',
                values: values
            });
        }, true); // capture phase — see variant search above
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    function init() {
        initDialog();
        initRowClicks();
        initViewToggleCleanup();
        initPanelQtyEdit();
        initVariantSearch();
        initProductionPanel();
    }

    document.addEventListener('DOMContentLoaded', init);
    document.addEventListener('htmx:after:settle', init);
})();
