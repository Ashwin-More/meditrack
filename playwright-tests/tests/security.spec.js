import { test, expect } from "@playwright/test";


test.describe("MediTrack Security", () => {

  // TC07A
  test("TC07A - Dashboard requires login", async ({ page }) => {

    await page.goto("/#/login");

    await page.evaluate(() => {
      localStorage.clear();
    });

    await page.goto("/#/dashboard");

    await expect(page.locator("#auth-root"))
      .toBeVisible();

    await expect(page.locator("#app-shell"))
      .toHaveClass(/hidden/);
  });


  // TC07B
  test("TC07B - Patients page requires login", async ({ page }) => {

    await page.goto("/#/login");

    await page.evaluate(() => {
      localStorage.clear();
    });

    await page.goto("/#/patients");

    await expect(page.locator("#auth-root"))
      .toBeVisible();

    await expect(page.locator("#app-shell"))
      .toHaveClass(/hidden/);
  });


  // TC07C
  test("TC07C - No authentication token exists", async ({ page }) => {

    await page.goto("/#/login");

    await page.evaluate(() => {
      localStorage.clear();
    });

    const token = await page.evaluate(() => {
      return localStorage.getItem("meditrack.token");
    });

    const refreshToken = await page.evaluate(() => {
      return localStorage.getItem("meditrack.refreshToken");
    });

    expect(token).toBeNull();

    expect(refreshToken).toBeNull();
  });

});