import { test, expect } from '@playwright/test';
import { AuthHelpers, WorldHelpers, ProjectHelpers, TestDataGenerator, TestSetup, ProjectData } from '../utils';

test.describe('Project Creation', () => {
  let authHelpers: AuthHelpers;
  let worldHelpers: WorldHelpers;
  let projectHelpers: ProjectHelpers;
  let testWorldName: string;

  test.beforeEach(async ({ page }) => {
    authHelpers = new AuthHelpers(page);
    worldHelpers = new WorldHelpers(page);
    projectHelpers = new ProjectHelpers(page);

    // Authenticate and create a test world for projects
    await authHelpers.signInAsTestUser();
    testWorldName = TestDataGenerator.generateTestWorldName();
    await worldHelpers.createWorld({ name: testWorldName });
    await worldHelpers.navigateToWorld(testWorldName);
    await TestSetup.waitForPageLoad(page);
  });

  test('should create project with minimal required information', async ({ page }) => {
    const projectName = TestDataGenerator.generateTestProjectName();

    const projectData = await projectHelpers.createProject({
      name: projectName
    });

    expect(projectData.name).toBe(projectName);
    expect(projectData.description).toBeDefined();
    expect(projectData.type).toBeDefined();

    await projectHelpers.verifyProjectExists(projectName);
  });

  test('should create project with detailed information', async ({ page }) => {
    const projectData: ProjectData = {
      name: TestDataGenerator.generateTestProjectName(),
      description: 'A comprehensive test project with all fields filled out for testing purposes',
      type: 'DECORATION',
    };

    const createdProject = await projectHelpers.createProject(projectData);

    expect(createdProject.name).toBe(projectData.name);
    expect(createdProject.description).toBe(projectData.description);
    expect(createdProject.type).toBe(projectData.type);

    await projectHelpers.verifyProjectExists(projectData.name);
  });

  test('should create multiple projects in same world', async ({ page }) => {
    const project1Name = TestDataGenerator.generateTestProjectName();
    const project2Name = TestDataGenerator.generateTestProjectName();
    const project3Name = TestDataGenerator.generateTestProjectName();

    // Create multiple projects
    await projectHelpers.createProject({ name: project1Name, type: 'TECHNICAL' });
    await projectHelpers.createProject({ name: project2Name, type: 'DECORATION' });
    await projectHelpers.createProject({ name: project3Name, type: 'FARMING' });

    // All projects should be visible
    await projectHelpers.verifyProjectExists(project1Name);
    await projectHelpers.verifyProjectExists(project2Name);
    await projectHelpers.verifyProjectExists(project3Name);

    // Verify project count
    const visibleProjects = await projectHelpers.getVisibleProjects();
    expect(visibleProjects.length).toBeGreaterThanOrEqual(3);
  });

  test("Should create project with different types", async ({ page }) => {
    const projectTypes = ['BUILD', 'REDSTONE', 'FARMING', 'EXPLORATION', 'DECORATION', 'OTHER'];

    for (const type of projectTypes) {
      const projectName = `${type}-Project-${TestDataGenerator.generateTestProjectName()}`;
      const projectData: ProjectData = { name: projectName, type: type as ProjectData['type'] };

      const createdProject = await projectHelpers.createProject(projectData);
      expect(createdProject.type).toBe(type);
      await projectHelpers.verifyProjectExists(projectName);
    }
  })

  test('should start projects in Planning stage by default', async ({ page }) => {
    const projectName = TestDataGenerator.generateTestProjectName();

    await projectHelpers.createProject({ name: projectName });

    // Navigate to project to check stage
    await projectHelpers.navigateToProject(projectName);

    // Should show Planning stage (this test may need adjustment based on UI)
    await expect(page.locator('body')).toContainText(/planning/i);
  });
});
