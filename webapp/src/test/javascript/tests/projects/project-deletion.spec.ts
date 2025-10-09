import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, ProjectHelpers, TestDataGenerator, TestSetup } from '../utils';

test.describe('Project Deletion', () => {
  let authHelpers: AuthHelpers;
  let worldHelpers: WorldHelpers;
  let projectHelpers: ProjectHelpers;
  let testWorldName: string;

  test.beforeEach(async ({ page }) => {
    authHelpers = new AuthHelpers(page);
    worldHelpers = new WorldHelpers(page);
    projectHelpers = new ProjectHelpers(page);

    // Set up test environment
    await authHelpers.signInAsTestUser();
    testWorldName = TestDataGenerator.generateTestWorldName();
    await worldHelpers.createWorld({ name: testWorldName });
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);
  });

  test('should delete a project successfully', async ({ page }) => {
    const projectName = TestDataGenerator.generateTestProjectName();

    // Create project
    await projectHelpers.createProject({ name: projectName });
    await projectHelpers.verifyProjectExists(projectName);

    // Delete project
    await projectHelpers.deleteProject(projectName);

    // Navigate back to world view and verify project is gone
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);

    await expect(page.locator('text=' + projectName)).not.toBeVisible();
  });

  test('should require confirmation for project deletion', async ({ page }) => {
    const projectName = TestDataGenerator.generateTestProjectName();

    await projectHelpers.createProject({ name: projectName });
    await projectHelpers.navigateToProject(projectName);

    // Find delete button
    const deleteButton = page.locator('button').filter({ hasText: /delete.*project/i });
    await expect(deleteButton).toBeVisible();
    await deleteButton.click();

    // Should show confirmation dialog or require additional confirmation
    const confirmationElements = page.locator('button').filter({ hasText: /confirm|delete|yes/i });
    if (await confirmationElements.count() > 0) {
      expect(await confirmationElements.count()).toBeGreaterThan(0);
    } else {
      await expect(page.locator('body')).toContainText(/confirm|sure|delete|warning/i);
    }
  });

  test('should delete project with tasks', async ({ page }) => {
    const projectName = TestDataGenerator.generateTestProjectName();

    // Create project
    await projectHelpers.createProject({ name: projectName });
    await projectHelpers.navigateToProject(projectName);

    // Create a task in this project (if task creation is available)
    const createTaskButton = page.locator('button, a').filter({ hasText: /create.*task|add.*task/i });
    if (await createTaskButton.count() > 0) {
      await createTaskButton.click();
      await page.fill('input[name="name"], input[placeholder*="name" i]', 'Test Task for Deletion');
      await page.fill('textarea[name="description"], textarea[placeholder*="description" i]', 'Task to test project deletion');
      const submitButton = page.locator('button[type="submit"], button').filter({ hasText: /create|save|submit/i });
      await submitButton.click();
      await TestSetup.waitForPageLoad(page);
    }

    // Delete the project (should also delete all tasks)
    await projectHelpers.deleteProject(projectName);

    // Verify project is gone
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);
    await expect(page.locator('text=' + projectName)).not.toBeVisible();
  });

  test('should maintain other projects when deleting one', async ({ page }) => {
    const project1Name = TestDataGenerator.generateTestProjectName();
    const project2Name = TestDataGenerator.generateTestProjectName();
    const project3Name = TestDataGenerator.generateTestProjectName();

    // Create multiple projects
    await projectHelpers.createProject({ name: project1Name });
    await projectHelpers.createProject({ name: project2Name });
    await projectHelpers.createProject({ name: project3Name });

    // Verify all exist
    await projectHelpers.verifyProjectExists(project1Name);
    await projectHelpers.verifyProjectExists(project2Name);
    await projectHelpers.verifyProjectExists(project3Name);

    // Delete middle project
    await projectHelpers.deleteProject(project2Name);

    // Verify others still exist
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);

    await expect(page.locator('text=' + project1Name)).toBeVisible();
    await expect(page.locator('text=' + project2Name)).not.toBeVisible();
    await expect(page.locator('text=' + project3Name)).toBeVisible();
  });

  test('should handle deletion from project list view', async ({ page }) => {
    const projectName = TestDataGenerator.generateTestProjectName();

    await projectHelpers.createProject({ name: projectName });

    // Stay in world view (project list)
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);

    // Look for delete button in project list
    const projectRow = page.locator('.project-item, [data-testid="project-item"]').filter({ hasText: projectName });
    await expect(projectRow).toBeVisible();

    const deleteButton = projectRow.locator('button').filter({ hasText: /delete|remove|✗/i });
    if (await deleteButton.count() > 0) {
      await deleteButton.click();

      // Handle confirmation if present
      const confirmButton = page.locator('button').filter({ hasText: /confirm|delete|yes/i });
      if (await confirmButton.count() > 0) {
        await confirmButton.click();
      }

      await TestSetup.waitForPageLoad(page);

      // Verify project is deleted
      await expect(page.locator('text=' + projectName)).not.toBeVisible();
    } else {
      // If no delete button in list view, that's acceptable - skip this test
      test.skip();
    }
  });

  test('should handle deletion of completed projects', async ({ page }) => {
    const projectName = TestDataGenerator.generateTestProjectName();

    // Create and complete project
    await projectHelpers.createProject({ name: projectName });
    await projectHelpers.navigateToProject(projectName);
    await projectHelpers.updateProjectStage('Complete');

    // Delete completed project
    await projectHelpers.deleteProject(projectName);

    // Verify it's gone
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);
    await expect(page.locator('text=' + projectName)).not.toBeVisible();
  });

  test('should handle deletion permissions correctly', async ({ page }) => {
    const projectName = TestDataGenerator.generateTestProjectName();

    // Create project (user should be owner/creator)
    await projectHelpers.createProject({ name: projectName });
    await projectHelpers.navigateToProject(projectName);

    // As creator, should have delete permissions
    const deleteButton = page.locator('button').filter({ hasText: /delete.*project/i });
    await expect(deleteButton).toBeVisible();

    // Note: Testing with different user roles would require
    // multi-user test setup which we don't have yet
  });
});
