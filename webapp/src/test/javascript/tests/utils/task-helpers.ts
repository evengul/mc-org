/**
 * Task management utilities for Playwright tests
 */
import { Page } from '@playwright/test';

export interface TaskData {
  name: string;
  description: string;
  type: 'Action' | 'Countable';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  quantity?: number; // For countable tasks
}

export class TaskHelpers {
  constructor(private page: Page) {}

  /**
   * Create a new task within the current project
   */
  async createTask(taskData: Partial<TaskData> = {}): Promise<TaskData> {
    const timestamp = Date.now();
    const task: TaskData = {
      name: taskData.name || `Test Task ${timestamp}`,
      description: taskData.description || `Test task created at ${new Date().toISOString()}`,
      type: taskData.type || 'Action',
      priority: taskData.priority || 'MEDIUM',
      quantity: taskData.quantity || (taskData.type === 'Countable' ? 10 : undefined)
    };

    // Look for "Create Task" or "Add Task" button
    const createTaskButton = this.page.locator('#project-header-content').getByRole('button', { name: 'New Task' });
    await createTaskButton.waitFor({ state: 'visible' });
    await createTaskButton.click();

    // Fill out task creation form
    await this.page.fill('input[name="name"]', task.name);
    await this.page.fill('textarea[name="description"]', task.description);

    // Select task type
    const typeTabs = this.page.locator('div.task-requirements-tabs').locator("button");
    if (task.type === "Countable") {
      await typeTabs.nth(0).click();
    } else if (task.type === "Action") {
      await typeTabs.nth(1).click();
    }

    // Select priority
    const priorityOption = this.page.locator("#project-header-content").locator(`input[value="${task.priority}"], label`).filter({ hasText: task.priority });
    if (await priorityOption.count() > 0) {
      await priorityOption.click();
    }

    if (task.type === 'Countable' && task.quantity) {
      const requirementNameInput = this.page.locator("#project-header-content").locator("#item-requirement-name-input");
      await requirementNameInput.fill("stone");
      const requirementAmountInput = this.page.locator("#project-header-content").locator("#item-requirement-amount-input");
      await requirementAmountInput.fill(task.quantity.toString());

      const requirementButton = this.page.locator("#project-header-content").locator('button').filter({ hasText: "Add Item Requirement" });
      if (await requirementButton.count() > 0) {
        await requirementButton.click();
      }
    } else if (task.type === "Action") {
      const requirementInput = this.page.locator("#project-header-content").locator("#action-requirement-input");
      await requirementInput.fill("Build a wall");

      const requirementButton = this.page.locator("#project-header-content").locator('button').filter({ hasText: "Add Action Requirement" });
      if (await requirementButton.count() > 0) {
        await requirementButton.click();
      }
    }



    // Submit the form
    const submitButton = this.page.locator("#project-header-content").locator('button[type="submit"]').filter({ hasText: /create|save|submit/i });
    await submitButton.click();

    await this.page.waitForLoadState('networkidle');

    return task;
  }

  /**
   * Mark an action task as complete
   */
  async completeActionTask(taskName: string): Promise<void> {
    const taskRow = this.page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await taskRow.waitFor({ state: 'visible' });

    const completeButton = taskRow.locator('input.task-completion-checkbox');
    await completeButton.waitFor({ state: 'visible' });
    await completeButton.click();

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Update progress on a countable task
   */
  async updateCountableTaskProgress(taskName: string, action: 'increment' | 'decrement' | 'set', value?: number): Promise<void> {
    const taskRow = this.page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await taskRow.waitFor({ state: 'visible' });

    if (await taskRow.locator("button").filter({hasText: "Hide details"}).count() === 0) {
      const showDetailsButton = taskRow.locator('button').filter({ hasText: "Show details" });
      if (await showDetailsButton.count() > 0) {
        await showDetailsButton.click();
      }
    }

    if (action === 'increment') {
      const incrementButton = taskRow.getByText("+1", { exact: true });
      await incrementButton.waitFor({ state: 'visible' });
      await incrementButton.click();
    } else if (action === 'decrement') {
      const decrementButton = taskRow.getByText("-1", { exact: true });
      await decrementButton.waitFor({ state: 'visible' });
      await decrementButton.click();
    } else if (action === 'set' && value !== undefined) {
      const quantityInput = taskRow.locator('input[type="number"]');
      if (await quantityInput.count() > 0) {
        await quantityInput.fill(value.toString());
        await quantityInput.press('Enter');
      }
    }

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Search for tasks by name
   */
  async searchTasks(searchTerm: string): Promise<void> {
    const searchInput = this.page.locator('input[type="search"], input[placeholder*="search" i]');
    await searchInput.waitFor({ state: 'visible' });
    await searchInput.fill(searchTerm);
  }



  /**
   * Filter tasks by completion status
   */
  async filterTasks(options: {
    status?: "ALL" | "IN_PROGRESS" | "COMPLETED"
    priority?: 'ALL' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  } = {}): Promise<void> {
    if (options.status !== undefined) {
      const completionStatusSelect = this.page.locator('select[name="completionStatus"]');
      if (await completionStatusSelect.count() > 0) {
        await completionStatusSelect.selectOption(options.status)
      }
    }

    if (options.priority) {
      const priorityFilter = this.page.locator('select[name="priority"]');
      if (await priorityFilter.count() > 0) {
        await priorityFilter.selectOption(options.priority);
      }
    }
  }

  async applySearch() {
    const searchButton = this.page.locator('button').filter({ hasText: "Search" });
    if (await searchButton.count() > 0) {
      await searchButton.click();
    }
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Delete a task
   */
  async deleteTask(taskName: string): Promise<void> {
    this.page.on("dialog", async (dialog) => await dialog.accept())
    const taskRow = this.page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await taskRow.waitFor({ state: 'visible' });

    const deleteButton = taskRow.locator('button[title="Delete task"]');
    await deleteButton.waitFor({ state: 'visible' });
    await deleteButton.click();

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Verify task exists in task list
   */
  async verifyTaskExists(taskName: string): Promise<void> {
    await this.page.waitForSelector(`text=${taskName}`, { state: 'visible' });
  }

  /**
   * Verify task is marked as complete
   */
  async verifyTaskCompleted(taskName: string): Promise<void> {
    const taskRow = this.page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await taskRow.waitFor({ state: 'visible' });

    const completionIndicator = taskRow.locator('input[type="checkbox"]:checked');
    await completionIndicator.waitFor({ state: 'visible' });
  }

  /**
   * Get visible tasks on current page
   */
  async getVisibleTasks(): Promise<string[]> {
    const taskElements = this.page.locator('.task-item');
    const count = await taskElements.count();
    const tasks: string[] = [];

    for (let i = 0; i < count; i++) {
      const taskName = await taskElements.nth(i).textContent();
      if (taskName) {
        tasks.push(taskName.trim());
      }
    }

    return tasks;
  }
}
