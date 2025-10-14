// Task Requirements Form Dynamic Functionality
let itemRequirementCounter = 0;
let actionRequirementCounter = 0;

function validateName(name) {
  const trimmedName = name.trim();
  if (trimmedName.length < 3) {
    return {isValid: false, message: "Name must be at least 3 characters long"};
  }
  if (trimmedName.length > 200) {
    return {isValid: false, message: "Name must be less than 200 characters"};
  }
  return {isValid: true};
}

function validateAmount(amount) {
  const numAmount = parseInt(amount, 10);
  if (isNaN(numAmount) || numAmount <= 0) {
    return {isValid: false, message: "Amount must be a positive number"};
  }
  if (numAmount >= 1000000) {
    return {isValid: false, message: "Amount must be less than 1,000,000"};
  }
  return {isValid: true};
}

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

function addItemRequirement() {
  const container = document.getElementById('item-requirements-container');
  const template = document.getElementById('requirement-template');
  const nameInput = document.getElementById('item-requirement-name-input');
  const amountInput = document.getElementById('item-requirement-amount-input');

  if (!container || !template || !nameInput || !amountInput) return;

  // Validate input
  const nameValidation = validateName(nameInput.value)
  if (!nameValidation.isValid) {
    nameInput.setCustomValidity(nameValidation.message)
    nameInput.reportValidity()

    if (nameInput.value === '') {
      setTimeout(() => {
        clearElementValidationMessage(nameInput)
      }, 300)
    }
    return
  } else {
    clearElementValidationMessage(nameInput);
  }

  const amountValidation = validateAmount(amountInput.value)
  if (!amountValidation.isValid) {
    amountInput.setCustomValidity(amountValidation.message)
    amountInput.reportValidity()
    if (amountInput.value === '') {
      setTimeout(() => {
        clearElementValidationMessage(amountInput)
      }, 300)
    }
    return
  } else {
    clearElementValidationMessage(amountInput)
  }

  // Clone the template

  const newRequirement = template.cloneNode(true);
  newRequirement.id = `item-requirement-${itemRequirementCounter}`;

  const paragraph = newRequirement.children.item(0);
  const input = newRequirement.children.item(1);
  const deleteButton = newRequirement.children.item(2);

  if (!paragraph || !input || !deleteButton) return;

  paragraph.textContent = `${nameInput.value} x${amountInput.value}`;
  const value = {
    name: nameInput.value,
    requiredAmount: parseInt(amountInput.value, 10)
  }
  input.value = JSON.stringify(value);
  input.name = `itemRequirements[${itemRequirementCounter}]`;
  input.id = `item-requirement-input-${itemRequirementCounter}`;
  input.type = 'hidden';

  // Clear the input fields
  nameInput.value = '';
  amountInput.value = '';

  // Set up delete button
  deleteButton.addEventListener('click', () => removeRequirement(deleteButton));

  // Update all name attributes and IDs
  updateRequirementIndexes(newRequirement, itemRequirementCounter);

  container.appendChild(newRequirement);
  itemRequirementCounter++;

  document.getElementById("item-requirement-name-input")?.focus();
}

function addActionRequirement() {
  const container = document.getElementById('action-requirements-container');
  const template = document.getElementById('requirement-template');
  const nameInput = document.getElementById('action-requirement-input');

  if (!container || !template || !nameInput) return;

  // Validate input
  const nameValidation = validateName(nameInput.value)
  if (!nameValidation.isValid) {
    nameInput.setCustomValidity(nameValidation.message)
    nameInput.reportValidity()
    if (nameInput.value === '') {
      setTimeout(() => {
        clearElementValidationMessage(nameInput)
      }, 300)
    }
    return
  } else {
    clearElementValidationMessage(nameInput);
  }

  // Clone the template

  const newRequirement = template.cloneNode(true);
  newRequirement.id = `item-requirement-${actionRequirementCounter}`;

  const paragraph = newRequirement.children.item(0);
  const input = newRequirement.children.item(1);
  const deleteButton = newRequirement.children.item(2);

  if (!paragraph || !input || !deleteButton) return;

  paragraph.textContent = nameInput.value;
  input.value = nameInput.value;
  input.name = `actionRequirements[${actionRequirementCounter}]`;
  input.id = `action-requirement-input-${actionRequirementCounter}`;
  input.type = 'hidden';

  // Clear the input fields
  nameInput.value = '';

  // Set up delete button
  deleteButton.addEventListener('click', () => removeRequirement(deleteButton));

  // Update all name attributes and IDs
  updateRequirementIndexes(newRequirement, actionRequirementCounter);

  container.appendChild(newRequirement);
  actionRequirementCounter++;

  document.getElementById("action-requirement-input")?.focus();
}

function removeRequirement(button) {
  const requirementItem = button.closest('.requirement-item');
  if (requirementItem) {
    requirementItem.remove();

    // Reindex all remaining requirements
    reindexAllRequirements();
  }
}

function updateRequirementIndexes(element, index) {
  // Update all name attributes
  const inputs = element.querySelectorAll('input, textarea');
  inputs.forEach(input => {
    if (input.name) {
      input.name = input.name.replace('[INDEX]', `[${index}]`);
      input.name = input.name.replace(/\[\d+]/, `[${index}]`);
    }
    if (input.id) {
      input.id = input.id.replace(/-\d+$/, `-${index}`);
    }

    // Add required attribute for form fields that need validation
    if (input.type === 'text' || input.type === 'number' || input.tagName.toLowerCase() === 'textarea') {
      if (input.name && input.name.includes('item')) {
        input.required = true;
      }
      if (input.name && input.name.includes('requiredAmount')) {
        input.required = true;
      }
      if (input.name && input.name.includes('action')) {
        input.required = true;
      }
    }
  });

  // Update label for attributes
  const labels = element.querySelectorAll('label');
  labels.forEach(label => {
    if (label.htmlFor) {
      label.htmlFor = label.htmlFor.replace(/-\d+$/, `-${index}`);
    }
  });
}

function reindexAllRequirements() {
  const container = document.getElementById('requirements-container');
  if (!container) return;

  const requirements = container.querySelectorAll('.requirement-item');
  requirements.forEach((requirement, index) => {
    requirement.setAttribute('data-requirement-index', index);
    updateRequirementIndexes(requirement, index);
  });

  // Update the counter
  itemRequirementCounter = requirements.length;
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

  document.getElementById("action-requirement-input")?.focus();
}

function switchToItems() {
  clearValidationMessages(['action']);
  document.getElementById("action-requirements-tab").style.display = 'none';
  document.getElementById("item-requirements-tab").style.display = 'block';

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
