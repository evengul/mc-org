/**
 * SearchableSelect - Client-side interactivity for searchable select component
 *
 * Features:
 * - Lazy rendering: Only shows results after user types minimum characters
 * - Client-side filtering: Fast search through large option lists
 * - Keyboard navigation: Arrow keys, Enter, Escape, Tab
 * - Result limiting: Prevents DOM bloat with thousands of options
 * - Accessibility: ARIA compliant, screen reader friendly
 * - Mobile responsive: Bottom sheet on mobile devices
 */
class SearchableSelect {
    constructor(container) {
        this.container = container;
        this.id = container.dataset.searchableSelect;
        this.trigger = container.querySelector('.searchable-select__trigger');
        this.dropdown = container.querySelector('.searchable-select__dropdown');
        this.search = container.querySelector('.searchable-select__search');
        this.optionsContainer = container.querySelector('.searchable-select__options');
        this.hiddenInput = container.querySelector(`#${this.id}-value`);
        this.clearButton = container.querySelector('.searchable-select__clear');

        // Configuration from data attributes
        this.minQueryLength = parseInt(container.dataset.minQueryLength || '2', 10);
        this.maxDisplayedResults = parseInt(container.dataset.maxDisplayedResults || '200', 10);

        // Messages
        this.minQueryMessage = this.optionsContainer.dataset.minQueryMessage || 'Type to search...';
        this.emptyMessage = this.optionsContainer.dataset.emptyMessage || 'No options found';
        this.tooManyMessage = this.optionsContainer.dataset.tooManyMessage || 'Showing {count} results. Refine your search for more.';

        // State
        this.isOpen = false;
        this.focusedIndex = -1;
        this.filteredOptions = [];
        this.allOptions = Array.from(this.optionsContainer.querySelectorAll('.searchable-select__option'));

        this.init();
    }

