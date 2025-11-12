# Searchable Select Component - Clear Selection Feature

## Overview
The searchable select component now includes a clear button (×) that appears when a value is selected, allowing users to easily remove their selection.

## Usage

### Basic Example
```kotlin
searchableSelect<String>(
    id = "item-select",
    name = "itemId",
    options = items.map { SearchableSelectOption(it.id, it.name) }
) {
    placeholder = "Select an item..."
    required = false // Set to true if field must have a value
}
```

### Features

1. **Auto-show Clear Button**: The × button automatically appears when a value is selected
2. **Auto-hide Clear Button**: The button is hidden when:
   - No value is selected (showing placeholder)
   - The component is disabled
   - User clears the selection

3. **Keyboard Accessible**: 
   - Tab to the clear button
   - Press Enter or Space to clear the selection

4. **Event Handling**:
   - Clicking the clear button does NOT open the dropdown
   - Dispatches a 'change' event when selection is cleared
   - Maintains focus on the trigger after clearing

## Visual Behavior

```
┌────────────────────────────────────┐
│ Select an item...            ▼    │  ← No selection (no × button)
└────────────────────────────────────┘

┌────────────────────────────────────┐
│ Diamond Pickaxe       ×       ▼    │  ← Has selection (× button visible)
└────────────────────────────────────┘
   Click × to clear ──┘
```

## CSS Classes

- `.searchable-select__clear` - The clear button element
- Hover state: Changes background and color
- Focus state: Shows outline for accessibility
- Hidden when disabled or no selection

## JavaScript API

The clear functionality is built into the `SearchableSelect` class:

```javascript
// Programmatically clear selection
searchableSelectInstance.clearSelection();

// Check if clear button exists
if (searchableSelectInstance.clearButton) {
    // Clear button is available
}
```

## Accessibility

- **ARIA Label**: "Clear selection"
- **Role**: button
- **Tabindex**: 0 (keyboard accessible)
- **Keyboard Support**: Enter and Space keys
- **Focus Management**: Returns focus to trigger after clearing

## Integration with Forms

When the clear button is clicked:
1. The hidden input value is set to empty string ("")
2. A 'change' event is dispatched
3. Form validation is triggered if the field is required
4. The trigger text returns to the placeholder state

## Example in Context

```kotlin
div {
    label {
        htmlFor = "minecraft-item"
        +"Select Item"
        if (required) span("required-indicator") { +"*" }
    }
    
    searchableSelect<String>(
        id = "minecraft-item",
        name = "itemId",
        options = minecraftItems.map { item ->
            SearchableSelectOption(
                value = item.id,
                label = item.displayName,
                searchTerms = listOf(item.displayName, item.id, item.category)
            )
        }
    ) {
        placeholder = "Search for an item..."
        minQueryLength = 2
        maxDisplayedResults = 200
        required = true
    }
}
```

## Notes

- The clear button does not appear in disabled state
- The button is styled to match the overall component theme
- Clicking the clear button prevents the dropdown from opening
- The placeholder text is automatically restored when cleared

