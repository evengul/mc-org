/**
 * Theme Switcher for MC-ORG
 *
 * Manages three Minecraft-inspired themes:
 * - overworld: Light theme with grass greens and sky blues
 * - nether: Dark theme with crimson reds, gold oranges, and lava
 * - end: Dark theme with purples, blacks, and endstone yellows
 *
 * Theme preference is stored in localStorage and defaults to system preference.
 */

const THEMES = {
    OVERWORLD: 'overworld',
    NETHER: 'nether',
    END: 'end'
};

const THEME_STORAGE_KEY = 'mc-org-theme';

/**
 * Gets the current theme from localStorage or system preference
 */
function getInitialTheme() {
    // Check localStorage first
    const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    if (savedTheme && Object.values(THEMES).includes(savedTheme)) {
        return savedTheme;
    }

    // Fall back to system preference
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        return THEMES.END; // Dark mode -> End theme
    }

    return THEMES.OVERWORLD; // Light mode -> Overworld theme (default)
}

/**
 * Applies the theme to the document
 */
function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
}

/**
 * Saves the theme preference to localStorage
 */
function saveTheme(theme) {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
}

/**
 * Switches to the specified theme and saves the preference
 */
function switchTheme(theme) {
    if (!Object.values(THEMES).includes(theme)) {
        console.error('Invalid theme:', theme);
        return;
    }

    applyTheme(theme);
    saveTheme(theme);
}

/**
 * Cycles to the next theme in the sequence: Overworld -> Nether -> End -> Overworld
 */
function cycleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme') || THEMES.OVERWORLD;

    let nextTheme;
    switch (currentTheme) {
        case THEMES.OVERWORLD:
            nextTheme = THEMES.NETHER;
            break;
        case THEMES.NETHER:
            nextTheme = THEMES.END;
            break;
        case THEMES.END:
            nextTheme = THEMES.OVERWORLD;
            break;
        default:
            nextTheme = THEMES.OVERWORLD;
    }

    switchTheme(nextTheme);
}

/**
 * Gets the current theme
 */
function getCurrentTheme() {
    return document.documentElement.getAttribute('data-theme') || THEMES.OVERWORLD;
}

// Initialize theme immediately on page load (before DOM ready)
(function initializeTheme() {
    const theme = getInitialTheme();
    applyTheme(theme);
})();

// Listen for system theme changes if no manual preference is set
if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        // Only auto-switch if user hasn't manually selected a theme
        const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
        if (!savedTheme) {
            const newTheme = e.matches ? THEMES.END : THEMES.OVERWORLD;
            applyTheme(newTheme);
        }
    });
}

// Export functions for use in other scripts
window.mcorgTheme = {
    switch: switchTheme,
    cycle: cycleTheme,
    getCurrent: getCurrentTheme,
    themes: THEMES
};

