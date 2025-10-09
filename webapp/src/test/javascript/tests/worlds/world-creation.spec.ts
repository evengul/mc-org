import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, TestDataGenerator, TestSetup } from '../utils';

test.describe('World Creation', () => {
  let authHelpers: AuthHelpers;
  let worldHelpers: WorldHelpers;

  test.beforeEach(async ({ page }) => {
    authHelpers = new AuthHelpers(page);
    worldHelpers = new WorldHelpers(page);

    // Authenticate before each test
    await authHelpers.signInAsTestUser();
    await TestSetup.waitForPageLoad(page);
  });

  test('should create a new world with basic information', async ({ page }) => {
    const worldData = {
      name: TestDataGenerator.generateTestWorldName(),
      description: 'A test world for basic functionality testing',
      minecraftVersion: '1.20.0'
    };

    const createdWorld = await worldHelpers.createWorld(worldData);

    // Verify world was created with correct data
    expect(createdWorld.name).toBe(worldData.name);
    expect(createdWorld.description).toBe(worldData.description);
    expect(createdWorld.version).toBe(worldData.minecraftVersion);

    // Verify world appears in world list
    await worldHelpers.verifyWorldExists(worldData.name);
  });

  test('should create world with minimal required information', async ({ page }) => {
    // Create world with only name (other fields should have defaults)
    const worldName = TestDataGenerator.generateTestWorldName();
    const createdWorld = await worldHelpers.createWorld({ name: worldName });

    expect(createdWorld.name).toBe(worldName);
    expect(createdWorld.description).toBeDefined();
    expect(createdWorld.version).toBeDefined();

    await worldHelpers.verifyWorldExists(worldName);
  });

  test('should create multiple worlds for same user', async ({ page }) => {
    const world1Name = TestDataGenerator.generateTestWorldName();
    const world2Name = TestDataGenerator.generateTestWorldName();

    // Create first world
    await worldHelpers.createWorld({ name: world1Name });
    await worldHelpers.verifyWorldExists(world1Name);

    // Create second world
    await worldHelpers.createWorld({ name: world2Name });
    await worldHelpers.verifyWorldExists(world2Name);

    // Both worlds should be visible
    const availableWorlds = await worldHelpers.getAvailableWorlds();
    expect(availableWorlds).toContain(world1Name);
    expect(availableWorlds).toContain(world2Name);
  });

  // Skipped because the app currently only supports a fixed set of versions (1.20.0)
  // Remove .skip when version selection is fully supported
  test.skip('should handle different Minecraft versions', async ({ page }) => {
    const versions = ['1.19.4', '1.20.1', '1.20.4'];

    for (const version of versions) {
      const worldName = `${TestDataGenerator.generateTestWorldName()} (${version})`;
      const createdWorld = await worldHelpers.createWorld({
        name: worldName,
        minecraftVersion: version
      });

      expect(createdWorld.version).toBe(version);
      await worldHelpers.verifyWorldExists(worldName);
    }
  });
});
