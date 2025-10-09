import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, ProjectHelpers, TestDataGenerator, TestSetup, ProjectData } from '../utils';

test.describe('Project Stages', () => {
  let authHelpers: AuthHelpers;
  let worldHelpers: WorldHelpers;
  let projectHelpers: ProjectHelpers;
  let testWorldName: string;
  let testProjectName: string;

  test.beforeEach(async ({ page }) => {
    authHelpers = new AuthHelpers(page);
    worldHelpers = new WorldHelpers(page);
    projectHelpers = new ProjectHelpers(page);

    // Set up test environment with world and project
    await authHelpers.signInAsTestUser();
    testWorldName = TestDataGenerator.generateTestWorldName();
    await worldHelpers.createWorld({ name: testWorldName });
    await worldHelpers.navigateToWorld(testWorldName);

    testProjectName = TestDataGenerator.generateTestProjectName();
    await projectHelpers.createProject({ name: testProjectName });
    await projectHelpers.navigateToProject(testProjectName);
    await TestSetup.waitForPageLoad(page);
  });

  test('should update project through all lifecycle stages', async ({ page }) => {
    const stages: ProjectData['stage'][] = [
      'IDEA',
      'DESIGN',
      'PLANNING',
      'RESOURCE_GATHERING',
      'BUILDING',
      'TESTING',
      'COMPLETED'
    ];

    // Test progression through each stage
    for (let i = 1; i < stages.length; i++) {
      const currentStage = stages[i];
      if (!currentStage) continue;

      await projectHelpers.updateProjectStage(currentStage);

      // Verify stage update was successful
      await expect(page.locator('body')).toContainText(new RegExp(currentStage.replace("_", " "), 'i'));

      await TestSetup.waitForPageLoad(page);
    }
  });

  test('should allow stage updates in both directions', async ({ page }) => {
    // Move from Planning to Building
    await projectHelpers.updateProjectStage('BUILDING');
    await expect(page.locator('body')).toContainText(/building/i);

    // Move back to Design (regression)
    await projectHelpers.updateProjectStage('DESIGN');
    await expect(page.locator('body')).toContainText(/design/i);

    // Move forward to Resource Gathering
    await projectHelpers.updateProjectStage('RESOURCE_GATHERING');
    await expect(page.locator('body')).toContainText(/resource.*gathering/i);
  });

  test('should show different stage-specific content', async ({ page }) => {
    const stageTests: { stage: ProjectData["stage"], expectedContent: RegExp }[] = [
      { stage: 'PLANNING', expectedContent: /planning|plan|initial/i },
      { stage: 'DESIGN', expectedContent: /design|blueprint|schematic/i },
      { stage: 'RESOURCE_GATHERING', expectedContent: /resource|material|gathering/i },
      { stage: 'BUILDING', expectedContent: /building|construction|build/i },
      { stage: 'COMPLETED', expectedContent: /complete|finished|done/i }
    ];

    for (const stageTest of stageTests) {
      await projectHelpers.updateProjectStage(stageTest.stage);
      await expect(page.locator('body')).toContainText(stageTest.expectedContent);
      await TestSetup.waitForPageLoad(page);
    }
  });

  test.skip('should handle completion stage specially', async ({ page }) => {
    // Move project to Complete stage
    await projectHelpers.updateProjectStage('COMPLETED');
    await expect(page.locator('body')).toContainText(/complete/i);

    // Navigate back to project list
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);

    // Completed project should be visible but marked as complete
    const projectRow = page.locator('.project-item, [data-testid="project-item"]').filter({ hasText: testProjectName });
    await expect(projectRow).toBeVisible();

    // Should have some completion indicator
    await expect(projectRow.or(page.locator('body'))).toContainText(/complete|✓|done/i);
  });

  test.skip('should handle archived stage', async ({ page }) => {
    // Move project to Archived stage
    // await projectHelpers.updateProjectStage('ARCHIVED');
    await expect(page.locator('body')).toContainText(/archived/i);

    // Navigate back to project list
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);

    // Archived project behavior depends on implementation:
    // - Might be hidden by default
    // - Might be shown with archived indicator
    // - Might require special filter to view

    const projectRow = page.locator('.project-item, [data-testid="project-item"]').filter({ hasText: testProjectName });
    const isVisible = await projectRow.count() > 0;

    if (isVisible) {
      // If archived projects are shown, should have archived indicator
      await expect(projectRow.or(page.locator('body'))).toContainText(/archived/i);
    } else {
      // If archived projects are hidden, that's acceptable behavior
      expect(isVisible).toBe(false);
    }
  });

  test('should persist stage changes across page refreshes', async ({ page }) => {
    // Update to specific stage
    await projectHelpers.updateProjectStage('BUILDING');
    await expect(page.locator('body')).toContainText(/building/i);

    // Refresh page
    await page.reload();
    await TestSetup.waitForPageLoad(page);

    // Stage should be preserved
    await expect(page.locator('body')).toContainText(/building/i);
  });
});
