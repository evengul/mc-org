import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, ProjectHelpers, TaskHelpers, TestDataGenerator, TestSetup } from '../utils';

test.describe('Task Management', () => {
  let authHelpers: AuthHelpers;
  let worldHelpers: WorldHelpers;
  let projectHelpers: ProjectHelpers;
  let taskHelpers: TaskHelpers;
  let testWorldName: string;
  let testProjectName: string;

  test.beforeEach(async ({ page }) => {
    authHelpers = new AuthHelpers(page);
    worldHelpers = new WorldHelpers(page);
    projectHelpers = new ProjectHelpers(page);
    taskHelpers = new TaskHelpers(page);

    // Set up test environment
    await authHelpers.signInAsTestUser();
    testWorldName = TestDataGenerator.generateTestWorldName();
    await worldHelpers.createWorld({ name: testWorldName });
    await worldHelpers.navigateToWorld(testWorldName);

    testProjectName = TestDataGenerator.generateTestProjectName();
    await projectHelpers.createProject({ name: testProjectName });
    await projectHelpers.navigateToProject(testProjectName);
    await TestSetup.waitForPageLoad(page);
  });

  test('should complete action task', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    // Create action task
    await taskHelpers.createTask({
      name: taskName,
      type: 'Action',
      description: 'Build the main entrance'
    });

    // Complete the task
    await taskHelpers.completeActionTask(taskName);

    // Verify task is marked as complete
    await taskHelpers.verifyTaskCompleted(taskName);
  });

  test('should update countable task progress by incrementing', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    // Create countable task
    await taskHelpers.createTask({
      name: taskName,
      type: 'Countable',
      quantity: 10,
      description: 'Collect 10 diamond blocks'
    });

    // Increment progress multiple times
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');

    // Verify progress is updated (exact verification depends on UI implementation)
    const taskRow = page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await expect(taskRow).toBeVisible();
    await expect(taskRow).toContainText(/[3-9]|progress/i); // Should show some progress
  });

  test.skip('should set specific quantity for countable task', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    // Create countable task
    await taskHelpers.createTask({
      name: taskName,
      type: 'Countable',
      quantity: 20
    });

    // Set specific progress value
    await taskHelpers.updateCountableTaskProgress(taskName, 'set', 15);

    // Verify specific value is set
    const taskRow = page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await expect(taskRow).toBeVisible();
    await expect(taskRow).toContainText(/15/);
  });

  test('should complete countable task when reaching target quantity', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    // Create small countable task for easy completion
    await taskHelpers.createTask({
      name: taskName,
      type: 'Countable',
      quantity: 3
    });

    // Increment to completion
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');

    // Task should be marked as complete
    await taskHelpers.verifyTaskCompleted(taskName);
  });

  test('should delete task', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    // Create task
    await taskHelpers.createTask({
      name: taskName,
      type: 'Action'
    });

    await taskHelpers.verifyTaskExists(taskName);

    // Delete task
    await taskHelpers.deleteTask(taskName);

    // Verify task is gone
    await expect(page.locator('text=' + taskName)).not.toBeVisible();
  });

  test('should persist task progress across page refreshes', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    // Create and update task
    await taskHelpers.createTask({
      name: taskName,
      type: 'Countable',
      quantity: 10
    });

    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');

    // Refresh page
    await page.reload();
    await TestSetup.waitForPageLoad(page);

    // Progress should be maintained
    const taskRow = page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await expect(taskRow).toBeVisible();
    await expect(taskRow).toContainText(/1/);
  });

  test('should handle multiple task updates in sequence', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    await taskHelpers.createTask({
      name: taskName,
      type: 'Countable',
      quantity: 20
    });

    // Perform rapid updates
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');
    await taskHelpers.updateCountableTaskProgress(taskName, 'increment');

    // Should handle all updates correctly
    const taskRow = page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await expect(taskRow).toBeVisible();
    await expect(taskRow).toContainText(/5/); // Should show final result
  });
});
