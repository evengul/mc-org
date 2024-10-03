import {expect, test} from "@playwright/test";
import {signInAndGoTo} from "./utils";
import {locators} from "./locators";

test.describe("When user has no worlds on sign in", () => {
  test("They create a new world", async ({page}) => {
    await signInAndGoTo(page, "MAIN")

    await expect(page).toHaveTitle("MC-ORG | Worlds")

    const {
      title,
      CREATE_WORLD: {name, submitButton}
    } = locators(page).WORLDS

    await expect(title).toBeVisible()
    await expect(name).toBeVisible()
    await expect(submitButton).toBeVisible()

    await name.fill("World")
    await submitButton.click()

    await page.waitForLoadState("domcontentloaded")

    await expect(page).toHaveTitle("MC-ORG | Projects")
  })
})