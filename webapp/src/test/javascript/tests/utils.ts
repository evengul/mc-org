import {expect, Page} from "@playwright/test";
import {locators} from "./locators";

type ENVIRONMENT = "LOCAL" | "PRODUCTION"

const getCurrentEnvironment = (): ENVIRONMENT => "LOCAL"

const urls: Record<ENVIRONMENT, string> = {
  LOCAL: "http://localhost:8080",
  PRODUCTION: "https://mcorg.app"
}

const pages = {
  MAIN: "/",
  SIGN_IN: "/auth/sign-in",
  WORLDS: "/app/worlds",
}

export const getUrl = (page: keyof typeof pages) => urls[getCurrentEnvironment()] + pages[page]

export const signInAndGoTo = async (page: Page, route: keyof typeof pages) => {
  await page.goto(getUrl("SIGN_IN"))
  const button = locators(page).SIGN_IN.button
  await button.click()
  await page.waitForLoadState("domcontentloaded")
  await page.goto(getUrl(route))
  await page.waitForLoadState("domcontentloaded")
}

export const signInCreateWorldAndGoTo = async (page: Page, route: keyof typeof pages) => {
  await signInAndGoTo(page, "WORLDS")

  const {
    CREATE_WORLD: {name, submitButton}
  } = locators(page).WORLDS

  await name.fill("World")
  await submitButton.click()

  await page.waitForLoadState("domcontentloaded")

  await expect(page).toHaveTitle("MC-ORG | Projects")

  await page.goto(getUrl(route))
}

export const signInCreateWorldAndProject = async (page: Page, projectName = "Project") => {
  await signInCreateWorldAndGoTo(page, "WORLDS")

  const {
    EMPTY_STATE: {createButton}
  } = locators(page).PROJECTS

  await createButton.click()

  const {
    CREATE_DIALOG: {
      name,
      dimension,
      priority,
      submitButton
    }
  } = locators(page).PROJECTS

  await name.fill(projectName)
  await dimension.fill("OVERWORLD")
  await priority.fill("HIGH")
  await submitButton.click()

  await page.waitForLoadState("domcontentloaded")
}





