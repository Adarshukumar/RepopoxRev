import { mkdirSync } from 'node:fs';
mkdirSync('test-results', { recursive: true });
import { chromium } from "playwright";
import chromiumBinary from "@sparticuz/chromium";
import assert from "node:assert/strict";
// Standard Playwright browser on developer machines; bundled browser in this sandbox.
const browser = await chromium.launch({
  ...(process.env.BUNDLED_CHROMIUM
    ? {
        executablePath: await chromiumBinary.executablePath(),
        args: chromiumBinary.args,
      }
    : {}),
  headless: true,
});
const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } });
const errors = [];
page.on("pageerror", (e) => errors.push(e.message));
await page.goto("http://localhost:5173");
await page.waitForSelector(".post");
await page.evaluate(() => document.fonts.ready);
await page.screenshot({
  path: "test-results/desktop.png",
  fullPage: true,
});
assert.equal(await page.locator(".post").count(), 4);
await page.getByRole("button", { name: "Like post 1", exact: true }).click();
assert.equal(
  await page
    .getByRole("button", { name: "Like post 1", exact: true })
    .getAttribute("aria-pressed"),
  "true",
);
await page.getByRole("button", { name: "Save post by Sofia Chen" }).click();
await page
  .locator(".sidebar")
  .getByRole("button", { name: "Saved", exact: true })
  .click();
assert.equal(await page.locator(".post").count(), 1);
await page
  .locator(".sidebar")
  .getByRole("button", { name: "Home", exact: true })
  .click();
await page.getByRole("button", { name: "Comments on post 1" }).click();
await page
  .getByRole("textbox", { name: "Comment", exact: true })
  .fill("What a lovely moment");
await page.locator(".comments-modal form button").click();
assert.equal(
  await page.getByText("What a lovely moment", { exact: true }).count(),
  1,
);
await page.getByRole("button", { name: "Close dialog" }).click();
await page.getByRole("button", { name: "Create a post", exact: true }).click();
await page
  .getByRole("textbox", { name: "Post caption" })
  .fill("A test moment from Lucknow");
await page.getByRole("button", { name: "Attach cafe photo" }).click();
await page.getByRole("button", { name: "Share moment", exact: true }).click();
assert.equal(await page.locator(".post").count(), 5);
await page.reload();
await page.waitForSelector(".post");
assert.equal(await page.locator(".post").count(), 5);
await page.getByRole("textbox", { name: "Search moments" }).fill("Lucknow");
assert.equal(await page.locator(".post").count(), 1);
await page.getByRole("textbox", { name: "Search moments" }).fill("");
await page.getByRole("button", { name: "Dark appearance" }).click();
assert.equal(await page.locator("html").getAttribute("data-theme"), "dark");
await page.getByRole("button", { name: "Light appearance" }).click();
await page
  .locator(".sidebar")
  .getByRole("button", { name: "Messages 2" })
  .click();
await page
  .getByRole("textbox", { name: "Message", exact: true })
  .fill("Hello Sofia");
await page.getByRole("button", { name: "Send message", exact: true }).click();
assert.equal(await page.getByText("Hello Sofia", { exact: true }).count(), 1);
await page
  .locator(".sidebar")
  .getByRole("button", { name: "Home", exact: true })
  .click();
for (const width of [390, 768, 1440]) {
  await page.setViewportSize({ width, height: 844 });
  assert.equal(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
    true,
    `Overflow at ${width}`,
  );
}
await page.setViewportSize({ width: 390, height: 844 });
await page.getByRole("textbox", { name: "Search moments" }).fill("Lucknow");
assert.equal(await page.locator(".post").count(), 1);
await page.getByRole("textbox", { name: "Search moments" }).fill("");
await page
  .locator(".mobile-nav")
  .getByRole("button", { name: "Explore", exact: true })
  .click();
assert.equal(await page.locator("h1").innerText(), "Follow your curiosity.");
await page
  .locator(".mobile-nav")
  .getByRole("button", { name: "Home", exact: true })
  .click();
await page.screenshot({
  path: "test-results/mobile.png",
  fullPage: true,
});
assert.deepEqual(errors, []);
assert.equal(
  await page
    .locator("img")
    .evaluateAll((imgs) => imgs.every((i) => i.complete && i.naturalWidth > 0)),
  true,
);
console.log(
  "PASS: feed, likes, saves, comments, photo composer, persistence, search, theme, messaging, responsive widths, image loading, zero runtime errors",
);
await browser.close();
