/**
 * resource-panel.js — plan view resource detail panel wiring
 *
 * - Row click in the plan table opens a <dialog> slide-over panel with that resource's details
 * - Same row click toggles the panel closed
 * - Different row click swaps the panel's inner content in place
 * - Panel closes on: Escape (native <dialog> cancel), backdrop click, back/X button,
 *   view toggle (#project-content swap), row delete
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
        var table = document.getElementById('plan-resource-table');
        if (!table) return;
        if (table.dataset.panelInitialized) return;
        table.dataset.panelInitialized = 'true';

        table.addEventListener('click', function (e) {
            // Ignore clicks on the delete button and the qty cell (already handled by plan-view.js)
            if (e.target.closest('.plan-resource-table__delete-btn')) return;
            if (e.target.closest('.plan-resource-table__qty')) return;

            var tr = e.target.closest('tr[data-resource-id]');
            if (!tr) return;

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

        document.body.addEventListener('htmx:afterSwap', function (e) {
            if (!e.target) return;
            if (e.target.id === 'project-content') closePanel();
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
    }

    function submitPanelQty(cell, input) {
        cell.classList.remove('resource-panel__qty--editing');
        var val = parseInt(input.value, 10);
        if (isNaN(val) || val < 1) {
            revertPanelQty(cell, input);
            return;
        }
        htmx.trigger(input, 'change');
    }

    function revertPanelQty(cell, input) {
        cell.classList.remove('resource-panel__qty--editing');
        var original = cell.dataset.currentQty || '1';
        input.value = original;
        var display = cell.querySelector('.resource-panel__qty-display');
        if (display) display.textContent = original;
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    function init() {
        initDialog();
        initRowClicks();
        initViewToggleCleanup();
        initPanelQtyEdit();
    }

    document.addEventListener('DOMContentLoaded', init);
    document.addEventListener('htmx:afterSettle', init);
})();
