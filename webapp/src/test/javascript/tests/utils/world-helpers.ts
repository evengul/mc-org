/**
 * World management utilities for Playwright tests
 */
import { Page } from '@playwright/test';

export class WorldHelpers {
  constructor(private page: Page) {}

  /**
   * Create a new world with unique name to avoid conflicts
   */
  async createWorld(options: {
    name?: string;
    description?: string;
    minecraftVersion?: string;
  } = {}): Promise<{ name: string; description: string; version: string }> {
    const timestamp = Date.now();
    const worldData = {
      name: options.name || `Test World ${timestamp}`,
      description: options.description || `Test world created at ${new Date().toISOString()}`,
      version: options.minecraftVersion || '1.20.0'
    };

    // Navigate to worlds page or home page where world creation is available
    await this.page.goto('/app');

    // Look for the main "Create World" button that opens the modal (not the submit button)
    const createWorldButton = this.page.locator('button.create-world-button, button').filter({ hasText: /create.*world/i }).first();
    await createWorldButton.waitFor({ state: 'visible' });
    await createWorldButton.click();

    // Wait for modal to appear
    await this.page.waitForSelector('#create-world-modal, .modal', { state: 'visible' });

    // Fill out world creation form
    await this.page.fill('input[name="name"], input[placeholder*="name" i]', worldData.name);
    await this.page.fill('textarea[name="description"], textarea[placeholder*="description" i]', worldData.description);

    // Handle Minecraft version
    const versionInput = this.page.locator('select[name="version"]');
    await versionInput.selectOption(worldData.version);

    // Submit the form (this should be the submit button inside the modal)
    const submitButton = this.page.locator('#create-world-form button[type="submit"], .modal button[type="submit"], button.modal-submit-button');
    await submitButton.waitFor({ state: 'visible' });
    await submitButton.click();

    // Wait for successful creation (should redirect or show success message)
    await this.page.waitForLoadState('networkidle');

    return worldData;
  }

  /**
   * Navigate to a specific world by name
   */
  async navigateToWorld(worldName: string): Promise<void> {
    await this.page.goto('/app');

    // Look for world selection area
    const worldLink = this.page.getByRole("listitem").filter({ hasText: worldName }).getByRole("button").filter({ hasText: "View World" })
    await worldLink.waitFor({ state: 'visible' });
    await worldLink.click();

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Delete a world (requires admin/owner permissions)
   */
  async deleteWorld(worldName: string): Promise<void> {
    // Accept all prompts
    this.page.on('dialog', async dialog => {
      await dialog.accept();
    });
    await this.navigateToWorld(worldName);

    // Navigate to world settings
    const settingsLink = this.page.getByRole("button").filter({ hasText: "Settings" });
    await settingsLink.click();

    // Find and click delete button
    const deleteButton = this.page.locator('button').filter({ hasText: /delete.*world/i });
    await deleteButton.waitFor({ state: 'visible' });
    await deleteButton.click();

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Verify world exists in world list
   */
  async verifyWorldExists(worldName: string): Promise<void> {
    await this.page.goto('/app');
    await this.page.waitForSelector(`text=${worldName}`, { state: 'visible' });
  }

  /**
   * Get list of available worlds
   */
  async getAvailableWorlds(): Promise<string[]> {
    await this.page.goto('/app');

    const worldElements = this.page.locator('.home-world-item-header h2');
    const count = await worldElements.count();
    const worlds: string[] = [];

    for (let i = 0; i < count; i++) {
      const worldName = await worldElements.nth(i).textContent();
      if (worldName) {
        worlds.push(worldName.trim());
      }
    }

    return worlds;
  }
}
