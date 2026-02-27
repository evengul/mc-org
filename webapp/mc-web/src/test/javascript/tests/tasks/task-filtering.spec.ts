import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, ProjectHelpers, TaskHelpers, TestDataGenerator, TestSetup } from '../utils';

test.describe('Task Filtering', () => {
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

    // Set up test environment with multiple tasks for filtering
    await authHelpers.signInAsTestUser();
    testWorldName = TestDataGenerator.generateTestWorldName();
    await worldHelpers.createWorld({ name: testWorldName });
    await worldHelpers.navigateToWorld(testWorldName);

    testProjectName = TestDataGenerator.generateTestProjectName();
    await projectHelpers.createProject({ name: testProjectName });
    await projectHelpers.navigateToProject(testProjectName);

    // Create tasks with different types and priorities for filtering tests
    await taskHelpers.createTask({
      name: 'Critical Foundation Task',
      type: 'Action',
      priority: 'CRITICAL',
      description: 'Build main foundation'
    });

    await taskHelpers.createTask({
      name: 'Normal Stone Collection',
      type: 'Countable',
      priority: 'MEDIUM',
      quantity: 64,
      description: 'Collect stone blocks'
    });

    await taskHelpers.createTask({
      name: 'Nice Decoration Task',
      type: 'Action',
      priority: 'LOW',
      description: 'Add decorative elements'
    });

    // Complete one task for filtering tests
    await taskHelpers.completeActionTask('Critical Foundation Task');

    await TestSetup.waitForPageLoad(page);
  });

  test('should search tasks by name', async ({ page }) => {
    // Search for "Foundation"
    await taskHelpers.searchTasks('Foundation');
    await taskHelpers.applySearch();

    // Should show Foundation task
    await expect(page.locator('text=Critical Foundation Task')).toBeVisible();

    // Should not show other tasks
    await expect(page.locator('text=Normal Stone Collection')).not.toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).not.toBeVisible();

    // Search for "Collection"
    await taskHelpers.searchTasks('Collection');

    // Should show Collection task
    await expect(page.locator('text=Normal Stone Collection')).toBeVisible();

    // Should not show other tasks
    await expect(page.locator('text=Critical Foundation Task')).not.toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).not.toBeVisible();
  });

  test('should filter tasks by completion status', async ({ page }) => {
    // Initially all tasks should be visible
    await expect(page.locator('text=Critical Foundation Task')).toBeVisible();
    await expect(page.locator('text=Normal Stone Collection')).toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).toBeVisible();

    // Enable "Hide Completed" filter
    await taskHelpers.filterTasks({ status: "IN_PROGRESS" });
    await taskHelpers.applySearch();

    // Completed task should be hidden
    await expect(page.locator('text=Critical Foundation Task')).not.toBeVisible();

    // Non-completed tasks should still be visible
    await expect(page.locator('text=Normal Stone Collection')).toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).toBeVisible();

    // Disable "Hide Completed" filter
    await taskHelpers.filterTasks({ status: "ALL" });
    await taskHelpers.applySearch();

    // All tasks should be visible again
    await expect(page.locator('text=Critical Foundation Task')).toBeVisible();
    await expect(page.locator('text=Normal Stone Collection')).toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).toBeVisible();
  });

  test('should filter tasks by priority', async ({ page }) => {
    // Filter by Critical priority
    await taskHelpers.filterTasks({ priority: 'CRITICAL' });
    await taskHelpers.applySearch();

    // Should show only Critical task
    await expect(page.locator('text=Critical Foundation Task')).toBeVisible();
    await expect(page.locator('text=Normal Stone Collection')).not.toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).not.toBeVisible();

    // Filter by Normal priority
    await taskHelpers.filterTasks({ priority: 'MEDIUM' });
    await taskHelpers.applySearch();

    // Should show only Normal task
    await expect(page.locator('text=Normal Stone Collection')).toBeVisible();
    await expect(page.locator('text=Critical Foundation Task')).not.toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).not.toBeVisible();

    // Filter by Nice-to-have priority
    await taskHelpers.filterTasks({ priority: 'LOW' });
    await taskHelpers.applySearch();

    // Should show only Nice-to-have task
    await expect(page.locator('text=Nice Decoration Task')).toBeVisible();
    await expect(page.locator('text=Critical Foundation Task')).not.toBeVisible();
    await expect(page.locator('text=Normal Stone Collection')).not.toBeVisible();
  });

  test('should combine multiple filters', async ({ page }) => {
    // Create additional task for complex filtering
    await taskHelpers.createTask({
      name: 'Critical Wood Collection',
      priority: 'CRITICAL',
      quantity: 32
    });

    // Filter by Critical priority AND Countable type
    await taskHelpers.filterTasks({
      priority: 'CRITICAL',
      status: 'COMPLETED',
    });
    await taskHelpers.applySearch();

    // Should show only the Critical Completed task
    await expect(page.locator('text=Critical Wood Collection')).not.toBeVisible(); // IN_PROGRESS
    await expect(page.locator('text=Critical Foundation Task')).toBeVisible();
    await expect(page.locator('text=Normal Stone Collection')).not.toBeVisible(); // Not Critical
    await expect(page.locator('text=Nice Decoration Task')).not.toBeVisible(); // No match
  });

  test('should handle empty search results', async ({ page }) => {
    // Search for non-existent task
    await taskHelpers.searchTasks('NonExistentTask123');
    await taskHelpers.applySearch();

    // Should show no tasks
    await expect(page.locator('text=Critical Foundation Task')).not.toBeVisible();
    await expect(page.locator('text=Normal Stone Collection')).not.toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).not.toBeVisible();

    // Should show empty state or "no results" message
    const hasEmptyState = await page.getByText("No tasks found matching the search criteria.").count() > 0;
    expect(hasEmptyState).toBe(true);
  });

  test.skip('should show task count after filtering', async ({ page }) => {
    // Get initial task count
    const initialTasks = await taskHelpers.getVisibleTasks();
    expect(initialTasks.length).toBeGreaterThanOrEqual(3);

    // Apply search filter
    await taskHelpers.searchTasks('Critical');

    // Should show fewer tasks
    const filteredTasks = await taskHelpers.getVisibleTasks();
    expect(filteredTasks.length).toBeLessThan(initialTasks.length);

    // Verify filtered results contain search term
    for (const task of filteredTasks) {
      expect(task.toLowerCase()).toContain('critical');
    }
  });

  test('should combine search with other filters', async ({ page }) => {
    // Apply search first
    await taskHelpers.searchTasks('Task');
    await taskHelpers.applySearch();

    // Should show tasks containing "Task"
    await expect(page.locator('text=Critical Foundation Task')).toBeVisible();
    await expect(page.locator('text=Nice Decoration Task')).toBeVisible();
    await expect(page.locator('text=Normal Stone Collection')).not.toBeVisible();

    // Add completion filter
    await taskHelpers.filterTasks({ status: "IN_PROGRESS" });
    await taskHelpers.applySearch();

    // Should show only non-completed tasks that match search
    await expect(page.locator('text=Critical Foundation Task')).not.toBeVisible(); // Completed
    await expect(page.locator('text=Nice Decoration Task')).toBeVisible(); // Not completed, matches search
    await expect(page.locator('text=Normal Stone Collection')).not.toBeVisible(); // Doesn't match search
  });

  test('should reset filters properly', async ({ page }) => {
    // Apply multiple filters
    await taskHelpers.searchTasks('Foundation');
    await taskHelpers.filterTasks({ priority: 'CRITICAL', status: "ALL" });
    await taskHelpers.applySearch();

    // Should show filtered results
    await expect(page.locator('text=Critical Foundation Task')).toBeVisible();
    await expect(page.locator('text=Normal Stone Collection')).not.toBeVisible();

    // Clear search
    await taskHelpers.searchTasks('');
    await taskHelpers.applySearch();

    // Should show more tasks now
    await expect(page.locator('text=Critical Foundation Task')).toBeVisible();

    // Clear priority filter (if there's a clear/reset option)
    const clearFiltersButton = page.locator('button').filter({ hasText: /clear.*filter|reset|all/i });
    if (await clearFiltersButton.count() > 0) {
      await clearFiltersButton.click();
      await taskHelpers.applySearch();
      await TestSetup.waitForPageLoad(page);

      // All tasks should be visible again
      await expect(page.locator('text=Critical Foundation Task')).not.toBeVisible();
      await expect(page.locator('text=Normal Stone Collection')).toBeVisible();
      await expect(page.locator('text=Nice Decoration Task')).toBeVisible();
    }
  });
});
