// Task Requirements Form Dynamic Functionality
let requirementCounter = 0;

function addItemRequirement() {
    const container = document.getElementById('requirements-container');
    const template = document.getElementById('item-requirement-template');

    if (!container || !template) return;

    // Clone the template
    const newRequirement = template.cloneNode(true);
    newRequirement.style.display = 'block';
    newRequirement.id = `item-requirement-${requirementCounter}`;
    newRequirement.setAttribute('data-requirement-index', requirementCounter);

    // Update all name attributes and IDs
    updateRequirementIndexes(newRequirement, requirementCounter);

    container.appendChild(newRequirement);
    requirementCounter++;

    // Focus on the first input of the new requirement
    const firstInput = newRequirement.querySelector('input[type="text"]');
    if (firstInput) {
        firstInput.focus();
    }
}

function addActionRequirement() {
    const container = document.getElementById('requirements-container');
    const template = document.getElementById('action-requirement-template');

    if (!container || !template) return;

    // Clone the template
    const newRequirement = template.cloneNode(true);
    newRequirement.style.display = 'block';
    newRequirement.id = `action-requirement-${requirementCounter}`;
    newRequirement.setAttribute('data-requirement-index', requirementCounter);

    // Update all name attributes and IDs
    updateRequirementIndexes(newRequirement, requirementCounter);

    container.appendChild(newRequirement);
    requirementCounter++;

    // Focus on the textarea of the new requirement
    const textarea = newRequirement.querySelector('textarea');
    if (textarea) {
        textarea.focus();
    }
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
    requirementCounter = requirements.length;
}

// Tab switching functionality for create task modal
function switchTab(tabName) {
    // Hide all tab contents
    const contents = document.querySelectorAll('.tab-content');
    contents.forEach(content => {
        content.style.display = 'none';
    });

    // Remove active class from all tabs
    const tabs = document.querySelectorAll('.tab-button');
    tabs.forEach(tab => {
        tab.classList.remove('active');
    });

    // Show selected tab content
    const selectedContent = document.getElementById(`${tabName}-tab`);
    if (selectedContent) {
        selectedContent.style.display = 'block';
    }

    // Add active class to selected tab
    const selectedTab = document.querySelector(`[data-tab="${tabName}"]`);
    if (selectedTab) {
        selectedTab.classList.add('active');
    }
}

// Initialize the form when modal opens
function initializeTaskRequirementsForm() {
    requirementCounter = 0;

    // Clear any existing requirements
    const container = document.getElementById('requirements-container');
    if (container) {
        container.innerHTML = '';
    }

    // Set basic info tab as active by default
    switchTab('basic');
}

// Call initialization when the modal is shown
document.addEventListener('DOMContentLoaded', function() {
    // Listen for modal show events
    const modal = document.getElementById('create-task-modal');
    if (modal) {
        modal.addEventListener('show', initializeTaskRequirementsForm);
    }
});
