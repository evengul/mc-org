// Global confirmation modal system for delete operations
// Supports type-to-confirm and simple yes/no patterns

const MODAL_ID = 'confirm-delete-modal';
let currentDeleteConfig = null;

window.addEventListener('htmx:confirm', function(event) {
  const { elt: deleteButton, issueRequest: handleDelete } = event.detail;

  if (deleteButton && deleteButton.hasAttribute('data-hx-delete-confirm')) {
    event.preventDefault();
    console.log(handleDelete);

    const config = {
      title: deleteButton.attributes["data-hx-delete-confirm-title"]?.value,
      description: deleteButton.attributes["data-hx-delete-confirm-description"]?.value,
      warning: deleteButton.attributes["data-hx-delete-confirm-warning"]?.value,
      confirmText: deleteButton.attributes["data-hx-delete-confirm-text"]?.value,
      handleDelete
    }

    showDeleteConfirmModal(config)
  }
})

/**
 * Show confirmation modal for delete operations
 * @param {Object} config - Configuration object
 * @param {string} config.title - Modal title
 * @param {string} config.description - Modal description
 * @param {string} config.warning - Warning message
 * @param {string} [config.confirmText] - Text user must type to confirm (optional, enables type-to-confirm mode)
 * @param {function} config.handleDelete - Function to call to proceed with deletion
 */
showDeleteConfirmModal = function(config) {
    currentDeleteConfig = config;

    const modal = document.getElementById(MODAL_ID);
    const title = document.getElementById(`${MODAL_ID}-title`);
    const description = document.getElementById(`${MODAL_ID}-description`);
    const warning = document.getElementById(`${MODAL_ID}-warning`);
    const inputContainer = document.getElementById(`${MODAL_ID}-input-container`);
    const inputLabel = document.getElementById(`${MODAL_ID}-input-label`);
    const input = document.getElementById(`${MODAL_ID}-input`);
    const confirmBtn = document.getElementById(`${MODAL_ID}-confirm-btn`);
    const validationError = document.getElementById(`${MODAL_ID}-validation-error`);

    // Populate modal content
    title.textContent = config.title;
    if (config.description) {
      description.textContent = config.description;
      description.style.display = 'block';
    } else {
      description.style.display = 'none';
    }
    if (config.warning) {
      warning.textContent = config.warning;
      warning.style.display = 'block';
    } else {
      warning.style.display = 'none';
    }

    // Reset input
    input.value = '';
    validationError.style.display = 'none';

    // Check if type-to-confirm mode
    if (config.confirmText) {
        // Type-to-confirm mode
        inputContainer.style.display = 'block';
        inputLabel.textContent = `Type "${config.confirmText}" to confirm`;
        confirmBtn.disabled = true;
        input.focus();
    } else {
        // Simple confirm mode
        inputContainer.style.display = 'none';
        confirmBtn.disabled = false;
    }

    modal.showModal();
};

/**
 * Close the confirmation modal
 */
window.closeDeleteConfirmModal = function() {
    const modal = document.getElementById(MODAL_ID);
    const input = document.getElementById(`${MODAL_ID}-input`);

    // Reset state
    input.value = '';
    currentDeleteConfig = null;

    // Close modal
    modal.close();
};

/**
 * Validate type-to-confirm input
 */
window.validateDeleteConfirmation = function() {
    if (!currentDeleteConfig || !currentDeleteConfig.confirmText) {
        return;
    }

    const input = document.getElementById(`${MODAL_ID}-input`);
    const confirmBtn = document.getElementById(`${MODAL_ID}-confirm-btn`);
    const validationError = document.getElementById(`${MODAL_ID}-validation-error`);

    const isMatch = input.value === currentDeleteConfig.confirmText;

    confirmBtn.disabled = !isMatch;

    // Show validation error if user has typed something but it doesn't match
    if (input.value.length > 0 && !isMatch) {
        validationError.style.display = 'block';
    } else {
        validationError.style.display = 'none';
    }
};

/**
 * Execute the delete operation via HTMX
 */
window.executeDeleteConfirmation = function() {
    if (!currentDeleteConfig) {
        return;
    }

    currentDeleteConfig.handleDelete(true);

    // Close modal after triggering
    window.closeDeleteConfirmModal();
};

// Close modal on Escape key
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        const modal = document.getElementById(MODAL_ID);
        if (modal && (modal.hasAttribute('open') || modal.open)) {
            window.closeDeleteConfirmModal();
        }
    }
});

// Close modal when delete completes successfully
document.addEventListener('htmx:afterOnLoad', function(e) {
    // If the delete was successful, ensure modal is closed
    if (currentDeleteConfig && e.detail.successful) {
        window.closeDeleteConfirmModal();
    }
});

