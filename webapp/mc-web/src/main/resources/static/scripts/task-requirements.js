// Task Requirements Form Dynamic Functionality
let itemRequirementCounter = 0;
let actionRequirementCounter = 0;

function clearElementValidationMessage(element) {
  if (element) {
    element.setCustomValidity("");
    element.reportValidity();
  }
}

function clearValidationMessages(inputsToClear) {
  const nameInput = document.getElementById('item-requirement-name-input');
  const amountInput = document.getElementById('item-requirement-amount-input');
  const actionInput = document.getElementById('action-requirement-input');

  if (nameInput && inputsToClear.includes('name')) {
    clearElementValidationMessage(nameInput)
  }
  if (amountInput && inputsToClear.includes('amount')) {
    clearElementValidationMessage(amountInput)
  }
  if (actionInput && inputsToClear.includes('action')) {
    clearElementValidationMessage(actionInput)
  }
}

// Tab switching functionality for create task modal
function switchTab(tabName) {
  switch (tabName) {
    case 'item-requirements-tab':
      switchToItems();
      break;
    case 'action-requirements-tab':
      switchToActions();
      break;
  }
}

function switchToActions() {
  clearValidationMessages(['name', 'amount']);
  document.getElementById("action-requirements-tab").style.display = 'block';
  document.getElementById("item-requirements-tab").style.display = 'none';

  const nameInput = document.getElementById('item-requirement-name-input');
  nameInput.value = '';
  nameInput.required = false;

  const amountInput = document.getElementById('item-requirement-amount-input');
  amountInput.value = '';
  amountInput.required = false;

  const actionInput = document.getElementById('action-requirement-input');
  actionInput.required = true;
  actionInput.minLength = "3";
  actionInput.maxLength = "100";

  document.getElementById("action-requirement-input")?.focus();
}

function switchToItems() {
  clearValidationMessages(['action']);
  document.getElementById("action-requirements-tab").style.display = 'none';
  document.getElementById("item-requirements-tab").style.display = 'block';

  const actionInput = document.getElementById('action-requirement-input');
  actionInput.value = '';
  actionInput.required = false;

  const nameInput = document.getElementById('item-requirement-name-input');
  nameInput.required = true;
  nameInput.minLength = "3";
  nameInput.maxLength = "200";

  const amountInput = document.getElementById('item-requirement-amount-input');
  amountInput.required = true;
  amountInput.min = "1";
  amountInput.max = "2000000000";

  document.getElementById("item-requirement-name-input")?.focus();
}

// Initialize the form when modal closes
function initializeTaskRequirementsForm() {
  itemRequirementCounter = 0;
  actionRequirementCounter = 0;
  clearValidationMessages(['name', 'amount', 'action']);

  const forms = document.getElementsByClassName("create-task-form");

  for (let i = 0; i < forms.length; i++) {
    forms[i].reset();
  }

  const containers = document.getElementsByClassName('requirements-list');
  for (let i = 0; i < containers.length; i++) {
    containers[i].innerHTML = '';
  }

  switchTab('item-requirements-tab');
}

function disableEnterSubmit(elementId) {
  const element = document.getElementById(elementId);
  if (element) {
    element.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.keyCode === 13) {
        event.preventDefault();
        return false;
      }
    });
  }
}

// Call initialization when the modal is shown
document.addEventListener('DOMContentLoaded', function () {
  const modal = document.getElementById('create-task-modal');
  if (modal) {
    modal.addEventListener('close', initializeTaskRequirementsForm);
  }

  document.getElementById('item-requirement-name-input')?.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      addItemRequirement();
      return false;
    }
  });

  document.getElementById('item-requirement-amount-input')?.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      addItemRequirement();
      return false;
    }
  });

  document.getElementById('action-requirement-input')?.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      addActionRequirement();
      return false;
    }
  });
});
