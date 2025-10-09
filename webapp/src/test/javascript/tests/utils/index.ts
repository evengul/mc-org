/**
 * Test utilities index - exports all helper classes
 */
export { AuthHelpers } from './auth-helpers';
export { WorldHelpers } from './world-helpers';
export { ProjectHelpers, type ProjectData } from './project-helpers';
export { TaskHelpers, type TaskData } from './task-helpers';

/**
 * Common test data generators
 */
export class TestDataGenerator {
  static generateUniqueId(): string {
    return `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  static generateTestWorldName(): string {
    return `Test World ${this.generateUniqueId()}`;
  }

  static generateTestProjectName(): string {
    return `Test Project ${this.generateUniqueId()}`;
  }

  static generateTestTaskName(): string {
    return `Test Task ${this.generateUniqueId()}`;
  }
}

/**
 * Common test setup utilities
 */
export class TestSetup {
  /**
   * Wait for page to be fully loaded
   */
  static async waitForPageLoad(page: any): Promise<void> {
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500); // Additional buffer for dynamic content
  }

  /**
   * Generate test timestamp for unique naming
   */
  static getTestTimestamp(): string {
    return new Date().toISOString().replace(/[:.]/g, '-');
  }
}
