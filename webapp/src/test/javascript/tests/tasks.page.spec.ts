import {expect, test} from "@playwright/test";
import {createCountableTask, createDoableTask, getUrl, signInCreateWorldAndProject} from "./utils";
import {locators} from "./locators";

test.beforeEach(async ({page}) => {
  const projectName = await signInCreateWorldAndProject(page)
  const projectHeader = locators(page).PROJECTS.project(projectName).header
  await projectHeader.click()
})

test.describe("General project page", () => {
  test("Has initial content", async ({page}) => {
    const {
      taskBoard,
      DOABLE: {
        board: doableBoard,
        header: doableHeader,
        columns: doableColumns,
        hideDoableTasksButton
      },
      COUNTABLE: {
        board: countableBoard,
        header: countableHeader,
        columns: countableColumns,
        hideCountableTasksButton
      }
    } = locators(page).PROJECT

    await expect(taskBoard).toBeVisible()

    await expect(doableBoard).toBeVisible()
    await expect(doableHeader).toBeVisible()
    await expect(doableColumns).toBeVisible()

    await expect(countableBoard).toBeVisible()
    await expect(countableHeader).toBeVisible()
    await expect(countableColumns).toBeVisible()

    await hideDoableTasksButton.click()
    await hideCountableTasksButton.click()

    await expect(doableColumns).not.toBeVisible()
    await expect(doableHeader).toBeVisible()
    await expect(countableColumns).not.toBeVisible()
    await expect(countableHeader).toBeVisible()
  })

  test.skip("Can move recently created task to In Progress", async () => {

  })

  test.skip("Can move finished task to Done", async () => {

  })

  test.skip("Can move non-finished task back to in progress", async () => {

  })

  test.skip("Can move no longer in-progress task back to To Do", async () => {

  })

  test.skip("Can move finished task back to To Do", async () => {

  })

  test.skip("Can move recently created tasks straight to Done", () => {

  })

  test.skip("Completed tasks are styled differently", ({page}) => {

  })
})

test.describe("Doable tasks", () => {
  test("Create a doable task", async ({page}) => {
    const taskName = await createDoableTask(page, "Some doable task")

    const taskElement = locators(page).PROJECT.DOABLE.task(taskName).self

    await expect(taskElement).toBeVisible()
  })

  test("Delete a doable task", async ({page}) => {
    const taskName = await createDoableTask(page, "Some doable task")

    page.on("dialog", dialog => dialog.accept())

    const {
      self: taskElement,
      deleteButton
    } = locators(page).PROJECT.DOABLE.task(taskName)

    await deleteButton.click()
    await expect(taskElement).not.toBeVisible()
  })

  test("Assign a doable task", async ({page}) => {
    const taskName = await createDoableTask(page, "Some doable task")

    const {
      assign
    } = locators(page).PROJECT.DOABLE.task(taskName)

    await expect(assign).toBeVisible()

    await page.goto(getUrl("PROFILE"))

    const usernameElement = page.getByText(/Username: TestUser_[0-9]+/)
    const username = (await usernameElement.textContent()).replace("Username: ", "")

    await page.locator("#menu-button").click()
    await page.getByRole("heading", { name: "Projects", level: 2}).click()
    await page.getByRole("heading", { name: "Project", level: 2 }).click()

    await assign.selectOption(username)

    await expect(assign.locator("[selected=\"selected\"]")).toContainText(/Assigned: TestUser_[0-9]+/)
  })

  test("Can not move a doable task to a countable column", async () => {

  })
})

test.describe("Countable tasks", () => {
  test("Create a countable task", async ({page}) => {
    const [taskName, amount] = await createCountableTask(page, "Some countable task", 50)

    const {
      amount: amountElement,
      assign
    } = locators(page).PROJECT.COUNTABLE.task(taskName)

    await expect(amountElement.locator(".task-progress-prefix")).not.toBeVisible()
    await expect(amountElement).toContainText(`${amount} items`)
    await expect(assign).toBeVisible()
  })

  test("Delete a countable task", async ({page}) => {
    const [taskName] = await createCountableTask(page, "Some countable task", 50)

    page.on("dialog", dialog => dialog.accept())

    const {
      self: taskElement,
      deleteButton
    } = locators(page).PROJECT.COUNTABLE.task(taskName)

    await deleteButton.click()
    await expect(taskElement).not.toBeVisible()
  })

  test("Assign a countable task", async ({page}) => {
    const [taskName] = await createCountableTask(page, "Some countable task", 50)

    const {
      assign
    } = locators(page).PROJECT.COUNTABLE.task(taskName)

    await expect(assign).toBeVisible()

    await page.goto(getUrl("PROFILE"))

    const usernameElement = page.getByText(/Username: TestUser_[0-9]+/)
    const username = (await usernameElement.textContent()).replace("Username: ", "")

    await page.locator("#menu-button").click()
    await page.getByRole("heading", { name: "Projects", level: 2}).click()
    await page.getByRole("heading", { name: "Project", level: 2 }).click()

    await assign.selectOption(username)

    await expect(assign.locator("[selected=\"selected\"]")).toContainText(/Assigned: TestUser_[0-9]+/)
  })

  test.skip("Larger countable tasks have a correct visualisation", async ({page}) => {

  })

  test.skip("Can add a stack to a countable task", async ({page}) => {

  })

  test.skip("Can add a shulker box to a countable task", async ({page}) => {

  })

  test.skip("Can edit a countable task", async ({page}) => {

  })

  test.skip("Tasks with all items gathered have disabled add buttons", async ({page}) => {

  })

  test.skip("Can not move a countable task to a doable column", async ({page}) => {

  })
})