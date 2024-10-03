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
  SIGN_IN: "/auth/sign-in?test=true"
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
  await signInAndGoTo(page, "MAIN")

  const {
    CREATE_WORLD: {name, submitButton}
  } = locators(page).WORLDS

  await name.fill("World")
  await submitButton.click()

  await page.waitForLoadState("domcontentloaded")

  await expect(page).toHaveTitle("MC-ORG | Projects")
}





