/**
 * MCO-315 — the import review list travels as one form field, not two per row.
 *
 * Two fields per row walked into Ktor's 1000-parameter body cap, which stops decoding at the
 * limit instead of failing: a 560-material schematic silently lost everything past row ~466.
 * The list now rides in a single hidden field (see ReviewedMaterialsCodec), which means the
 * include checkboxes carry no name of their own — this file is what folds their state back
 * into that field.
 *
 * The DOM is the source of truth; the field is only transport. It is rebuilt on every change
 * and again on submit, so a missed change event cannot turn into a wrong list on the wire.
 */
(function () {
    var FORM_ID = 'import-review-form';
    var FIELD_ID = 'import-review-materials-field';
    var VERSION = 'v1';

    var form = document.getElementById(FORM_ID);
    if (!form) {
        return;
    }

    /** Encodes the current checkbox state, or null when the review section is not on the page. */
    function encode() {
        var boxes = form.querySelectorAll('.import-review__include');
        var rows = [];
        for (var i = 0; i < boxes.length; i++) {
            var box = boxes[i];
            var itemId = box.getAttribute('data-item-id');
            var amount = box.getAttribute('data-amount');
            if (!itemId || !amount) {
                continue;
            }
            rows.push((box.checked ? '' : '!') + itemId + '=' + amount);
        }
        var payload = VERSION + ';' + rows.length;
        return rows.length === 0 ? payload : payload + ';' + rows.join(';');
    }

    /** Writes the encoded list into the hidden field and hands it back. */
    function sync() {
        var field = document.getElementById(FIELD_ID);
        if (!field) {
            return null;
        }
        field.value = encode();
        return field.value;
    }

    /** The row checkboxes inside one section (MCO-398). */
    function rowsIn(regionBox) {
        var section = regionBox.closest('.import-review__region');
        return section ? section.querySelectorAll('.import-review__include') : [];
    }

    /**
     * Reflects a section's rows back onto its header box: all on, all off, or indeterminate.
     * Without this, unticking one row of a fully-included section would leave the header
     * claiming the whole section is in.
     */
    function refreshRegion(regionBox) {
        var rows = rowsIn(regionBox);
        var checked = 0;
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].checked) checked++;
        }
        regionBox.checked = rows.length > 0 && checked === rows.length;
        regionBox.indeterminate = checked > 0 && checked < rows.length;
    }

    function refreshAllRegions() {
        var boxes = form.querySelectorAll('.import-review__region-include');
        for (var i = 0; i < boxes.length; i++) {
            refreshRegion(boxes[i]);
        }
    }

    form.addEventListener('change', function (event) {
        var target = event.target;
        if (!target || !target.classList) {
            return;
        }
        if (target.classList.contains('import-review__region-include')) {
            var rows = rowsIn(target);
            for (var i = 0; i < rows.length; i++) {
                rows[i].checked = target.checked;
            }
            target.indeterminate = false;
            sync();
            return;
        }
        if (target.classList.contains('import-review__include')) {
            refreshAllRegions();
            sync();
        }
    });

    // A click on the header checkbox must not also open or close the section — <summary>
    // toggles the disclosure for any click inside it.
    form.addEventListener('click', function (event) {
        var target = event.target;
        if (target && target.classList && target.classList.contains('import-review__region-include')) {
            event.stopPropagation();
        }
    });

    refreshAllRegions();

    form.addEventListener('submit', function () {
        sync();
    });
})();
