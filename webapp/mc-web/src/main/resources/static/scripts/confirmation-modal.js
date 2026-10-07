// Global confirmation modal system for delete operations
// Supports type-to-confirm and simple yes/no patterns

const MODAL_ID = 'confirm-delete-modal';
let currentDeleteConfig = null;

// htmx waits on a prevented confirm until issueRequest() or dropRequest() is called, and the
// element's request queue waits with it: a dialog closed without either leaves that button dead
// until reload. So every way the dialog closes settles the request (see the 'close' listener).
window.addEventListener('htmx:confirm', function(event) {
  const { ctx, issueRequest, dropRequest } = event.detail;
  const deleteButton = ctx.sourceElement;

  if (deleteButton && deleteButton.hasAttribute('data-hx-delete-confirm')) {
    event.preventDefault();

    const config = {
      title: deleteButton.attributes["data-hx-delete-confirm-title"]?.value,
      description: deleteButton.attributes["data-hx-delete-confirm-description"]?.value,
      warning: deleteButton.attributes["data-hx-delete-confirm-warning"]?.value,
      confirmText: deleteButton.attributes["data-hx-delete-confirm-text"]?.value,
      issueRequest,
      dropRequest
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
 * @param {function} config.issueRequest - Sends the delete
 * @param {function} config.dropRequest - Abandons it
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

    input.value = '';

    // The 'close' listener below drops a request still waiting on this dialog.
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

    // Cleared before closing, so the 'close' listener does not drop what was just issued.
    const config = currentDeleteConfig;
    currentDeleteConfig = null;
    config.issueRequest();

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

// Cancel, Escape, the × and a backdrop click all close the dialog, the last two without going
// through closeDeleteConfirmModal. A dialog's 'close' event does not bubble, hence the capture.
document.addEventListener('close', function(e) {
    if (e.target.id !== MODAL_ID || !currentDeleteConfig) return;
    const config = currentDeleteConfig;
    currentDeleteConfig = null;
    config.dropRequest();
}, true);

