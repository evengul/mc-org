import {expect, test} from "@playwright/test";
import {signInAndGoTo, signInCreateWorldAndProject} from "./utils";
import {locators} from "./locators";
import {randomUUID} from "node:crypto";

test.describe("When user has no worlds on sign in", () => {
  test("They create a new world", async ({page}) => {
    await signInAndGoTo(page, "MAIN")

    await expect(page).toHaveTitle("MC-ORG | Worlds")

    const {
      title,
      CREATE_WORLD: {name, submitButton}
    } = locators(page).WORLDS

    const {
      EMPTY_STATE: {
        self: emptyState,
        text,
        createButton
      },
      FILTER: {
        self: filter
      },
      showCreateProjectDialogButton
    } = locators(page).PROJECTS

    await expect(title).toBeVisible()
    await expect(name).toBeVisible()
    await expect(submitButton).toBeVisible()

    await name.fill(`World ${randomUUID()}`)
    await submitButton.click()

    await expect(page).toHaveTitle("MC-ORG | Projects")
    await expect(showCreateProjectDialogButton).toBeVisible()
    await expect(emptyState).toBeVisible()
    await expect(text).toBeVisible()
    await expect(createButton).toBeVisible()
    await expect(filter).not.toBeVisible()
  })

  test("They create a new project", async ({page}) => {
    const projectName = "First project"
    await signInCreateWorldAndProject(page, projectName)

    const {
      project,
      FILTER: {
        self: filter
      }
    } = locators(page).PROJECTS

    const {
      self: projectElement,
      deleteButton,
      priority,
      dimension,
      assignment,
      progress
    } = project(projectName)

    await expect(projectElement).toBeVisible()
    await expect(filter).toBeVisible()
    await expect(deleteButton).toBeVisible()
    await expect(priority).toBeVisible()
    await expect(dimension).toBeVisible()
    await expect(assignment).toBeVisible()
    await expect(progress).toBeVisible()

    await expect(priority).toContainText("High")
    await expect(dimension).toContainText("Overworld")
    await expect(assignment).toContainText("Unassigned")
  })
})