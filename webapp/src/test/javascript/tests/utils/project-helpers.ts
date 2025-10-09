/**
 * Project management utilities for Playwright tests
 */
import { Page } from '@playwright/test';

export interface ProjectData {
  name: string;
  description?: string;
  type: "BUILDING" | "REDSTONE" | "MINING" | "FARMING" | "EXPLORATION" | "DECORATION" | "TECHNICAL";
  stage?: 'IDEA' | 'DESIGN' | 'PLANNING' | 'RESOURCE_GATHERING' | 'BUILDING' | 'TESTING' | 'COMPLETED';
}

export class ProjectHelpers {
  constructor(private page: Page) {}

  /**
   * Create a new project within the current world
   */
  async createProject(projectData: Partial<ProjectData> = {}): Promise<ProjectData> {
    const timestamp = Date.now();
    const project: ProjectData = {
      name: projectData.name || `Test Project ${timestamp}`,
      description: projectData.description || `Test project created at ${new Date().toISOString()}`,
      type: projectData.type || 'BUILDING'
    };

    // Look for "Create Project" or "Add Project" button
    const createProjectButton = this.page.getByRole("button").filter({ hasText: "New Project" })
    await createProjectButton.waitFor({ state: 'visible' });
    await createProjectButton.click();

    // Fill out project creation form
    await this.page.fill('input[name="name"]', project.name);
    await this.page.fill('textarea[name="description"]', project.description);

    // Select type
    const typeOption = this.page.locator(`input[value="${project.type}"], label`).filter({ hasText: new RegExp(project.type, 'i') });
    if (await typeOption.count() > 0) {
      await typeOption.click();
    }

    // Submit the form
    const submitButton = this.page.locator('button[type="submit"], button').filter({ hasText: /create|save|submit/i });
    await submitButton.click();

    await this.page.waitForLoadState('networkidle');

    return project;
  }

  /**
   * Navigate to a specific project
   */
  async navigateToProject(projectName: string): Promise<void> {
    const projectLink = this.page.getByRole("listitem").filter({ hasText: projectName }).getByRole("button").filter({ hasText: "View Project" })
    await projectLink.waitFor({ state: 'visible' });
    await projectLink.click();

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Update project stage
   */
  async updateProjectStage(newStage: ProjectData['stage']): Promise<void> {
    if (!newStage) return;

    // Look for stage dropdown or buttons
    const stageSelect = this.page.locator('select[name="stage"], select[name="projectStage"]');
    if (await stageSelect.count() > 0) {
      await stageSelect.selectOption(newStage);
    } else {
      // Try buttons or other stage selection method
      const stageButton = this.page.locator('button').filter({ hasText: newStage });
      if (await stageButton.count() > 0) {
        await stageButton.click();
      }
    }

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Search for projects by name
   */
  async searchProjects(searchTerm: string): Promise<void> {
    const searchInput = this.page.locator('input[type="search"], input[placeholder*="search" i]');
    await searchInput.waitFor({ state: 'visible' });
    await searchInput.fill(searchTerm);

    // Wait for search results
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Filter projects by completion status
   */
  async filterByCompletion(hideCompleted: boolean = true): Promise<void> {
    const hideCompletedToggle = this.page.locator('input[type="checkbox"]').filter({ hasText: /hide.*completed/i });
    if (await hideCompletedToggle.count() > 0) {
      if (hideCompleted) {
        await hideCompletedToggle.check();
      } else {
        await hideCompletedToggle.uncheck();
      }
    }

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Clear all filters
   */
  async clearFilters(): Promise<void> {
    const clearFiltersButton = this.page.locator('button').filter({ hasText: /clear.*filter/i });
    if (await clearFiltersButton.count() > 0) {
      await clearFiltersButton.click();
      await this.page.waitForLoadState('networkidle');
    }
  }

  /**
   * Delete a project
   */
  async deleteProject(projectName: string): Promise<void> {
    await this.navigateToProject(projectName);

    // Look for delete button (might be in settings or main page)
    const deleteButton = this.page.locator('button').filter({ hasText: /delete.*project/i });
    await deleteButton.waitFor({ state: 'visible' });
    await deleteButton.click();

    // Confirm deletion if there's a confirmation dialog
    const confirmButton = this.page.locator('button').filter({ hasText: /confirm|delete|yes/i });
    if (await confirmButton.count() > 0) {
      await confirmButton.click();
    }

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Verify project exists in project list
   */
  async verifyProjectExists(projectName: string): Promise<void> {
    await this.page.waitForSelector(`text=${projectName}`, { state: 'visible' });
  }

  /**
   * Get visible projects on current page
   */
  async getVisibleProjects(): Promise<string[]> {
    const projectElements = this.page.locator('[data-testid="project-item"], .project-item, .project-card');
    const count = await projectElements.count();
    const projects: string[] = [];

    for (let i = 0; i < count; i++) {
      const projectName = await projectElements.nth(i).textContent();
      if (projectName) {
        projects.push(projectName.trim());
      }
    }

    return projects;
  }
}
