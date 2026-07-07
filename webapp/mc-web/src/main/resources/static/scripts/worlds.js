// Worlds page — custom Minecraft-version dropdown.
// Event-delegated on document so it keeps working after HTMX swaps #worlds-content
// (create / pin re-render the modal, losing any directly-bound listeners).
(function () {
    "use strict";

    var CHECK_SVG =
        '<svg class="icon" width="15px" height="15px" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>';

    function menuOf(select) {
        return select.parentElement ? select.parentElement.querySelector(".version-menu") : null;
    }

    function closeAll(except) {
        document.querySelectorAll("[data-version-select]").forEach(function (select) {
            if (select === except) return;
            var menu = menuOf(select);
            select.classList.remove("version-select--open");
            select.setAttribute("aria-expanded", "false");
            if (menu) menu.setAttribute("hidden", "true");
        });
    }

    function toggle(select) {
        var menu = menuOf(select);
        if (!menu) return;
        var isOpen = select.getAttribute("aria-expanded") === "true";
        closeAll(select);
        if (isOpen) {
            select.classList.remove("version-select--open");
            select.setAttribute("aria-expanded", "false");
            menu.setAttribute("hidden", "true");
        } else {
            select.classList.add("version-select--open");
            select.setAttribute("aria-expanded", "true");
            menu.removeAttribute("hidden");
        }
    }

    function choose(row) {
        var field = row.closest(".version-field");
        if (!field) return;
        var value = row.getAttribute("data-version");
        var input = field.querySelector("[data-version-input]");
        var valueEl = field.querySelector(".version-select__value");
        var select = field.querySelector("[data-version-select]");

        if (input) input.value = value;
        if (valueEl) valueEl.textContent = value;

        field.querySelectorAll(".version-menu__row").forEach(function (r) {
            var selected = r === row;
            r.classList.toggle("version-menu__row--selected", selected);
            r.setAttribute("aria-selected", selected ? "true" : "false");
            var check = r.querySelector("svg.icon");
            if (selected && !check) {
                r.insertAdjacentHTML("beforeend", CHECK_SVG);
            } else if (!selected && check) {
                check.remove();
            }
        });

        if (select) {
            select.classList.remove("version-select--open");
            select.setAttribute("aria-expanded", "false");
            select.focus();
        }
        var menu = row.closest(".version-menu");
        if (menu) menu.setAttribute("hidden", "true");
    }

    document.addEventListener("click", function (e) {
        var select = e.target.closest("[data-version-select]");
        if (select) {
            toggle(select);
            return;
        }
        var row = e.target.closest(".version-menu__row");
        if (row) {
            choose(row);
            return;
        }
        closeAll(null);
    });

    document.addEventListener("keydown", function (e) {
        var select = e.target.closest("[data-version-select]");
        if (select) {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                toggle(select);
            } else if (e.key === "Escape") {
                closeAll(null);
            } else if (e.key === "ArrowDown") {
                e.preventDefault();
                var menu = menuOf(select);
                if (menu && menu.hasAttribute("hidden")) toggle(select);
                var first = menu && menu.querySelector(".version-menu__row");
                if (first) first.focus();
            }
            return;
        }
        var row = e.target.closest(".version-menu__row");
        if (row) {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                choose(row);
            } else if (e.key === "Escape") {
                closeAll(null);
                var sel = row.closest(".version-field").querySelector("[data-version-select]");
                if (sel) sel.focus();
            } else if (e.key === "ArrowDown") {
                e.preventDefault();
                if (row.nextElementSibling) row.nextElementSibling.focus();
            } else if (e.key === "ArrowUp") {
                e.preventDefault();
                if (row.previousElementSibling) row.previousElementSibling.focus();
            }
        }
    });
})();
