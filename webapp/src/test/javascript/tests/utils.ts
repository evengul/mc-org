import {expect, Page} from "@playwright/test";
import {locators} from "./locators";
import {randomUUID} from "node:crypto";

type ENVIRONMENT = "LOCAL" | "TEST" | "PRODUCTION"

const getCurrentEnvironment = (): ENVIRONMENT => process.env.ENV as ENVIRONMENT ?? "LOCAL"

const testUrl = "http://localhost:8080"

const urls: Record<ENVIRONMENT, string> = {
  LOCAL: "http://localhost:8080",
  TEST: stripEndingSlash(process.env.FLY_DEV_URL ?? testUrl),
  PRODUCTION: "https://mcorg.app"
}

function stripEndingSlash(str: string) {
  return str.replace(/\/$/, "")
}

const pages = {
  MAIN: "/",
  SIGN_IN: "/auth/sign-in",
  WORLDS: "/app",
  PROFILE: "/app/profile"
}

export const getUrl = (page: keyof typeof pages) => urls[getCurrentEnvironment()] + pages[page]

export const signInAndGoTo = async (page: Page, route: keyof typeof pages) => {
  await page.goto(getUrl("SIGN_IN"))
  const button = locators(page).SIGN_IN.button
  await button.click()
  await page.goto(getUrl(route))
}

export const signInCreateWorldAndGoTo = async (page: Page, route: keyof typeof pages) => {
  await signInAndGoTo(page, "WORLDS")

  const {
    CREATE_WORLD: {name, submitButton}
  } = locators(page).WORLDS

  await name.fill(`World ${randomUUID()}`)
  await submitButton.click()

  await expect(page).toHaveTitle("MC-ORG | Projects")

  await page.goto(getUrl(route))
}

export const signInCreateWorldAndProject = async (page: Page, projectName = "Project") => {
  await signInCreateWorldAndGoTo(page, "MAIN")

  const {
    EMPTY_STATE: {createButton}
  } = locators(page).PROJECTS

  await createButton.click()

  return await createProjectInForm(page, projectName)
}

export const createProject = async (page: Page, projectName = "Project") => {
  const {
    showCreateProjectDialogButton
  } = locators(page).PROJECTS

  await showCreateProjectDialogButton.click()

  await createProjectInForm(page, projectName)

  return projectName
}

const createProjectInForm = async (page: Page, projectName = "Project") => {
  const {
    project,
    CREATE_DIALOG: {
      name,
      dimension,
      priority,
      submitButton
    }
  } = locators(page).PROJECTS

  await name.fill(projectName)
  await dimension.selectOption("OVERWORLD")
  await priority.selectOption("HIGH")
  await submitButton.click()

  await expect(project(projectName).self).toBeVisible()

  return projectName
}

export const createDoableTask = async (page: Page, taskName: string) => {
  const {
    createDoableButton,
    ADD_DOABLE: {
      nameField,
      submitButton
    }
  } = locators(page).PROJECT.DOABLE

  await createDoableButton.click()

  await nameField.fill(taskName)
  await submitButton.click()

  return taskName
}

export const createCountableTask = async (page: Page, taskName: string, amount: number) => {
  const {
    createCountableButton,
    ADD_COUNTABLE: {
      nameField,
      amountField,
      submitButton
    }
  } = locators(page).PROJECT.COUNTABLE

  await createCountableButton.click()

  await nameField.fill(taskName)
  await amountField.fill(amount.toString())
  await submitButton.click()

  return [taskName, amount] as const
}





