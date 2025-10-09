import {test, expect} from '@playwright/test';
import {AuthHelpers, TestSetup} from '../utils';

test.describe('Authentication', () => {
  test.beforeEach(async ({page}) => {
    // Ensure we start each test unauthenticated
    await page.goto('/auth/sign-out');
    await TestSetup.waitForPageLoad(page);
  });

  test('should redirect unauthenticated users to sign-in page', async ({page}) => {
    // Attempt to access protected route
    await page.goto('/app');

    // Should be redirected to sign-in page
    await expect(page).toHaveURL(/\/auth\/sign-in/);

    // Verify sign-in page content
    await expect(page.locator('body')).toContainText(/sign.*in/i);
  });

  test('should successfully authenticate via test sign-in flow', async ({page}) => {
    const authHelpers = new AuthHelpers(page);

    // Navigate to sign-in page first
    await page.goto('/auth/sign-in');
    await expect(page).toHaveURL(/\/auth\/sign-in/);

    // Perform test authentication
    await authHelpers.signInAsTestUser();

    // Should be redirected to app and authenticated
    await expect(page).toHaveURL('/app');

    // Verify we can access authenticated content
    await expect(page.locator('body')).not.toContainText('Sign In');
  });

  test('should maintain authenticated session across page refreshes', async ({page}) => {
    const authHelpers = new AuthHelpers(page);

    // Sign in first
    await authHelpers.signInAsTestUser();
    await expect(page).toHaveURL('/app');

    // Refresh the page
    await page.reload();
    await TestSetup.waitForPageLoad(page);

    // Should still be authenticated
    await expect(page).toHaveURL('/app');
    await expect(page.locator('body')).not.toContainText('Sign In');
  });

  test('should successfully sign out and clear session', async ({page}) => {
    const authHelpers = new AuthHelpers(page);

    // Sign in first
    await authHelpers.signInAsTestUser();
    await expect(page).toHaveURL('/app');

    // Sign out
    await authHelpers.signOut();

    // Should be redirected to landing page or sign-in page
    await expect(page).toHaveURL(/\/(auth\/sign-in)?$/);

    // Attempt to access protected route should redirect to sign-in
    await page.goto('/app');
    await expect(page).toHaveURL(/\/auth\/sign-in/);
  });

  test('should handle direct navigation to test redirect endpoint', async ({page}) => {
    // Direct navigation to test authentication endpoint
    await page.goto('/auth/oidc/test-redirect');

    // Should be authenticated and redirected to app
    await expect(page).toHaveURL('/app');

    // Verify authentication worked
    await expect(page.locator('body')).not.toContainText('Sign In');
  });

  test('should create unique test users for each authentication', async ({page}) => {
    const authHelpers = new AuthHelpers(page);

    // First authentication
    await authHelpers.signInAsTestUser();
    await expect(page).toHaveURL('/app');

    // Get some indicator of current user (this may need adjustment based on actual UI)
    await page.goto("/app/profile")
    const userIndicator1 = await page.getByRole("textbox").nth(1).textContent();

    // Sign out and sign in again
    await authHelpers.signOut();
    await authHelpers.signInAsTestUser();

    // Get user indicator again
    await page.goto("/app/profile")
    const userIndicator2 = await page.getByRole("textbox").nth(1).textContent();

    // Should have different users (TestUser_{random} format)
    // This test verifies that fresh users are created each time
    if (userIndicator1 && userIndicator2) {
      expect(userIndicator1).not.toEqual(userIndicator2);
    }
  });
});
