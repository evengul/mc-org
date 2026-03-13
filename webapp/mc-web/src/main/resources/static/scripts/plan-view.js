/**
 * plan-view.js - Interactions for the project detail plan view
 *
 * - Qty inline edit: click qty cell to edit inline, Enter/blur triggers HTMX PATCH, Escape reverts
 * - Tasks section expand/collapse
 * - Add resource form show/hide
 * - Item search selection handler (sets hidden input + label)
 */

(function () {
    'use strict';

    // -------------------------------------------------------------------------
    // Qty inline edit
    // -------------------------------------------------------------------------

    function initQtyEdit() {
        var table = document.getElementById('plan-resource-table');
        if (!table) return;
        if (table.dataset.qtyInitialized) return;
        table.dataset.qtyInitialized = 'true';

        table.addEventListener('click', function (e) {
            var td = e.target.closest('.plan-resource-table__qty');
            if (!td) return;
            // Don't open again if already editing
            if (td.classList.contains('plan-resource-table__qty--editing')) return;

            var input = td.querySelector('.plan-resource-table__qty-input');
            if (!input) return;

            td.classList.add('plan-resource-table__qty--editing');
            input.focus();
            input.select();
        });

        table.addEventListener('keydown', function (e) {
            var input = e.target;
            if (!input.classList.contains('plan-resource-table__qty-input')) return;
            var td = input.closest('.plan-resource-table__qty');
            if (!td) return;

            if (e.key === 'Enter') {
                e.preventDefault();
                submitQty(td, input);
            } else if (e.key === 'Escape') {
                revertQty(td, input);
            }
        });

        table.addEventListener('blur', function (e) {
            var input = e.target;
            if (!input.classList.contains('plan-resource-table__qty-input')) return;
            var td = input.closest('.plan-resource-table__qty');
            if (!td) return;
            submitQty(td, input);
        }, true);
    }

    function submitQty(td, input) {
        td.classList.remove('plan-resource-table__qty--editing');
        var val = parseInt(input.value, 10);
        if (isNaN(val) || val < 1) {
            revertQty(td, input);
            return;
        }
        // Trigger HTMX PATCH — set form value then dispatch
        htmx.trigger(input, 'change');
    }

    function revertQty(td, input) {
        td.classList.remove('plan-resource-table__qty--editing');
        var original = td.dataset.currentQty || '1';
        input.value = original;
        var display = td.querySelector('.plan-resource-table__qty-display');
        if (display) display.textContent = original;
    }

    // -------------------------------------------------------------------------
    // Tasks expand/collapse
    // -------------------------------------------------------------------------

    function initTasksToggle() {
        var btn = document.getElementById('plan-tasks-toggle');
        var section = document.getElementById('plan-task-section');
        if (!btn || !section) return;
        if (btn.dataset.initialized) return;
        btn.dataset.initialized = 'true';

        btn.addEventListener('click', function () {
            var isCollapsed = section.classList.contains('tasks-section--collapsed');
            if (isCollapsed) {
                section.classList.remove('tasks-section--collapsed');
                section.classList.add('tasks-section--expanded');
                btn.textContent = 'Hide';
            } else {
                section.classList.remove('tasks-section--expanded');
                section.classList.add('tasks-section--collapsed');
                btn.textContent = 'Show';
            }
        });
    }

    // -------------------------------------------------------------------------
    // Add resource form show/hide
    // -------------------------------------------------------------------------

    function initAddResourceForm() {
        var addBtn = document.getElementById('plan-add-resource-btn');
        var cancelBtn = document.getElementById('plan-add-resource-cancel');
        var submitBtn = document.getElementById('plan-add-resource-submit');
        var form = document.getElementById('plan-add-resource-form');
        var resourceForm = document.getElementById('plan-resource-form');
        if (!addBtn || !form) return;
        if (addBtn.dataset.initialized) return;
        addBtn.dataset.initialized = 'true';

        addBtn.addEventListener('click', function () {
            form.classList.add('plan-add-resource-form--visible');
            var searchInput = document.getElementById('plan-item-search');
            if (searchInput) searchInput.focus();
        });

        if (cancelBtn) {
            cancelBtn.addEventListener('click', function () {
                form.classList.remove('plan-add-resource-form--visible');
                resetAddResourceForm();
            });
        }

        if (submitBtn && resourceForm) {
            submitBtn.addEventListener('click', function () {
                var itemId = document.getElementById('plan-selected-item-id');
                var qty = document.getElementById('plan-item-amount');
                if (!itemId || !itemId.value.trim()) return;

                var worldId = getWorldIdFromUrl();
                var projectId = getProjectIdFromUrl();
                if (!worldId || !projectId) return;

                var url = '/worlds/' + worldId + '/projects/' + projectId + '/resources/gathering?context=plan';
                htmx.ajax('POST', url, {
                    target: '#plan-resource-table-body',
                    swap: 'beforeend',
                    values: {
                        requiredItemId: itemId.value.trim(),
                        requiredAmount: qty ? qty.value : '1'
                    }
                });

                resetAddResourceForm();
                var searchInput = document.getElementById('plan-item-search');
                if (searchInput) searchInput.focus();
            });
        }
    }

    function resetAddResourceForm() {
        var searchInput = document.getElementById('plan-item-search');
        var itemId = document.getElementById('plan-selected-item-id');
        var results = document.getElementById('plan-item-search-results');
        var qty = document.getElementById('plan-item-amount');
        if (searchInput) searchInput.value = '';
        if (itemId) itemId.value = '';
        if (results) results.innerHTML = '';
        if (qty) qty.value = '1';
    }

    // -------------------------------------------------------------------------
    // Item search selection (plan view)
    // -------------------------------------------------------------------------

    // Called from search result items via onclick="selectSearchedItem(this)"
    window.selectSearchedItem = function (el) {
        document.getElementById('plan-selected-item-id').value = el.dataset.itemId;
        document.getElementById('plan-item-search').value = el.dataset.itemName;
        var results = document.getElementById('plan-item-search-results');
        if (results) results.innerHTML = '';
    };

    // -------------------------------------------------------------------------
    // Item search keyboard navigation
    // -------------------------------------------------------------------------

    function initItemSearchKeyNav() {
        var searchInput = document.getElementById('plan-item-search');
        if (!searchInput || searchInput.dataset.keyNavInitialized) return;
        searchInput.dataset.keyNavInitialized = 'true';

        searchInput.addEventListener('keydown', function (e) {
            var results = document.getElementById('plan-item-search-results');
            if (!results) return;
            var options = results.querySelectorAll('.item-search-option');
            if (!options.length) return;

            var current = results.querySelector('.item-search-option--focused');
            var idx = Array.prototype.indexOf.call(options, current);

            if (e.key === 'ArrowDown') {
                e.preventDefault();
                var next = idx < options.length - 1 ? options[idx + 1] : options[0];
                setFocused(results, next);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                var prev = idx > 0 ? options[idx - 1] : options[options.length - 1];
                setFocused(results, prev);
            } else if (e.key === 'Enter') {
                if (current) {
                    e.preventDefault();
                    window.selectSearchedItem(current);
                    searchInput.focus();
                }
            } else if (e.key === 'Escape') {
                results.innerHTML = '';
                searchInput.blur();
            }
        });
    }

    function setFocused(results, el) {
        results.querySelectorAll('.item-search-option--focused')
            .forEach(function (o) { o.classList.remove('item-search-option--focused'); });
        el.classList.add('item-search-option--focused');
        el.scrollIntoView({ block: 'nearest' });
    }

    // -------------------------------------------------------------------------
    // URL parsing helpers
    // -------------------------------------------------------------------------

    function getWorldIdFromUrl() {
        var match = window.location.pathname.match(/\/worlds\/(\d+)/);
        return match ? match[1] : null;
    }

    function getProjectIdFromUrl() {
        var match = window.location.pathname.match(/\/projects\/(\d+)/);
        return match ? match[1] : null;
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    function init() {
        initQtyEdit();
        initTasksToggle();
        initAddResourceForm();
        initItemSearchKeyNav();
    }

    document.addEventListener('DOMContentLoaded', init);
    document.addEventListener('htmx:afterSettle', init);
})();
