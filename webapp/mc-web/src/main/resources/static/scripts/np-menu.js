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

// Arriving from the Worlds page's "New project" nudge (/worlds/N/projects#new):
// open the door menu immediately, so the link lands on the choice rather than on the
// list with the choice still one click away.
document.addEventListener('DOMContentLoaded', () => {
    if (window.location.hash !== '#new') return;
    const menu = document.getElementById('new-project-menu');
    if (!menu) return;
    menu.setAttribute('open', '');
    menu.scrollIntoView({ block: 'center' });
});
