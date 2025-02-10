import {expect, test} from "@playwright/test";
import {getUrl, signInAndGoTo} from "./utils";
import {locators} from "./locators";

test.describe("Sign in", () => {
  test("Has content", async ({page}) => {
    await page.goto(getUrl("MAIN"));

    await expect(page).toHaveTitle(/MC-ORG.*/g)

    const {title, button} = locators(page).SIGN_IN

    await expect(title).toBeVisible()
    await expect(button).toBeVisible()
  })

  test("Sign in adds token", async ({page, browser}) => {
    const [context] = browser.contexts()
    await context.clearCookies()
    await signInAndGoTo(page, "MAIN")

    const cookies = await context.cookies()

    const cookie = cookies.find(cookie => cookie.name === "MCORG-USER-TOKEN")
    expect(cookie).toBeDefined()
    expect(cookie.value).toBeDefined()
    expect(cookie.value.length).toBeGreaterThan(0)
    expect(cookie.expires).toBeGreaterThan(-1)
  })
})