    init() {
        // Bind event listeners
        this.trigger.addEventListener('click', (e) => this.handleTriggerClick(e));
        this.search.addEventListener('input', (e) => this.handleSearch(e));
        this.optionsContainer.addEventListener('click', (e) => this.handleOptionClick(e));

        // Clear button listeners
        if (this.clearButton) {
            this.clearButton.addEventListener('click', (e) => this.handleClearClick(e));
            this.clearButton.addEventListener('keydown', (e) => this.handleClearKeydown(e));
        }

        // Keyboard navigation
        this.trigger.addEventListener('keydown', (e) => this.handleTriggerKeydown(e));
        this.search.addEventListener('keydown', (e) => this.handleSearchKeydown(e));

        // Close on outside click
        document.addEventListener('click', (e) => this.handleOutsideClick(e));

        // Close on escape key (global)
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.isOpen) {
                this.close();
                this.trigger.focus();
            }
        });
    }

    toggle() {
        if (this.isOpen) {
            this.close();
        } else {
            this.open();
        }
    }

    handleTriggerClick(event) {
        // Don't toggle dropdown if clear button was clicked
        if (event.target.closest('.searchable-select__clear')) {
            return;
        }
        this.toggle();
    }

    handleClearClick(event) {
        event.stopPropagation(); // Prevent trigger click
        this.clearSelection();
    }

    handleClearKeydown(event) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            event.stopPropagation();
            this.clearSelection();
        }
    }

    clearSelection() {
        // Remove selection from all options
        this.allOptions.forEach(opt => {
            opt.removeAttribute('data-selected');
            opt.setAttribute('aria-selected', 'false');
        });

        // Clear hidden input
        this.hiddenInput.value = '';

        // Reset trigger text to placeholder
        const triggerText = this.trigger.querySelector('.searchable-select__trigger-text');
        const placeholder = this.container.querySelector('.searchable-select__trigger-text--placeholder')?.textContent || 'Select an option...';

        // Get placeholder from component or use default
        triggerText.textContent = triggerText.dataset.placeholder || triggerText.textContent || placeholder;
        triggerText.classList.add('searchable-select__trigger-text--placeholder');

        // Hide clear button
        if (this.clearButton) {
            this.clearButton.style.display = 'none';
        }

        // Dispatch change event
        this.hiddenInput.dispatchEvent(new Event('change', { bubbles: true }));

        // Keep focus on trigger
        this.trigger.focus();
    }


    open() {
        if (this.container.dataset.disabled === 'true') return;

        this.isOpen = true;
        this.dropdown.removeAttribute('hidden');
        this.trigger.setAttribute('aria-expanded', 'true');

        // Focus search input
        setTimeout(() => {
            this.search.focus();
        }, 50);

        // Show initial state (min query message)
        this.showMinQueryMessage();
    }

    close() {
        this.isOpen = false;
        this.dropdown.setAttribute('hidden', '');
        this.trigger.setAttribute('aria-expanded', 'false');
        this.search.value = '';
        this.focusedIndex = -1;

        // Hide all options and messages
        this.hideAllOptions();
        this.hideAllMessages();
    }

    handleOutsideClick(event) {
        if (!this.container.contains(event.target) && this.isOpen) {
            this.close();
        }
    }

    handleSearch(event) {
        const query = event.target.value.trim();

        // Lazy rendering: Only search if query meets minimum length
        if (query.length < this.minQueryLength) {
            this.showMinQueryMessage();
            this.hideAllOptions();
            return;
        }

        this.filterOptions(query);
    }

    filterOptions(query) {
        const lowerQuery = query.toLowerCase();
        this.filteredOptions = [];

        // Hide all messages first
        this.hideAllMessages();

        // Filter options
        this.allOptions.forEach(option => {
            if (option.dataset.disabled === 'true') {
                option.dataset.hidden = 'true';
                return;
            }

            const searchTerms = option.dataset.searchTerms || option.textContent.toLowerCase();
            const matches = searchTerms.includes(lowerQuery);

            if (matches) {
                this.filteredOptions.push(option);
            } else {
                option.dataset.hidden = 'true';
            }
        });

        // Limit displayed results to prevent DOM bloat
        const displayCount = Math.min(this.filteredOptions.length, this.maxDisplayedResults);

        if (this.filteredOptions.length === 0) {
            // No results found
            this.hideAllOptions();
            this.showEmptyMessage();
        } else {
            // Show limited results
            this.filteredOptions.forEach((option, index) => {
                if (index < displayCount) {
                    option.removeAttribute('data-hidden');
                } else {
                    option.dataset.hidden = 'true';
                }
            });

            // Show "too many results" message if needed
            if (this.filteredOptions.length > this.maxDisplayedResults) {
                this.showTooManyResultsMessage(displayCount);
            }
        }

        // Reset focus index
        this.focusedIndex = -1;
    }

    hideAllOptions() {
        this.allOptions.forEach(option => {
            option.dataset.hidden = 'true';
            option.classList.remove('searchable-select__option--focused');
        });
    }

    hideAllMessages() {
        this.optionsContainer.querySelectorAll('.searchable-select__message').forEach(msg => {
            msg.dataset.hidden = 'true';
        });
    }

    showMinQueryMessage() {
        this.hideAllMessages();
        let messageDiv = this.optionsContainer.querySelector('.searchable-select__message--min-query');

        if (!messageDiv) {
            messageDiv = document.createElement('div');
            messageDiv.className = 'searchable-select__message searchable-select__message--min-query';
            messageDiv.textContent = this.minQueryMessage;
            this.optionsContainer.insertBefore(messageDiv, this.optionsContainer.firstChild);
        }

        messageDiv.removeAttribute('data-hidden');
    }

    showEmptyMessage() {
        this.hideAllMessages();
        let messageDiv = this.optionsContainer.querySelector('.searchable-select__message--empty');

        if (!messageDiv) {
            messageDiv = document.createElement('div');
            messageDiv.className = 'searchable-select__message searchable-select__message--empty';
            messageDiv.textContent = this.emptyMessage;
            this.optionsContainer.appendChild(messageDiv);
        }

        messageDiv.removeAttribute('data-hidden');
    }

    showTooManyResultsMessage(displayCount) {
        let messageDiv = this.optionsContainer.querySelector('.searchable-select__message--too-many');

        if (!messageDiv) {
            messageDiv = document.createElement('div');
            messageDiv.className = 'searchable-select__message searchable-select__message--too-many';
            this.optionsContainer.insertBefore(messageDiv, this.optionsContainer.firstChild);
        }

        messageDiv.textContent = this.tooManyMessage.replace('{count}', displayCount);
        messageDiv.removeAttribute('data-hidden');
    }

    handleOptionClick(event) {
        const option = event.target.closest('.searchable-select__option');
        if (!option || option.dataset.disabled === 'true' || option.dataset.hidden === 'true') {
            return;
        }

        this.selectOption(option);
    }

    selectOption(option) {
        // Remove previous selection
        this.allOptions.forEach(opt => {
            opt.removeAttribute('data-selected');
            opt.setAttribute('aria-selected', 'false');
        });

        // Set new selection
        option.dataset.selected = 'true';
        option.setAttribute('aria-selected', 'true');

        // Update hidden input
        this.hiddenInput.value = option.dataset.value;

        // Update trigger text
        const triggerText = this.trigger.querySelector('.searchable-select__trigger-text');

        // Store placeholder before changing text
        if (triggerText.classList.contains('searchable-select__trigger-text--placeholder')) {
            triggerText.dataset.placeholder = triggerText.textContent;
        }

        triggerText.textContent = option.textContent.trim();
        triggerText.classList.remove('searchable-select__trigger-text--placeholder');

        // Show clear button
        if (this.clearButton) {
            this.clearButton.style.display = 'inline-flex';
        }

        // Dispatch change event for form validation and listeners
        this.hiddenInput.dispatchEvent(new Event('change', { bubbles: true }));

        // Close dropdown
        this.close();

        // Return focus to trigger
        this.trigger.focus();
    }

    handleTriggerKeydown(event) {
        switch (event.key) {
            case 'Enter':
            case ' ':
            case 'ArrowDown':
                event.preventDefault();
                this.open();
                break;
            case 'ArrowUp':
                event.preventDefault();
                this.open();
                break;
        }
    }

    handleSearchKeydown(event) {
        switch (event.key) {
            case 'ArrowDown':
                event.preventDefault();
                this.navigateOptions(1);
                break;
            case 'ArrowUp':
                event.preventDefault();
                this.navigateOptions(-1);
                break;
            case 'Enter':
                event.preventDefault();
                if (this.focusedIndex >= 0) {
                    const visibleOptions = this.getVisibleOptions();
                    if (visibleOptions[this.focusedIndex]) {
                        this.selectOption(visibleOptions[this.focusedIndex]);
                    }
                }
                break;
            case 'Tab':
                // Allow tab to close and move to next field
                this.close();
                break;
        }
    }

    navigateOptions(direction) {
        const visibleOptions = this.getVisibleOptions();

        if (visibleOptions.length === 0) return;

        // Remove current focus
        if (this.focusedIndex >= 0 && visibleOptions[this.focusedIndex]) {
            visibleOptions[this.focusedIndex].classList.remove('searchable-select__option--focused');
        }

        // Update index
        this.focusedIndex += direction;

        // Wrap around
        if (this.focusedIndex < 0) {
            this.focusedIndex = visibleOptions.length - 1;
        } else if (this.focusedIndex >= visibleOptions.length) {
            this.focusedIndex = 0;
        }

        // Apply focus
        const focusedOption = visibleOptions[this.focusedIndex];
        if (focusedOption) {
            focusedOption.classList.add('searchable-select__option--focused');
            focusedOption.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        }
    }

    getVisibleOptions() {
        return this.allOptions.filter(option => {
            return option.dataset.hidden !== 'true' && option.dataset.disabled !== 'true';
        });
    }

    destroy() {
        // Cleanup event listeners
        this.trigger.removeEventListener('click', this.toggle);
        this.search.removeEventListener('input', this.handleSearch);
        this.optionsContainer.removeEventListener('click', this.handleOptionClick);
        this.trigger.removeEventListener('keydown', this.handleTriggerKeydown);
        this.search.removeEventListener('keydown', this.handleSearchKeydown);
        document.removeEventListener('click', this.handleOutsideClick);
    }
}

/**
 * Initialize all searchable select components on page load
 */
function initSearchableSelects(container = document) {
    container.querySelectorAll('[data-searchable-select]').forEach(element => {
        // Check if already initialized
        if (!element.searchableSelectInstance) {
            element.searchableSelectInstance = new SearchableSelect(element);
        }
    });
}

// Initialize on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => initSearchableSelects());
} else {
    initSearchableSelects();
}

// Re-initialize after HTMX content swap
document.addEventListener('htmx:afterSwap', (event) => {
    initSearchableSelects(event.detail.target);
});

// Also handle HTMX settle event for dynamic content
document.addEventListener('htmx:afterSettle', (event) => {
    initSearchableSelects(event.detail.target);
});

