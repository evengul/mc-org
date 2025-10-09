import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, TestDataGenerator, TestSetup } from '../utils';

test.describe('World Navigation', () => {
  let authHelpers: AuthHelpers;
  let worldHelpers: WorldHelpers;

  test.beforeEach(async ({ page }) => {
    authHelpers = new AuthHelpers(page);
    worldHelpers = new WorldHelpers(page);

    // Authenticate before each test
    await authHelpers.signInAsTestUser();
    await TestSetup.waitForPageLoad(page);
  });

  test('should navigate between different worlds', async ({ page }) => {
    // Create two test worlds
    const world1Name = TestDataGenerator.generateTestWorldName();
    const world2Name = TestDataGenerator.generateTestWorldName();

    await worldHelpers.createWorld({ name: world1Name });
    await worldHelpers.createWorld({ name: world2Name });

    // Navigate to first world
    await worldHelpers.navigateToWorld(world1Name);
    await expect(page.locator('body')).toContainText(world1Name);

    // Navigate to second world
    await worldHelpers.navigateToWorld(world2Name);
    await expect(page.locator('body')).toContainText(world2Name);

    // Navigate back to first world
    await worldHelpers.navigateToWorld(world1Name);
    await expect(page.locator('body')).toContainText(world1Name);
  });

  test('should display world information when navigating to world', async ({ page }) => {
    const worldData = {
      name: TestDataGenerator.generateTestWorldName(),
      description: 'Test world with detailed description for navigation testing',
      minecraftVersion: '1.20.0'
    };

    await worldHelpers.createWorld(worldData);
    await worldHelpers.navigateToWorld(worldData.name);

    // Verify world details are displayed
    await expect(page.locator('body')).toContainText(worldData.name);
    await expect(page.locator('body')).toContainText(worldData.description);
  });

  test('should handle navigation to non-existent world gracefully', async ({ page }) => {
    await page.goto("/app/worlds/999999")
    await expect(page.locator('body')).toContainText("World with ID 999999 does not exist");
  });

  test('should show world selection when multiple worlds exist', async ({ page }) => {
    // Create multiple worlds
    const worldNames = [
      TestDataGenerator.generateTestWorldName(),
      TestDataGenerator.generateTestWorldName(),
      TestDataGenerator.generateTestWorldName()
    ];

    for (const worldName of worldNames) {
      await worldHelpers.createWorld({ name: worldName });
    }

    // Navigate to home/world selection
    await page.goto('/app');
    await TestSetup.waitForPageLoad(page);

    // All worlds should be visible
    for (const worldName of worldNames) {
      await expect(page.locator('text=' + worldName)).toBeVisible();
    }
  });

  test('should remember user is owner of created worlds', async ({ page }) => {
    const worldName = TestDataGenerator.generateTestWorldName();

    await worldHelpers.createWorld({ name: worldName });
    await worldHelpers.navigateToWorld(worldName);

    // As owner, user should have access to world settings
    const settingsLink = page.getByRole("button").filter({ hasText: "Settings" });
    await expect(settingsLink).toBeVisible();

    await settingsLink.click();
    await expect(page.locator('body')).toContainText("World Settings");
  });
});
