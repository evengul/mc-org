/**
 * item-search.js — the one `selectSearchedItem` (MCO-547).
 *
 * `/items/search` renders every result with `onclick="selectSearchedItem(this)"`, whichever page
 * asked, so every host has to supply a global of that name. There used to be three — one per
 * host, each addressing its own fields by id — and the draft form's two combos in one `<form>`
 * showed why that cannot scale: two globals of one name, the later definition wins (MCO-417).
 *
 * This one resolves the fields from the clicked option's own combo instead. Every host renders
 * the same shape: a `.item-search-combo` holding a `.item-search-input`, a `.item-search-results`
 * the endpoint fills, a hidden `.item-search-selected-id`, and optionally a
 * `.item-search-selected-label`. Ids stay where other scripts address a field directly.
 *
 * The production and resource-detail panels (resource-panel.js) intercept the click in the
 * capture phase and stop it, so this never runs for their combos.
 */
window.selectSearchedItem = function (option) {
    var combo = option.closest('.item-search-combo');
    if (!combo) return;
    var idField = combo.querySelector('.item-search-selected-id');
    var label = combo.querySelector('.item-search-selected-label');
    var input = combo.querySelector('.item-search-input');
    var results = option.closest('.item-search-results');
    if (idField) idField.value = option.dataset.itemId;
    if (label) label.textContent = option.dataset.itemName;
    if (input) input.value = option.dataset.itemName;
    if (results) results.innerHTML = '';
};
