import { test, expect } from "@playwright/test";
import { users } from "../utils/testData.js";
import { loginAs } from "../utils/auth.js";


test.describe("MediTrack Authentication", () => {

  // TC01
  test("TC01 - Valid Doctor Login", async ({ page }) => {

    await loginAs(page, users.doctor);

    await expect(page.locator("#app-shell"))
      .toBeVisible();

    await expect(page.locator("#topbar-role"))
      .toContainText("DOCTOR");
  });


  // TC02
  test("TC02 - Invalid Login", async ({ page }) => {

    await page.goto("/#/login");

    await page.getByRole("button", {
      name: /Staff Login/i
    }).click();

    await page.locator("#login-username")
      .fill("wronguser");

    await page.locator("#login-password")
      .fill("WrongPassword123");

    await page.getByRole("button", {
      name: "Log in"
    }).click();

    await expect(page.locator("#auth-root"))
      .toBeVisible();

    await expect(page.locator("#app-shell"))
      .toHaveClass(/hidden/);
  });


  // TC03
  test("TC03 - Login Validation", async ({ page }) => {

    await page.goto("/#/login");

    await page.getByRole("button", {
      name: /Staff Login/i
    }).click();

    await page.getByRole("button", {
      name: "Log in"
    }).click();

    await expect(page.locator("#login-username"))
      .toBeVisible();

    await expect(page.locator("#login-password"))
      .toBeVisible();

    await expect(page.locator("#auth-root"))
      .toBeVisible();
  });


  // TC08
  test("TC08 - Logout and Session Clearing", async ({ page }) => {

    await loginAs(page, users.doctor);

    await expect(page.locator("#app-shell"))
      .toBeVisible();

    await page.locator("#logout-btn").click();

    await expect(page.locator("#auth-root"))
      .toBeVisible();

    const token = await page.evaluate(() => {
      return localStorage.getItem("meditrack.token");
    });

    const refreshToken = await page.evaluate(() => {
      return localStorage.getItem("meditrack.refreshToken");
    });

    const user = await page.evaluate(() => {
      return localStorage.getItem("meditrack.user");
    });

    expect(token).toBeNull();

    expect(refreshToken).toBeNull();

    expect(user).toBeNull();
  });

});