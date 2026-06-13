// Dismiss the "+ New project" dropdown on outside click or Escape.
// Progressive enhancement — the <details> menu opens/closes without it.
document.addEventListener('click', (event) => {
    document.querySelectorAll('details.np-menu[open]').forEach((menu) => {
        if (!menu.contains(event.target)) {
            menu.removeAttribute('open');
        }
    });
});

document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
        document.querySelectorAll('details.np-menu[open]').forEach((menu) => {
            menu.removeAttribute('open');
        });
    }
});
