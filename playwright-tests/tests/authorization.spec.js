import { test, expect } from "@playwright/test";
import { users } from "../utils/testData.js";
import { loginAs } from "../utils/auth.js";


test.describe("MediTrack Role-Based Authorization", () => {

  // TC06A
  test("TC06A - Doctor should not see Admin navigation", async ({ page }) => {

    await loginAs(page, users.doctor);

    const adminLinks = page.locator(".role-admin");

    await expect(adminLinks.first())
      .toBeHidden();
  });


  // TC06B
  test("TC06B - Nurse should not see Admin navigation", async ({ page }) => {

    await loginAs(page, users.nurse);

    const adminLinks = page.locator(".role-admin");

    await expect(adminLinks.first())
      .toBeHidden();
  });


  // TC06C
  test("TC06C - Admin should see Admin navigation", async ({ page }) => {

    await loginAs(page, users.admin);

    const adminLinks = page.locator(".role-admin");

    await expect(adminLinks.first())
      .toBeVisible();
  });


  // TC06D
  test("TC06D - Super Admin should see Admin navigation", async ({ page }) => {

    await page.goto("/#/login");

    await page.getByRole("button", {
      name: /Super Admin Login/i
    }).click();

    await page.locator("#login-username")
      .fill(users.superAdmin.username);

    await page.locator("#login-password")
      .fill(users.superAdmin.password);

    await page.getByRole("button", {
      name: "Log in"
    }).click();

    await expect(page.locator("#app-shell"))
      .toBeVisible();

    await expect(page.locator("#topbar-role"))
      .toContainText("SUPER_ADMIN");

    const adminLinks = page.locator(".role-admin");

    await expect(adminLinks.first())
      .toBeVisible();
  });

});