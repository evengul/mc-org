#!/usr/bin/env node
// Signs in through the LOCAL demo sign-in and screenshots one page at one or more widths.
// For sessions without playwright-cli (cloud sessions); uses the preinstalled Playwright.
//
//   node webapp/scripts/screenshot.js <path> <out-prefix> [widths=375,1440] [port=8080]
//   node webapp/scripts/screenshot.js /worlds /tmp/shots/worlds
//     → /tmp/shots/worlds-375.png, /tmp/shots/worlds-1440.png
//
// Prints the final URL and title per width, so a redirect to sign-in or an error page shows
// up in the output and not only in the image. Exits non-zero on a 4xx/5xx page response.
const { chromium } = require('playwright');

(async () => {
    const [path, outPrefix, widthsArg = '375,1440', port = process.env.PORT || '8080'] = process.argv.slice(2);
    if (!path || !outPrefix) {
        console.error('usage: screenshot.js <path> <out-prefix> [widths] [port]');
        process.exit(1);
    }
    const base = `http://localhost:${port}`;
    const browser = await chromium.launch();
    let failed = false;
    try {
        const context = await browser.newContext();
        const signIn = await context.newPage();
        await signIn.goto(`${base}/auth/oidc/demo-redirect`, { waitUntil: 'networkidle' });
        await signIn.close();
        for (const width of widthsArg.split(',').map(Number)) {
            const page = await context.newPage();
            await page.setViewportSize({ width, height: 900 });
            const response = await page.goto(base + path, { waitUntil: 'networkidle' });
            const status = response ? response.status() : 0;
            const file = `${outPrefix}-${width}.png`;
            await page.screenshot({ path: file, fullPage: true });
            console.log(`${width}px  ${status}  ${page.url()}  "${await page.title()}"  → ${file}`);
            if (status >= 400) failed = true;
            await page.close();
        }
    } finally {
        await browser.close();
    }
    process.exit(failed ? 2 : 0);
})();
