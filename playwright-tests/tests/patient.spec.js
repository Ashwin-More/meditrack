import { test, expect } from "@playwright/test";
import { users } from "../utils/testData.js";
import { loginAs } from "../utils/auth.js";


test.describe("MediTrack Patient Module", () => {

  // TC04
  test("TC04 - Patient Search", async ({ page }) => {

    await loginAs(page, users.doctor);

    await page.goto("/#/patients");

    await expect(
      page.getByRole("heading", {
        name: "Patients"
      })
    ).toBeVisible();

    const searchBox = page.getByPlaceholder(
      /Sharma, PAT-2026-001, 98765/i
    );

    await expect(searchBox)
      .toBeVisible();

    await searchBox.fill("PAT");

    // Wait for the search result/UI update.
    await expect(page.locator("#view-root"))
      .toContainText(/Patients|No patients found/i);
  });


  // TC05
  test("TC05 - Patient Registration", async ({ page }) => {

    await loginAs(page, users.doctor);

    await page.goto("/#/patients?new=1");

    await expect(
      page.getByRole("heading", {
        name: "Register new patient"
      })
    ).toBeVisible();


    // Generate unique test data.
    const uniqueId = Date.now();

    const firstName = `PW${uniqueId}`;

    const email = `pw${uniqueId}@example.com`;

    const phone =
      `98765${String(uniqueId).slice(-5)}`;


    // First name
    await page
      .getByPlaceholder("Jane")
      .fill(firstName);


    // Last name
    await page
      .getByPlaceholder("Doe")
      .fill("Patient");


    // Gender
    await page
      .getByLabel("Gender")
      .selectOption("MALE");


    // Blood group
    await page
      .getByPlaceholder("e.g. O+")
      .fill("O+");


    // Phone
    await page
      .getByPlaceholder("+1 555 0100")
      .fill(phone);


    // Email
    await page
      .getByPlaceholder("jane@example.com")
      .fill(email);


    // Address
    await page
      .getByPlaceholder("123 Main St")
      .fill("Pune, Maharashtra");


    // Submit
    await page
      .getByRole("button", {
        name: "Register patient"
      })
      .click();


    // Verify success
    await expect(page.locator("#toast"))
      .toContainText(/registered/i);
  });

});