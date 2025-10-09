/**
 * Task management utilities for Playwright tests
 */
import { Page } from '@playwright/test';

export interface TaskData {
  name: string;
  description: string;
  type: 'Action' | 'Countable';
  priority: 'Critical' | 'Normal' | 'Nice-to-have';
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
      priority: taskData.priority || 'Normal',
      quantity: taskData.quantity || (taskData.type === 'Countable' ? 10 : undefined)
    };

    // Look for "Create Task" or "Add Task" button
    const createTaskButton = this.page.locator('button, a').filter({ hasText: /create.*task|add.*task|\+.*task/i });
    await createTaskButton.waitFor({ state: 'visible' });
    await createTaskButton.click();

    // Fill out task creation form
    await this.page.fill('input[name="name"], input[placeholder*="name" i]', task.name);
    await this.page.fill('textarea[name="description"], textarea[placeholder*="description" i]', task.description);

    // Select task type
    const typeSelect = this.page.locator('select[name="type"], select[name="taskType"]');
    if (await typeSelect.count() > 0) {
      await typeSelect.selectOption(task.type);
    } else {
      // Try radio buttons
      const typeOption = this.page.locator(`input[value="${task.type}"], label`).filter({ hasText: task.type });
      if (await typeOption.count() > 0) {
        await typeOption.click();
      }
    }

    // Select priority
    const prioritySelect = this.page.locator('select[name="priority"]');
    if (await prioritySelect.count() > 0) {
      await prioritySelect.selectOption(task.priority);
    } else {
      const priorityOption = this.page.locator(`input[value="${task.priority}"], label`).filter({ hasText: task.priority });
      if (await priorityOption.count() > 0) {
        await priorityOption.click();
      }
    }

    // Set quantity for countable tasks
    if (task.type === 'Countable' && task.quantity) {
      const quantityInput = this.page.locator('input[name="quantity"], input[type="number"]');
      if (await quantityInput.count() > 0) {
        await quantityInput.fill(task.quantity.toString());
      }
    }

    // Submit the form
    const submitButton = this.page.locator('button[type="submit"], button').filter({ hasText: /create|save|submit/i });
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

    const completeButton = taskRow.locator('button').filter({ hasText: /complete|done|✓/i });
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

    if (action === 'increment') {
      const incrementButton = taskRow.locator('button').filter({ hasText: /\+|increment|more/i });
      await incrementButton.waitFor({ state: 'visible' });
      await incrementButton.click();
    } else if (action === 'decrement') {
      const decrementButton = taskRow.locator('button').filter({ hasText: /-|decrement|less/i });
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

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Filter tasks by completion status
   */
  async filterTasks(options: {
    hideCompleted?: boolean;
    priority?: 'Critical' | 'Normal' | 'Nice-to-have';
    type?: 'Action' | 'Countable';
  } = {}): Promise<void> {
    if (options.hideCompleted !== undefined) {
      const hideCompletedToggle = this.page.locator('input[type="checkbox"]').filter({ hasText: /hide.*completed/i });
      if (await hideCompletedToggle.count() > 0) {
        if (options.hideCompleted) {
          await hideCompletedToggle.check();
        } else {
          await hideCompletedToggle.uncheck();
        }
      }
    }

    if (options.priority) {
      const priorityFilter = this.page.locator('select[name="priority"], select').filter({ hasText: /priority/i });
      if (await priorityFilter.count() > 0) {
        await priorityFilter.selectOption(options.priority);
      }
    }

    if (options.type) {
      const typeFilter = this.page.locator('select[name="type"], select').filter({ hasText: /type/i });
      if (await typeFilter.count() > 0) {
        await typeFilter.selectOption(options.type);
      }
    }

    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Delete a task
   */
  async deleteTask(taskName: string): Promise<void> {
    const taskRow = this.page.locator('.task-item, [data-testid="task-item"]').filter({ hasText: taskName });
    await taskRow.waitFor({ state: 'visible' });

    const deleteButton = taskRow.locator('button').filter({ hasText: /delete|remove|✗/i });
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

    // Look for completion indicators (checkmark, completed class, etc.)
    const completionIndicator = taskRow.locator('.completed, .task-completed, [data-status="completed"]');
    await completionIndicator.waitFor({ state: 'visible' });
  }

  /**
   * Get visible tasks on current page
   */
  async getVisibleTasks(): Promise<string[]> {
    const taskElements = this.page.locator('[data-testid="task-item"], .task-item, .task-card');
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
