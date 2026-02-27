import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, TestDataGenerator, TestSetup } from '../utils';

test.describe('World Deletion', () => {
  let authHelpers: AuthHelpers;
  let worldHelpers: WorldHelpers;

  test.beforeEach(async ({ page }) => {
    authHelpers = new AuthHelpers(page);
    worldHelpers = new WorldHelpers(page);

    // Authenticate before each test
    await authHelpers.signInAsTestUser();
    await TestSetup.waitForPageLoad(page);
  });

  test('should delete a world successfully', async ({ page }) => {
    const worldName = TestDataGenerator.generateTestWorldName();

    // Create world first
    await worldHelpers.createWorld({ name: worldName });
    await worldHelpers.verifyWorldExists(worldName);

    // Delete the world
    await worldHelpers.deleteWorld(worldName);

    // Navigate back to world list and verify world is gone
    await page.goto('/app');
    await TestSetup.waitForPageLoad(page);

    // World should no longer be visible
    await expect(page.locator('text=' + worldName)).not.toBeVisible();
  });

  test('should require confirmation for world deletion', async ({ page }) => {
    const worldName = TestDataGenerator.generateTestWorldName();

    await worldHelpers.createWorld({ name: worldName });
    await worldHelpers.navigateToWorld(worldName);

    // Navigate to world settings
    const settingsLink = page.getByRole("button").filter({ hasText: "Settings" });
    await settingsLink.click();

    let confirmed = false;

    page.on("dialog", async (dialog) => {
      confirmed = true;
      await dialog.accept();
    });

    // Find and click delete button
    const deleteButton = page.locator('button').filter({ hasText: /delete.*world/i });
    await deleteButton.waitFor({ state: 'visible' });
    await deleteButton.click();

    await page.waitForLoadState('networkidle');

    expect(confirmed).toBe(true);
  });

  test('should handle deletion of world with multiple projects', async ({ page }) => {
    const worldName = TestDataGenerator.generateTestWorldName();

    // Create world
    await worldHelpers.createWorld({ name: worldName });
    await worldHelpers.navigateToWorld(worldName);

    // Create a test project in this world (if possible from current page)
    const createProjectButton = page.locator('button, a').filter({ hasText: "New Project" });
    if (await createProjectButton.count() > 0) {
      await createProjectButton.click();
      await page.fill('input[name="name"]', 'Test Project for Deletion');
      const submitButton = page.locator('button[type="submit"], button').filter({ hasText: /create|save|submit/i });
      await submitButton.click();
      await TestSetup.waitForPageLoad(page);
    }

    // Now delete the world
    await worldHelpers.deleteWorld(worldName);

    // Verify world and all its contents are gone
    await page.goto('/app');
    await TestSetup.waitForPageLoad(page);
    await expect(page.locator('text=' + worldName)).not.toBeVisible();
  });

  test('should handle edge case of deleting last world', async ({ page }) => {
    const worldName = TestDataGenerator.generateTestWorldName();

    // Create a single world
    await worldHelpers.createWorld({ name: worldName });

    // Get count of current worlds
    const initialWorlds = await worldHelpers.getAvailableWorlds();

    // Delete the world
    await worldHelpers.deleteWorld(worldName);

    // Navigate to app page
    await page.goto('/app');
    await TestSetup.waitForPageLoad(page);

    // Check what happens when user has no worlds
    // This could redirect to world creation, show empty state, etc.
    const finalWorlds = await worldHelpers.getAvailableWorlds();
    expect(finalWorlds.length).toBeLessThan(initialWorlds.length);

    // Should either show world creation prompt or empty state
    const hasCreateWorldOption = await page.locator('button, a').filter({ hasText: /create.*world/i }).count() > 0;
    const hasEmptyState = await page.locator('body').filter({ hasText: /no.*world|empty|create.*first/i }).count() > 0;

    expect(hasCreateWorldOption || hasEmptyState).toBe(true);
  });

  test.skip('should only allow world owner to delete world', async ({ page }) => {
    const worldName = TestDataGenerator.generateTestWorldName();

    // Create world (user becomes owner)
    await worldHelpers.createWorld({ name: worldName });
    await worldHelpers.navigateToWorld(worldName);

    // Navigate to settings
    const settingsLink = page.locator('a, button').filter({ hasText: /settings/i });
    await settingsLink.click();

    // As owner, delete button should be visible
    const deleteButton = page.locator('button').filter({ hasText: /delete.*world/i });
    await expect(deleteButton).toBeVisible();

    // Note: Testing with different user roles would require
    // multi-user test setup which we don't have yet
  });

  test('should maintain other worlds when deleting one world', async ({ page }) => {
    // Create multiple worlds
    const world1Name = TestDataGenerator.generateTestWorldName();
    const world2Name = TestDataGenerator.generateTestWorldName();
    const world3Name = TestDataGenerator.generateTestWorldName();

    await worldHelpers.createWorld({ name: world1Name });
    await worldHelpers.createWorld({ name: world2Name });
    await worldHelpers.createWorld({ name: world3Name });

    // Verify all worlds exist
    await worldHelpers.verifyWorldExists(world1Name);
    await worldHelpers.verifyWorldExists(world2Name);
    await worldHelpers.verifyWorldExists(world3Name);

    // Delete middle world
    await worldHelpers.deleteWorld(world2Name);

    // Verify other worlds still exist
    await page.goto('/app');
    await TestSetup.waitForPageLoad(page);

    await expect(page.locator('text=' + world1Name)).toBeVisible();
    await expect(page.locator('text=' + world2Name)).not.toBeVisible();
    await expect(page.locator('text=' + world3Name)).toBeVisible();
  });
});
