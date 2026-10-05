import { defineConfig, devices } from "@playwright/test";
import dotenv from "dotenv";

dotenv.config();

export default defineConfig({
  testDir: "./tests",

  timeout: 30 * 1000,

  expect: {
    timeout: 5 * 1000
  },

  fullyParallel: false,

  workers: 1,

  forbidOnly: !!process.env.CI,

  retries: process.env.CI ? 2 : 0,

  reporter: [
    ["list"],
    ["html", { open: "never" }]
  ],

  use: {
    baseURL: process.env.BASE_URL || "http://localhost:5500",

    headless: true,

    screenshot: "only-on-failure",

    video: "retain-on-failure",

    trace: "retain-on-failure",

    actionTimeout: 10 * 1000,

    navigationTimeout: 15 * 1000
  },

  projects: [
    {
      name: "chromium",

      use: {
        ...devices["Desktop Chrome"]
      }
    }
  ]
});