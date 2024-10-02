import {expect, test} from "@playwright/test";
import {createProject, signInCreateWorldAndGoTo} from "./utils";
import {locators} from "./locators";

test.describe("Projects page", () => {
  test("Has content", async ({page}) => {
    await signInCreateWorldAndGoTo(page, "MAIN")

    await expect(page).toHaveTitle("MC-ORG | Projects")
    const {menuButton, PROJECTS} = locators(page)
    await expect(PROJECTS.title).toBeVisible()
    await expect(menuButton).toBeVisible()
    await expect(PROJECTS.FILTER.self).not.toBeVisible()
    await expect(PROJECTS.CREATE_DIALOG.self).not.toBeVisible()
    await expect(PROJECTS.showCreateProjectDialogButton).toBeVisible()
  })

  test("Create project", async ({page}) => {
    const projectName = "Some project!"

    await signInCreateWorldAndGoTo(page, "MAIN")

    const {
      showCreateProjectDialogButton,
      CREATE_DIALOG,
      project
    } = locators(page).PROJECTS

    await showCreateProjectDialogButton.click()
    await expect(CREATE_DIALOG.self).toBeVisible()

    await CREATE_DIALOG.name.fill(projectName)
    await CREATE_DIALOG.dimension.selectOption("THE_END")
    await CREATE_DIALOG.priority.selectOption("HIGH")
    if (await CREATE_DIALOG.requiresPerimeter.isVisible()) {
      await CREATE_DIALOG.requiresPerimeter.check()
    }

    await CREATE_DIALOG.submitButton.click()

    const {self: projectNameLocator, dimension, priority, progress} = project(projectName)
    await expect(projectNameLocator).toBeVisible()

    const [dimensionContent] = await dimension.allTextContents()
    expect(dimensionContent).toContain("Dimension: The End")

    const [priorityContent] = await priority.allTextContents()
    expect(priorityContent).toContain("Priority: High")

    const [progressContent] = await progress.allTextContents()
    expect(progressContent).toContain("0%")
  })

  test("Filter projects by search", async ({page}) => {
    await signInCreateWorldAndGoTo(page, "MAIN")

    const project1 = await createProject(page, "Project 1")
    const project2 = await createProject(page, "Project 2")

    const {
      FILTER: {
        search,
        submitButton
      },
      amountShowed,
      project
    } = locators(page).PROJECTS

    await search.fill("Project 1")
    await submitButton.click()

    await expect(amountShowed).toContainText("Showing 1 of 2 projects")
    await expect(project(project1).self).toBeVisible()
    await expect(project(project2).self).not.toBeVisible()
  })

  test("Delete project", async ({page}) => {
    await signInCreateWorldAndGoTo(page, "MAIN")

    const projectId = await createProject(page)

    const {
      project,
      FILTER: {
        self: filter
      },
      EMPTY_STATE: {
        self: emptyState
      }
    } = locators(page).PROJECTS

    const {
      deleteButton,
      self: projectElement
    } = project(projectId)

    page.on("dialog", dialog => dialog.accept())

    await deleteButton.click({
      force: true
    })

    await expect(projectElement).not.toBeVisible()
    await expect(emptyState).toBeVisible()
    await expect(filter).not.toBeVisible()
  })

  test("Assign self to project", async ({page}) => {
    await signInCreateWorldAndGoTo(page, "MAIN")

    const projectId = await createProject(page)

    const {
      assignment
    } = locators(page).PROJECTS.project(projectId)

    await assignment.selectOption({
      index: 1
    })

    await expect(assignment.locator("[selected=\"selected\"]")).toContainText(/Assigned: TestUser_[0-9]+/)
  })
})