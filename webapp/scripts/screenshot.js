#!/usr/bin/env node
// Signs in through the LOCAL demo sign-in and screenshots one page at one or more widths.
// For sessions without playwright-cli (cloud sessions); uses the preinstalled Playwright.
//
//   node webapp/scripts/screenshot.js <path> <out-prefix> [widths=375,1440] [port=8080]
//   node webapp/scripts/screenshot.js /worlds /tmp/shots/worlds
//     → /tmp/shots/worlds-375.png, /tmp/shots/worlds-1440.png
//
// Prints the final URL and title per width, so a redirect to sign-in or an error page shows
// up in the output and not only in the image. Exit 2: a 4xx/5xx page or a page that bounced
// to sign-in. Exit 3: the demo sign-in itself failed.
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
        // A failed demo sign-in lands back on a sign-in page with a 200; every screenshot after
        // it would be of the wrong page and still "succeed".
        if (new URL(signIn.url()).pathname.startsWith('/auth') || signIn.url() === `${base}/`) {
            console.error(`demo sign-in did not sign in (ended on ${signIn.url()}); is DEMO_USER set in local.env?`);
            process.exitCode = 3;
            return;
        }
        await signIn.close();
        const widths = widthsArg.split(',').map(Number).filter((w) => Number.isInteger(w) && w > 0);
        for (const width of widths) {
            const page = await context.newPage();
            await page.setViewportSize({ width, height: 900 });
            const response = await page.goto(base + path, { waitUntil: 'networkidle' });
            const status = response ? response.status() : 0;
            const file = `${outPrefix}-${width}.png`;
            await page.screenshot({ path: file, fullPage: true });
            console.log(`${width}px  ${status}  ${page.url()}  "${await page.title()}"  → ${file}`);
            if (status >= 400) failed = true;
            if (new URL(page.url()).pathname.startsWith('/auth')) {
                console.error(`  redirected to sign-in: the session was lost`);
                failed = true;
            }
            await page.close();
        }
    } finally {
        await browser.close();
    }
    if (failed) process.exitCode = 2;
})();
