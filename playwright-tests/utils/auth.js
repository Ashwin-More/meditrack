import { expect } from "@playwright/test";

export async function loginAs(page, user) {
  await page.goto("/#/login");

  await page.getByRole("button", {
    name: /Staff Login/i
  }).click();

  await page.locator("#login-username").fill(user.username);

  await page.locator("#login-password").fill(user.password);

  await page.getByRole("button", {
    name: "Log in"
  }).click();

  await expect(page.locator("#app-shell")).toBeVisible();
}