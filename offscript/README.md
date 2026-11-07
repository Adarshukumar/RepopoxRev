# Offscript

A responsive social-media **frontend concept**, separate from the existing PRISM Android app. React + Vite + Lucide, with local DM Sans / Manrope fonts. No API keys or external runtime image/font requests.

## Run

```sh
cd offscript
npm ci
npm run dev
```

Development server binds to `0.0.0.0`; Arena preview hosts are allowed. `npm run build` creates the production site in `dist/`.

## Design

Warm ivory, dark olive ink, restrained vermilion actions, sage editorial panels. Photography comes first; persistent desktop navigation becomes bottom navigation on phones. Light/dark themes use shared semantic CSS variables. Native dialogs provide keyboard focus containment and Escape dismissal; reduced-motion preferences are respected.

Research references:
- https://www.designstudiouiux.com/blog/mobile-app-design-examples/ — image-first discovery, visual hierarchy and clear navigation.
- https://www.lyssna.com/blog/app-design-trends/ — purposeful feedback and avoiding dark-pattern interactions.
- https://designmd.app/blog/dark-mode-vs-light-mode-design-md — semantic theme tokens.

These are inspiration references, not copied UI templates.

## Implemented interactions

- Home / Following, Explore, category filtering and live search (Cmd/Ctrl+K).
- Likes, saved collection, following, comments and profile.
- Caption composer with sample photos or a local image upload (3 MB limit).
- Story/photo viewers and daily-prompt composer.
- Local demo inbox, sending messages and sharing posts into a conversation.
- Theme switching. Posts, likes, saves, follows, messages persist in localStorage.

**Scope:** no authentication, backend, real users, moderation, remote uploads, push notifications or message delivery. Notifications and community member counts are sample data. The Sunday Club opens a demo collection, not a real membership flow. Demo posts use fictional identities; captions and location labels are illustrative, not verified photo metadata. Clearing this site's browser storage resets the demo.

## Checks

```sh
npx playwright install chromium
# Keep npm run dev running on port 5173 in another terminal.
npm test
```

The browser smoke test checks feed rendering, likes, saves, comments, image posting, reload persistence, search, theme switching, demo messaging, mobile navigation, image loading, no JavaScript exceptions, and no page overflow at 390 / 768 / 1440 px. Production build and these checks passed in the Arena sandbox. Tests used npm-packaged Chromium because browser-download hosts are blocked here (requires the package's AL2023 libraries extracted under `/tmp/al2023/lib`, `BUNDLED_CHROMIUM=1`, and `LD_LIBRARY_PATH=/tmp/al2023/lib`). No Android APK is produced by this frontend.

## Demo image provenance

Search-result images are stored locally so preview rendering is reliable. They are **reference/demo imagery**, not newly licensed production assets. Confirm original licensing and attribution or replace them with owned photographs before commercial release.

| Local file | Source page |
|---|---|
| coast.jpg | https://theblondescout.com/the-ultimate-guide-to-positano |
| italy.jpg | https://travelreveal.com/destination-guides/guide-to-visiting-positano-italy |
| cafe.jpg, coffee.jpg | https://blisslights.com/blogs/blisslights/coffee-house-lighting-tips |
| lake.jpg | https://unsplash.com/s/photos/lake-mountain |
| mountains.jpg | https://unsplash.com/photos/snowy-mountains-reflected-in-dolomites-lake-YFFGkE3y4F8 |

Fonts are distributed by `@fontsource` packages under their included licenses. Lucide icons are distributed under ISC.
