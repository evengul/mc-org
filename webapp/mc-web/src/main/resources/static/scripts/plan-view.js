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
    // Plan activity count: click-to-edit collected (left of the "/")
    //
    // The ".resource-row__count-current" span is keyboard-activatable. Activating it
    // reveals a number input; committing sets an absolute collected value, sent to
    // /plan/progress as a delta (new - current) so it reuses the counter endpoint.
    // Bound once on document (events bubble) so it survives HTMX swaps.
    // -------------------------------------------------------------------------

    function initPlanCountEdit() {
        if (document.body.dataset.planCountInit) return;
        document.body.dataset.planCountInit = 'true';

        document.addEventListener('click', function (e) {
            var trigger = e.target.closest('.resource-row__count-current');
            if (!trigger) return;
            var wrap = trigger.closest('.resource-row__count');
            if (wrap) beginCountEdit(wrap);
        });

        document.addEventListener('keydown', function (e) {
            var t = e.target;
            if (!t.classList) return;
            if (t.classList.contains('resource-row__count-current')) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    var w = t.closest('.resource-row__count');
                    if (w) beginCountEdit(w);
                }
                return;
            }
            if (t.classList.contains('resource-row__count-input')) {
                var wrap = t.closest('.resource-row__count');
                if (!wrap) return;
                if (e.key === 'Enter') { e.preventDefault(); commitCountEdit(wrap, t); }
                else if (e.key === 'Escape') { e.preventDefault(); cancelCountEdit(wrap); }
            }
        });

        // focusout bubbles (blur does not) — commit when the input loses focus.
        document.addEventListener('focusout', function (e) {
            var input = e.target;
            if (!input.classList || !input.classList.contains('resource-row__count-input')) return;
            var wrap = input.closest('.resource-row__count');
            if (wrap) commitCountEdit(wrap, input);
        });
    }

    function beginCountEdit(wrap) {
        if (wrap.classList.contains('resource-row__count--editing')) return;
        var input = wrap.querySelector('.resource-row__count-input');
        if (!input) return;
        input.value = wrap.dataset.current || '0';
        wrap.classList.add('resource-row__count--editing');
        input.focus();
        input.select();
    }

    function cancelCountEdit(wrap) {
        wrap.classList.remove('resource-row__count--editing');
    }

    function commitCountEdit(wrap, input) {
        // Guard against the double-fire of Enter (keydown commit) + focusout commit.
        if (wrap.dataset.committing === 'true') return;
        wrap.classList.remove('resource-row__count--editing');

        var required = parseInt(wrap.dataset.required, 10);
        var current = parseInt(wrap.dataset.current, 10);
        var next = parseInt(input.value, 10);
        if (isNaN(required) || isNaN(current) || isNaN(next)) return;

        if (next < 0) next = 0;
        if (next > required) next = required;
        var delta = next - current;
        if (delta === 0) return; // unchanged — nothing to persist

        var worldId = getWorldIdFromUrl();
        var projectId = getProjectIdFromUrl();
        var itemId = wrap.dataset.itemId;
        var row = wrap.closest('.resource-row');
        if (!worldId || !projectId || !itemId || !row || !row.id) return;

        wrap.dataset.committing = 'true';
        htmx.ajax('PATCH', '/worlds/' + worldId + '/projects/' + projectId + '/plan/progress', {
            target: '#' + row.id,
            swap: 'outerHTML',
            values: { itemId: itemId, amount: delta, required: required }
        });
    }

    // -------------------------------------------------------------------------
    // List resolution toggle (MCO-226): "What I need" (targets) <-> "How to make
    // it" (breakdown). Both views are already in the DOM; this just shows one and
    // persists the choice per project so it survives #project-content re-renders
    // (e.g. the inline variant pick, which re-renders the whole list).
    // -------------------------------------------------------------------------

    var RESOLUTION_DEFAULT = 'targets';

    function resolutionStorageKey() {
        var projectId = getProjectIdFromUrl();
        return projectId ? 'planResolution:' + projectId : null;
    }

    function readResolution() {
        var key = resolutionStorageKey();
        if (!key) return RESOLUTION_DEFAULT;
        try {
            var stored = window.sessionStorage.getItem(key);
            return stored === 'breakdown' ? 'breakdown' : RESOLUTION_DEFAULT;
        } catch (e) {
            return RESOLUTION_DEFAULT;
        }
    }

    function writeResolution(value) {
        var key = resolutionStorageKey();
        if (!key) return;
        try {
            window.sessionStorage.setItem(key, value);
        } catch (e) { /* storage unavailable — non-fatal, state just won't persist */ }
    }

    function applyResolution(value) {
        document.querySelectorAll('.list-resolution-view').forEach(function (view) {
            var active = view.dataset.resolutionView === value;
            view.classList.toggle('list-resolution-view--hidden', !active);
        });
        document.querySelectorAll('.list-resolution__option').forEach(function (btn) {
            var active = btn.dataset.resolution === value;
            btn.classList.toggle('list-resolution__option--active', active);
            btn.setAttribute('aria-selected', active ? 'true' : 'false');
        });
    }

    function initResolutionToggle() {
        var toggle = document.querySelector('.list-resolution');
        if (!toggle) return;
        // Re-apply the persisted choice on every settle (the toggle re-renders with
        // #project-content), but only bind click handlers once.
        applyResolution(readResolution());
        if (toggle.dataset.initialized) return;
        toggle.dataset.initialized = 'true';

        toggle.addEventListener('click', function (e) {
            var btn = e.target.closest('.list-resolution__option');
            if (!btn) return;
            var value = btn.dataset.resolution;
            if (value !== 'targets' && value !== 'breakdown') return;
            writeResolution(value);
            applyResolution(value);
        });
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
    // Drill scroll restoration (MCO-244): drilling into a target's source chain
    // (DrillView.kt) and "< Back to plan" both outerHTML-swap #project-content, which
    // otherwise leaves the page scrolled wherever the new (usually shorter) content
    // happens to land. Remember the scroll position on the way in — via the
    // `data-drill-nav="in"` marker on every drill trigger (ProjectDetailPage.kt) — and
    // restore it once #project-content settles back on a non-drill view (no
    // `.drill-header`, see DrillView.kt). Scoped per-project via sessionStorage so it
    // survives the swap and multiple drill round-trips.
    // -------------------------------------------------------------------------

    function drillScrollKey() {
        var projectId = getProjectIdFromUrl();
        return projectId ? 'drillScroll:' + projectId : null;
    }

    function initDrillScrollRestore() {
        if (document.body.dataset.drillScrollInit) return;
        document.body.dataset.drillScrollInit = 'true';

        document.body.addEventListener('click', function (e) {
            var trigger = e.target.closest('[data-drill-nav="in"]');
            if (!trigger) return;
            var key = drillScrollKey();
            if (!key) return;
            try {
                window.sessionStorage.setItem(key, String(window.scrollY));
            } catch (err) { /* storage unavailable — non-fatal, scroll just won't restore */ }
        });

        document.body.addEventListener('htmx:afterSettle', function (e) {
            if (!e.target || e.target.id !== 'project-content') return;
            if (e.target.querySelector('.drill-header')) return; // still in the drill chain
            var key = drillScrollKey();
            if (!key) return;
            try {
                var saved = window.sessionStorage.getItem(key);
                if (saved === null) return;
                window.sessionStorage.removeItem(key);
                window.scrollTo(0, parseInt(saved, 10));
            } catch (err) { /* storage unavailable */ }
        });
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
        initPlanCountEdit();
        initResolutionToggle();
        initDrillScrollRestore();
    }

    document.addEventListener('DOMContentLoaded', init);
    document.addEventListener('htmx:afterSettle', init);
})();
