/**
 * ResourceSearch - Client-side filter and sort for resource rows
 *
 * Filter: reads data-item-name attributes on .resource-row elements
 * Sort: reads data-progress-pct attributes and reorders DOM nodes
 * Re-applies active filter after htmx:afterSwap on the resource list container
 */

(function () {
    'use strict';

    let activeSort = 'name';
    let activeSearch = '';

    function initResourceSearch() {
        const searchInput = document.getElementById('resource-search-input');
        const sortBtns = document.querySelectorAll('.resource-sort__btn');
        const resourceList = document.getElementById('resource-list');

        if (!searchInput || !resourceList) return;

        // Guard: skip if this exact element is already wired up.
        // Counter button clicks trigger htmx:afterSettle too, and we must not re-sort then.
        // View switches replace the DOM entirely, so the fresh element won't have this flag.
        if (searchInput.dataset.initialized) return;
        searchInput.dataset.initialized = 'true';

        searchInput.addEventListener('input', function () {
            activeSearch = this.value.toLowerCase().trim();
            applyFilter();
        });

        sortBtns.forEach(function (btn) {
            btn.addEventListener('click', function () {
                sortBtns.forEach(function (b) { b.classList.remove('resource-sort__btn--active'); });
                this.classList.add('resource-sort__btn--active');
                activeSort = this.dataset.sort;
                applySort();
                applyFilter();
            });
        });

        // Re-apply filter after HTMX swaps (counter updates) — intentionally no re-sort
        resourceList.addEventListener('htmx:afterSwap', function () {
            applyFilter();
        });

        // Apply sort once on (re-)init to restore chosen order after view switches
        applySort();
    }

    function applyFilter() {
        const resourceList = document.getElementById('resource-list');
        if (!resourceList) return;

        const rows = resourceList.querySelectorAll('.resource-row');
        let visibleCount = 0;

        rows.forEach(function (row) {
            const itemName = (row.dataset.itemName || '').toLowerCase();
            const matches = activeSearch === '' || itemName.includes(activeSearch);
            row.style.display = matches ? '' : 'none';
            if (matches) visibleCount++;
        });

        // Show/hide "no match" message
        const noMatch = resourceList.querySelector('.resource-list__no-match');
        if (noMatch) {
            noMatch.classList.toggle('resource-list__no-match--visible', visibleCount === 0 && activeSearch !== '');
        }
    }

    function applySort() {
        const resourceList = document.getElementById('resource-list');
        if (!resourceList) return;

        const rows = Array.from(resourceList.querySelectorAll('.resource-row'));
        if (rows.length === 0) return;

        rows.sort(function (a, b) {
            if (activeSort === 'name') {
                return (a.dataset.itemName || '').localeCompare(b.dataset.itemName || '');
            } else if (activeSort === 'progress-asc') {
                return parseInt(a.dataset.progressPct || '0', 10) - parseInt(b.dataset.progressPct || '0', 10);
            } else if (activeSort === 'progress-desc') {
                return parseInt(b.dataset.progressPct || '0', 10) - parseInt(a.dataset.progressPct || '0', 10);
            } else if (activeSort === 'required-desc') {
                return parseInt(b.dataset.required || '0', 10) - parseInt(a.dataset.required || '0', 10);
            }
            return 0;
        });

        rows.forEach(function (row) {
            resourceList.appendChild(row);
        });
    }

    function initFreeEntry() {
        // Delegate click to resource list for free-entry on count display
        const resourceList = document.getElementById('resource-list');
        if (!resourceList) return;

        resourceList.addEventListener('click', function (e) {
            const countEl = e.target.closest('.resource-row__count:not(.resource-row__count--complete)');
            if (!countEl) return;

            const resourceId = countEl.dataset.resourceId;
            const current = countEl.dataset.current;
            const required = countEl.dataset.required;
            const row = countEl.closest('.resource-row');
            if (!row) return;

            // Replace count span with input
            const input = document.createElement('input');
            input.type = 'number';
            input.className = 'resource-row__count-input';
            input.min = '0';
            input.max = required;
            input.value = current;
            input.id = 'count-input-' + resourceId;

            countEl.replaceWith(input);
            input.focus();
            input.select();

            function submitValue() {
                const val = parseInt(input.value, 10);
                if (isNaN(val)) {
                    cancelEdit();
                    return;
                }
                const worldId = row.dataset.worldId;
                const projectId = row.dataset.projectId;
                htmx.ajax('PUT',
                    '/worlds/' + worldId + '/projects/' + projectId + '/resources/gathering/' + resourceId + '/collected',
                    {
                        target: '#resource-row-' + resourceId,
                        swap: 'outerHTML',
                        values: { value: val }
                    }
                );
            }

            function cancelEdit() {
                input.replaceWith(countEl);
            }

            input.addEventListener('blur', function () {
                if (document.activeElement !== input) {
                    submitValue();
                }
            });

            input.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    submitValue();
                } else if (e.key === 'Escape') {
                    cancelEdit();
                }
            });
        });
    }

    function initTasksAnchor() {
        const anchor = document.querySelector('.resource-list__tasks-anchor');
        const taskSection = document.getElementById('task-section');
        if (!anchor || !taskSection) return;

        const observer = new IntersectionObserver(function (entries) {
            anchor.style.display = entries[0].isIntersecting ? 'none' : '';
        }, { threshold: 0 });

        observer.observe(taskSection);
    }

    document.addEventListener('DOMContentLoaded', function () {
        initResourceSearch();
        initFreeEntry();
        initTasksAnchor();
    });

    // Re-init after HTMX page loads (if needed)
    document.addEventListener('htmx:afterSettle', function () {
        initResourceSearch();
        initFreeEntry();
        initTasksAnchor();
    });
})();
