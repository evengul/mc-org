function addIdeaItemRequirement() {
  const hiddenContainer = document.getElementById('hidden-item-requirements-fields')
  const visibleContainer = document.getElementById('idea-item-requirements-list');
  const itemSelect = document.getElementById('item-requirements-items-select-value');
  const amountInput = document.getElementById('item-requirements-amount');

  if (!hiddenContainer || !itemSelect || !amountInput || !visibleContainer) {
    return;
  }

  if (!itemSelect.reportValidity() || !amountInput.reportValidity()) {
    return;
  }

  const selectedItemId = itemSelect.value
  const amount = amountInput.value;

  const hiddenElement = document.createElement('input');
  hiddenElement.type = 'hidden';
  hiddenElement.name = `itemRequirements.${selectedItemId}`;
  hiddenElement.value = amount;

  hiddenContainer.appendChild(hiddenElement);

  const visibleElement = document.createElement('li');
  visibleElement.textContent = selectedItemId + ' x ' + amount;
  visibleContainer.prepend(visibleElement);

  // Optionally, reset the input fields after adding
  itemSelect.value = '';
  amountInput.value = '1';

  const trigger = document.querySelector('.searchable-select__trigger');
  const triggerText = trigger.querySelector('.searchable-select__trigger-text');
  const placeholder = document.querySelector('.searchable-select__trigger-text--placeholder')?.textContent || 'Select an option...';
  triggerText.textContent = triggerText.dataset.placeholder || triggerText.textContent || placeholder;
  triggerText.classList.add('searchable-select__trigger-text--placeholder');

  itemSelect.dispatchEvent(new Event('change', { bubbles: true }));
}