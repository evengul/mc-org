import {expect, test} from "@playwright/test";
import {getUrl} from "./utils";

test.describe("Sign in", () => {
  test("Has content", async ({page}) => {
    await page.goto(getUrl("MAIN"));

    await expect(page).toHaveTitle(/MC-ORG.*/g)

    const h1 = page.getByText("Welcome to MCORG!")
    await expect(h1).toBeVisible()

    const button = page.getByText("Sign in with Microsoft")
    await expect(button).toBeVisible()
  })

  test("Sign in adds token", async ({page, browser}) => {
    const [context] = browser.contexts()
    await page.goto(getUrl("MAIN"));

    const button = page.getByText("Sign in with Microsoft")
    await button.click()

    await expect(page).toHaveTitle("MC-ORG | Projects")
    const h1 = page.getByRole("heading", {name: "Projects"})
    await expect(h1).toBeVisible()
    const cookies = await context.cookies()

    const cookie = cookies.find(cookie => cookie.name === "MCORG-USER-TOKEN")
    expect(cookie).toBeDefined()
    expect(cookie.value).toBeDefined()
    expect(cookie.value.length).toBeGreaterThan(0)
    expect(cookie.expires).toBeGreaterThan(-1)
  })
})