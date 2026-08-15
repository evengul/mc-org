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

    form.addEventListener('change', function (event) {
        var target = event.target;
        if (target && target.classList && target.classList.contains('import-review__include')) {
            sync();
        }
    });

    form.addEventListener('submit', function () {
        sync();
    });
})();
