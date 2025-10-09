import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, ProjectHelpers, TaskHelpers, TestDataGenerator, TestSetup, TaskData } from '../utils';

test.describe('Task Creation', () => {
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

  test('should create action (doable) task', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    const taskData = await taskHelpers.createTask({
      name: taskName,
      description: 'Complete the foundation layer for the castle',
      type: 'Action',
      priority: 'CRITICAL'
    });

    expect(taskData.name).toBe(taskName);
    expect(taskData.type).toBe('Action');
    expect(taskData.priority).toBe('CRITICAL');

    await taskHelpers.verifyTaskExists(taskName);
  });

  test('should create countable task with quantity', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    const taskData = await taskHelpers.createTask({
      name: taskName,
      description: 'Collect stone blocks for construction',
      type: 'Countable',
      priority: 'MEDIUM',
      quantity: 64
    });

    expect(taskData.name).toBe(taskName);
    expect(taskData.type).toBe('Countable');
    expect(taskData.quantity).toBe(64);

    await taskHelpers.verifyTaskExists(taskName);
  });

  test('should create tasks with different priority levels', async ({ page }) => {
    const priorities: TaskData['priority'][] = ['LOW', 'MEDIUM', 'CRITICAL'];

    for (const priority of priorities) {
      const taskName = `${priority} Task ${TestDataGenerator.generateUniqueId()}`;

      const taskData = await taskHelpers.createTask({
        name: taskName,
        description: `Test task with ${priority} priority`,
        type: 'Action',
        priority: priority
      });

      expect(taskData.priority).toBe(priority);
      await taskHelpers.verifyTaskExists(taskName);
    }
  });

  test('should create task with minimal required information', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    const taskData = await taskHelpers.createTask({
      name: taskName
    });

    expect(taskData.name).toBe(taskName);
    expect(taskData.description).toBeDefined();
    expect(taskData.type).toBeDefined();
    expect(taskData.priority).toBeDefined();

    await taskHelpers.verifyTaskExists(taskName);
  });

  test('should validate task creation form', async ({ page }) => {
    // Try to create task without required fields
    const createTaskButton = page.locator('button, a').filter({ hasText: /create.*task|add.*task/i });
    await expect(createTaskButton).toBeVisible();
    await createTaskButton.click();

    // Try to submit empty form
    const submitButton = page.locator('button[type="submit"], button').filter({ hasText: /create|save|submit/i });
    await submitButton.click();

    // Should show validation errors
    await expect(page.locator('body')).toContainText(/required|error|invalid/i);
  });

  test('should create multiple tasks in same project', async ({ page }) => {
    const tasks = [
      { name: 'Foundation Task', type: 'Action' as const, priority: 'CRITICAL' as const },
      { name: 'Stone Collection', type: 'Countable' as const, priority: 'MEDIUM' as const, quantity: 32 },
      { name: 'Decoration Task', type: 'Action' as const, priority: 'LOW' as const }
    ];

    for (const taskConfig of tasks) {
      const taskName = `${taskConfig.name} ${TestDataGenerator.generateUniqueId()}`;

      await taskHelpers.createTask({
        name: taskName,
        description: `Test task: ${taskConfig.name}`,
        type: taskConfig.type,
        priority: taskConfig.priority,
        quantity: taskConfig.quantity
      });

      await taskHelpers.verifyTaskExists(taskName);
    }

    // Verify all tasks are visible
    const visibleTasks = await taskHelpers.getVisibleTasks();
    expect(visibleTasks.length).toBeGreaterThanOrEqual(3);
  });

  test('should handle countable task quantity validation', async ({ page }) => {
    const taskName = TestDataGenerator.generateTestTaskName();

    // Create countable task with large quantity
    const taskData = await taskHelpers.createTask({
      name: taskName,
      type: 'Countable',
      quantity: 999
    });

    expect(taskData.quantity).toBe(999);
    await taskHelpers.verifyTaskExists(taskName);
  });

  test('should differentiate between task types visually', async ({ page }) => {
    const actionTaskName = `Action ${TestDataGenerator.generateUniqueId()}`;
    const countableTaskName = `Countable ${TestDataGenerator.generateUniqueId()}`;

    // Create both task types
    await taskHelpers.createTask({
      name: actionTaskName,
      type: 'Action'
    });

    await taskHelpers.createTask({
      name: countableTaskName,
      type: 'Countable',
      quantity: 50
    });

    // Both tasks should be visible
    await taskHelpers.verifyTaskExists(actionTaskName);
    await taskHelpers.verifyTaskExists(countableTaskName);

    // Tasks should have different visual indicators for their types
    // This might be icons, different buttons, or different layouts
    const actionTaskRow = page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: actionTaskName });
    const countableTaskRow = page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: countableTaskName });

    await expect(actionTaskRow).toBeVisible();
    await expect(countableTaskRow).toBeVisible();

    // Countable tasks should show quantity or progress indicators
    await expect(countableTaskRow).toContainText(/\d+|progress|quantity/i);
  });
});
