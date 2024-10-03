import {expect, test} from "@playwright/test";
import {signInCreateWorldAndGoTo} from "./utils";
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
})