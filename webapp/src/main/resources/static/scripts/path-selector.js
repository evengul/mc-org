/**
 * Path selector client-side logic
 * Handles path selection state in URL parameters
 */

/**
 * Clear all path selectors from URL and UI. Used before starting a new selection.
 */
window.clearAllPathSelectors = function() {
    const url = new URL(window.location);
    url.searchParams.delete('path');
    window.history.pushState({}, '', url);

    const elements = document.getElementsByClassName("project-resources-collection-found-paths");
    for (let i = 0; i < elements.length; i++) {
        elements[i].innerHTML = '';
    }
}

/**
 * Select a path node and update the UI
 * @param {string} gatheringId - Resource gathering ID
 * @param {string} itemId - Item ID being selected
 * @param {string} sourceType - Source type ID being selected
 */
window.selectPathNode = function(gatheringId, itemId, sourceType) {
    console.log('Path node selected:', {gatheringId, itemId, sourceType});

    // Build the path by selecting this source for the given item
    const currentPath = getPathFromURL();
    const updatedPath = selectSourceForItem(currentPath, itemId, sourceType);

    if (updatedPath) {
        const encodedPath = encodePath(updatedPath);
        console.log('Updated path:', encodedPath);

        // Update URL without page reload
        updateURLParameter('path', encodedPath);

        // Trigger HTMX to reload the path selector with the new path
        const url = window.location.pathname + `/resources/gathering/${gatheringId}/select-path?path=` + encodeURIComponent(encodedPath);
        htmx.ajax('GET', url, {
            target: '#path-selector-' + gatheringId,
            swap: 'outerHTML'
        });
    }
};

/**
 * Get current path from URL parameters
 * @returns {object|null} Decoded path object or null
 */
function getPathFromURL() {
    const urlParams = new URLSearchParams(window.location.search);
    const pathParam = urlParams.get('path');

    if (!pathParam) {
        return null;
    }

    return decodePath(pathParam);
}

/**
 * Update a URL parameter without page reload
 * @param {string} key - Parameter name
 * @param {string} value - Parameter value
 */
function updateURLParameter(key, value) {
    const url = new URL(window.location);
    url.searchParams.set(key, value);
    window.history.pushState({}, '', url);
}

/**
 * Select a source for a specific item in the path
 * @param {object|null} currentPath - Current path object
 * @param {string} targetItemId - Item to update
 * @param {string} sourceType - Source type to assign
 * @returns {object} Updated path object
 */
function selectSourceForItem(currentPath, targetItemId, sourceType) {
    // If no current path, create a new one
    if (!currentPath) {
        return {
            itemId: targetItemId,
            source: sourceType,
            requirements: []
        };
    }

    // If this is the target item, update it
    if (currentPath.itemId === targetItemId) {
        return {
            ...currentPath,
            source: sourceType
        };
    }

    // Check if the target item exists in any requirement
    const hasMatchingRequirement = currentPath.requirements.some(req =>
        itemExistsInPath(req, targetItemId)
    );

    if (hasMatchingRequirement) {
        // Recursively update requirements
        return {
            ...currentPath,
            requirements: currentPath.requirements.map(req =>
                selectSourceForItem(req, targetItemId, sourceType)
            )
        };
    }

    // If target item doesn't exist in current path tree, add it as a new requirement
    // This handles the case where we're selecting a source for a nested requirement
    // that hasn't been added to the path yet
    return {
        ...currentPath,
        requirements: [...currentPath.requirements, {
            itemId: targetItemId,
            source: sourceType,
            requirements: []
        }]
    };
}

/**
 * Check if an item exists anywhere in the path tree
 * @param {object} path - Path object to search
 * @param {string} itemId - Item ID to find
 * @returns {boolean} True if item exists in path
 */
function itemExistsInPath(path, itemId) {
    if (path.itemId === itemId) {
        return true;
    }
    return path.requirements.some(req => itemExistsInPath(req, itemId));
}

/**
 * Encode path object to URL-safe string
 * Format: item>source~req1>source|req2>source
 * @param {object} path - Path object to encode
 * @returns {string} Encoded path string
 */
function encodePath(path) {
    if (!path) {
        return '';
    }

    // Leaf item (no source)
    if (!path.source) {
        return path.itemId;
    }

    // Item with source
    let encoded = path.itemId + '>' + path.source;

    // Add requirements if any
    if (path.requirements && path.requirements.length > 0) {
        const reqsEncoded = path.requirements.map(req => encodePath(req)).join('|');
        encoded += '~' + reqsEncoded;
    }

    return encoded;
}

/**
 * Decode path string to path object
 * @param {string} encoded - Encoded path string
 * @returns {object|null} Decoded path object or null
 */
function decodePath(encoded) {
    if (!encoded || encoded.trim() === '') {
        return null;
    }

    try {
        return decodePathInternal(encoded);
    } catch (e) {
        console.error('Failed to decode path:', e);
        return null;
    }
}

/**
 * Internal decoder function
 * @param {string} encoded - Encoded path string
 * @returns {object} Decoded path object
 */
function decodePathInternal(encoded) {
    // Check if this is a leaf node (no source)
    if (!encoded.includes('>')) {
        return {
            itemId: encoded,
            source: null,
            requirements: []
        };
    }

    // Split on first ~ to separate item>source from requirements
    const parts = encoded.split('~', 2);
    const itemAndSource = parts[0].split('>', 2);

    const itemId = itemAndSource[0];
    const source = itemAndSource[1] || null;

    let requirements = [];
    if (parts.length > 1) {
        // Parse requirements (separated by |)
        requirements = parts[1].split('|').map(req => decodePathInternal(req));
    }

    return {
        itemId: itemId,
        source: source,
        requirements: requirements
    };
}

console.log('Path selector initialized');

