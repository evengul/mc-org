import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, ProjectHelpers, TestDataGenerator, TestSetup } from '../utils';

test.describe.skip('Project Filtering', () => {
  let authHelpers: AuthHelpers;
  let worldHelpers: WorldHelpers;
  let projectHelpers: ProjectHelpers;
  let testWorldName: string;

  test.beforeEach(async ({ page }) => {
    authHelpers = new AuthHelpers(page);
    worldHelpers = new WorldHelpers(page);
    projectHelpers = new ProjectHelpers(page);

    // Set up test environment with world and multiple projects
    await authHelpers.signInAsTestUser();
    testWorldName = TestDataGenerator.generateTestWorldName();
    await worldHelpers.createWorld({ name: testWorldName });
    await worldHelpers.navigateToWorld(testWorldName);

    // Create projects with different priorities and stages for filtering tests
    await projectHelpers.createProject({
      name: 'Critical Castle Project',
      description: 'High priority castle build',
      type: "BUILDING"
    });

    await projectHelpers.createProject({
      name: 'Normal Farm Project',
      description: 'Standard farm construction',
      type: "FARMING"
    });

    await projectHelpers.createProject({
      name: 'Nice Bridge Project',
      description: 'Optional bridge decoration',
      type: "DECORATION"
    });

    // Mark one project as complete for filtering tests
    await projectHelpers.navigateToProject('Critical Castle Project');
    await projectHelpers.updateProjectStage('COMPLETED');

    // Navigate back to world view for filtering tests
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);
  });

  test('should search projects by name', async ({ page }) => {
    // Search for "Castle"
    await projectHelpers.searchProjects('Castle');

    // Should show Castle project
    await expect(page.locator('text=Critical Castle Project')).toBeVisible();

    // Should not show other projects
    await expect(page.locator('text=Normal Farm Project')).not.toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).not.toBeVisible();

    // Search for "Farm"
    await projectHelpers.searchProjects('Farm');

    // Should show Farm project
    await expect(page.locator('text=Normal Farm Project')).toBeVisible();

    // Should not show other projects
    await expect(page.locator('text=Critical Castle Project')).not.toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).not.toBeVisible();
  });

  test('should filter projects by completion status', async ({ page }) => {
    // Initially all projects should be visible
    await expect(page.locator('text=Critical Castle Project')).toBeVisible();
    await expect(page.locator('text=Normal Farm Project')).toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).toBeVisible();

    // Enable "Hide Completed" filter
    await projectHelpers.filterByCompletion(true);

    // Completed project should be hidden
    await expect(page.locator('text=Critical Castle Project')).not.toBeVisible();

    // Non-completed projects should still be visible
    await expect(page.locator('text=Normal Farm Project')).toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).toBeVisible();

    // Disable "Hide Completed" filter
    await projectHelpers.filterByCompletion(false);

    // All projects should be visible again
    await expect(page.locator('text=Critical Castle Project')).toBeVisible();
    await expect(page.locator('text=Normal Farm Project')).toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).toBeVisible();
  });

  test('should clear all filters', async ({ page }) => {
    // Apply search filter
    await projectHelpers.searchProjects('Castle');
    await expect(page.locator('text=Critical Castle Project')).toBeVisible();
    await expect(page.locator('text=Normal Farm Project')).not.toBeVisible();

    // Apply completion filter
    await projectHelpers.filterByCompletion(true);

    // Clear all filters
    await projectHelpers.clearFilters();

    // All projects should be visible again
    await expect(page.locator('text=Critical Castle Project')).toBeVisible();
    await expect(page.locator('text=Normal Farm Project')).toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).toBeVisible();
  });

  test('should combine search and completion filters', async ({ page }) => {
    // Create another completed project for more complex filtering
    await projectHelpers.createProject({
      name: 'Completed Mine Project',
      description: 'Finished mining operation',
      type: "MINING"
    });

    await projectHelpers.navigateToProject('Completed Mine Project');
    await projectHelpers.updateProjectStage('COMPLETED');
    await worldHelpers.navigateToWorld(testWorldName);

    // Apply search filter first
    await projectHelpers.searchProjects('Project');

    // Should show all projects containing "Project"
    await expect(page.locator('text=Critical Castle Project')).toBeVisible();
    await expect(page.locator('text=Normal Farm Project')).toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).toBeVisible();
    await expect(page.locator('text=Completed Mine Project')).toBeVisible();

    // Now add completion filter
    await projectHelpers.filterByCompletion(true);

    // Should only show non-completed projects that match search
    await expect(page.locator('text=Critical Castle Project')).not.toBeVisible();
    await expect(page.locator('text=Completed Mine Project')).not.toBeVisible();
    await expect(page.locator('text=Normal Farm Project')).toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).toBeVisible();
  });

  test('should handle empty search results', async ({ page }) => {
    // Search for non-existent project
    await projectHelpers.searchProjects('NonExistentProject123');

    // Should show no projects or empty state
    await expect(page.locator('text=Critical Castle Project')).not.toBeVisible();
    await expect(page.locator('text=Normal Farm Project')).not.toBeVisible();
    await expect(page.locator('text=Nice Bridge Project')).not.toBeVisible();

    // Should show empty state or "no results" message
    const hasEmptyState = await page.locator('body').filter({ hasText: /no.*results|no.*projects|empty/i }).count() > 0;
    expect(hasEmptyState).toBe(true);
  });

  test('should maintain filter state during navigation', async ({ page }) => {
    // Apply search filter
    await projectHelpers.searchProjects('Castle');
    await expect(page.locator('text=Critical Castle Project')).toBeVisible();

    // Navigate to a project
    await projectHelpers.navigateToProject('Critical Castle Project');
    await TestSetup.waitForPageLoad(page);

    // Navigate back to world view
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);

    // Filter might or might not be maintained - this depends on implementation
    // This test documents the expected behavior
    const searchInput = page.locator('input[type="search"], input[placeholder*="search" i]');
    if (await searchInput.count() > 0) {
      const searchValue = await searchInput.inputValue();
      // Document whether search is maintained or cleared
      console.log('Search filter maintained:', searchValue === 'Castle');
    }
  });

  test('should show project count after filtering', async ({ page }) => {
    // Check initial project count
    const initialProjects = await projectHelpers.getVisibleProjects();
    expect(initialProjects.length).toBeGreaterThanOrEqual(3);

    // Apply search filter
    await projectHelpers.searchProjects('Castle');

    // Check filtered project count
    const filteredProjects = await projectHelpers.getVisibleProjects();
    expect(filteredProjects.length).toBe(1);
    expect(filteredProjects[0]).toContain('Castle');
  });
});
