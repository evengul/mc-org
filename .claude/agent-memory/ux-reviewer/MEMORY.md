# UX Reviewer Agent Memory

## Application Overview
- MC-ORG: Minecraft World Collaboration Platform
- Server-side rendered (Kotlin HTML DSL + HTMX)
- Three Minecraft-themed themes: Overworld (light), Nether (dark/red), End (dark/purple)
- Runs on localhost:8080, redirects unauthenticated users to /auth/sign-in

## Design System
- CSS custom properties for theming in `/webapp/src/main/resources/static/styles/root.css`
- Font: Roboto Mono (monospace), base size 14px, scale ratio 1.1
- Spacing: baseline 1.75em, xxs/xs/sm/md/lg/xl variants
- Button variants: btn--action, btn--neutral, btn--danger, btn--ghost + sizes btn--sm/btn--lg
- Semantic color tokens: --clr-action, --clr-neutral, --clr-danger, --clr-success, etc.
- Shadows: xs/sm/md/lg/xl variants

## Page Architecture
- TopBar: sticky header with MC-ORG branding, nav links, theme toggle, notifications, profile
- Main content wrapped in `<main>` tag with `padding: var(--spacing-md)`
- Alert container: empty `<ul id="alert-container">` always rendered (causes empty list in a11y tree)
- Confirm delete modal always rendered in body

## Known Issues (Landing Page - reviewed 2026-02-05)
- Microsoft sign-in button has `all: unset` on hover/focus, breaking focus indicators and styles
- Button text color hardcoded to #5E5E5E (gray) -- poor contrast against action bg on all themes
- Feature cards use 3-column grid with no responsive breakpoint -- breaks badly on mobile
- No favicon (404 console error)
- Dual h1 elements: one in topbar, one in main content (a11y concern)
- Landing page uses `height: calc(100vh - var(--navbar-height))` which pushes feature cards off-viewport on smaller screens
- Card description text uses opacity: 0.8 which compounds with light color to create contrast issues
- Navbar responsive media query uses `var(--media-lg)` in `@media` which CSS doesn't support (vars in media queries)

## Patterns Observed
- Links wrapping buttons (sign-in) create nested interactive elements in a11y tree
- Theme toggle icon doesn't change to indicate current theme
- Feature cards use `<p class="card-title">` instead of proper heading elements

## File Locations
- Landing page template: `/webapp/src/main/kotlin/app/mcorg/presentation/templated/landing/LandingPage.kt`
- Landing page CSS: `/webapp/src/main/resources/static/styles/pages/landing-page.css`
- Root CSS variables: `/webapp/src/main/resources/static/styles/root.css`
- TopBar component: `/webapp/src/main/kotlin/app/mcorg/presentation/templated/layout/topbar/TopBar.kt`
- Navbar CSS: `/webapp/src/main/resources/static/styles/components/navbar.css`
- Button CSS: `/webapp/src/main/resources/static/styles/components/button.css` + `button-states.css`
- Main content CSS: `/webapp/src/main/resources/static/styles/components/main.css`
