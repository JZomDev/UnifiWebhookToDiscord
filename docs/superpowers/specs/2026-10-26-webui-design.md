# Web UI for configuration: design

## Intent

Today the app is configured entirely through environment variables: `webhook`, `port`, `UUID_1`, `UUID_2`, plus one variable per camera where the *name* is the camera's MAC address and the *value* is its display name. On Unraid this means editing the container template and restarting for every new camera, which is obtuse. Add a small password-protected web UI where users manage this configuration through simple forms. Adding a camera should be one line: MAC and name.

## Goals

- Light, server-rendered web UI inside the existing Spring Boot app. No JS framework, minimal CSS, follows the system light/dark theme.
- Manage everything currently set by env vars: Discord webhook, webhook listen port, Console UUID 1 and 2, and the MAC to name camera list.
- Adding a camera is a single inline row (MAC, name, Add).
- Form login with a session cookie. First login is `admin`/`admin`, which works once and forces a password change before anything else is reachable.
- Existing env var users keep working: env values are imported into the saved settings on startup (see Migration).
- A "Send test message" button next to the Discord webhook field in Settings that posts a message through the webhook so users can confirm it works.
- A Guide page rendering `guides/setup.md` (with screenshots) from the repo, bundled into the image.

## Non-goals

- Multi-user accounts, roles, password reset flows, HTTPS termination (users front it with their own reverse proxy if they want TLS).
- Per-camera options. A camera is only MAC + name; thumbnails are controlled on the UniFi side.
- Changing the web UI port from within the UI.
- Rewriting the existing webhook/Discord forwarding logic beyond reading config from the new store.

## Anti-goals

- Heavy dependencies or a big image-size increase. Do not add a JS build toolchain or SPA framework.
- A stale env var silently overwriting a value the user edited in the UI.
- Locking the user out (e.g. via a port change they cannot recover from).

## Decisions (made with the user)

- **Two ports.** Web UI on **45462** (default, fixed in the UI). Webhook listener for UniFi keeps default **8080** and is editable in the UI (user's own setup uses 7019, just a saved value). Changing the listener port should rebind the listener without a full restart; the UI warns that the container port mapping must match.
- **Login.** Form login, session cookie, logout link. Default credentials `admin`/`admin`, one-time; forced password change on first login.
- **Migration.** On startup the app reads the env vars and imports values into saved settings. If any imported/present values came from env vars, every page shows a banner at the top naming exactly which variables (e.g. `webhook`, `UUID_1`, each MAC variable) and saying they are no longer needed and can be removed. The banner can be dismissed but returns on every page load until the user turns off the "env variable reminder" toggle in Settings.
- **Pages.** Cameras (home), Guide, Settings, plus login and force-change-password screens. Layout per the approved mockup (mockup: `2026-10-26-webui-mockup.html` in this folder; it predates the Guide page, which adds a nav item between Cameras and Settings; nav: Cameras, Guide, Settings, Log out; Settings holds webhook, listen port, UUIDs, reminder toggle, change password).
- **Guide.** Renders `guides/setup.md` and its screenshots, bundled into the Docker image at build time, behind the login. The file is written later by the user; the page must degrade gracefully (friendly message) if it is missing.

## Builder's calls (my guesses, not agreed; please check)

- Stay on Java 17 / Spring Boot, server-rendered HTML (Thymeleaf or hand-built strings, builder's choice), small markdown library (e.g. commonmark-java) for the guide.
- Settings persisted as JSON in a mounted `/config` volume (new Unraid path mapping); password stored hashed (BCrypt or similar), never plaintext.
- Env imports only fill values the saved settings do not already have; once edited in the UI the saved value wins. The banner lists only variables that are *currently set* in the container, so it disappears once they are removed.
- MAC addresses are normalized (uppercase, colon-separated) on save and on import. The current lookup uppercases the incoming MAC but stores env names as-is, so lowercase env names never match; normalizing fixes this.
- Validation: MAC must match the existing MAC regex, webhook must match the existing Discord URL regex, port 1-65535 and different from the UI port.
- If no Discord webhook is configured the app still starts (today it refuses to start) so the user can reach the UI and set it; forwarding is skipped with a clear warning until set.
- CSRF protection on forms; the existing `POST /data` webhook endpoint stays unauthenticated on the listener port only and the UI is not served on that port.
- The UI port can be overridden via env (container-level) but not in the UI.

### More builder's calls (from the review round; mine, please check)

- **Import is one-time per value.** The settings file records which env values were already imported. A camera deleted in the UI stays deleted even if its MAC env var is still set, and a UUID cleared in the UI stays cleared. The banner still lists env vars that are present in the container, since the point is to tell the user to remove them. A new MAC env var added later (never imported) is imported.
- **Ports.** Web UI and `POST /data` are on separate listeners: the UI is not reachable on the webhook port and `/data` is not reachable on 45462. The mechanism (second connector, second server) is the builder's. If the new listener port cannot be bound, the save is refused with an error and the old port keeps running. The existing env `port` already works in Docker (read in `Application`'s static init and applied in `main`), so it stays supported as an import source.
- **No webhook configured.** The app starts anyway. `POST /data` returns 200 (so UniFi does not retry or disable the hook), logs a warning, and sends nothing. An invalid env webhook is not imported; it is logged and the UI shows it as unset.
- **`/config` missing or unwritable.** The app still starts using in-memory settings, shows a prominent warning in the UI that changes will be lost on restart, and still requires the forced password change.
- **UI port env override** is named `webui_port` (lowercase, matching `webhook`, `port`).
- **Guide bundling.** `guides/` (markdown plus images) is copied into the image at build time and served as classpath/static resources. The build must not fail if `guides/` is absent; the page shows a friendly "guide not available" message.
- **Sessions.** Session cookie is not `Secure` by default (plain-HTTP LAN installs must work) but is set `Secure` when `X-Forwarded-Proto: https` is present. Login failures get a small delay; no lockout.
- **Docs.** No Unraid template XML lives in this repo, so the new `/config` volume and port 45462 are documented in the README only.
- **Test button (mine, please check).** It sends a short plain message (e.g. "Test message from Unifi Webhook to Discord") using the webhook URL currently typed in the field, saved or not, so users can verify before saving. It is a CSRF-protected POST behind the login, validates the URL with the existing regex first, and shows the result inline on the Settings page (success, or Discord's error/status) without a full-page reload requirement. It reuses the existing `WebhookManager`/`WebhookClient` send path.

## Constraints

- Container runs as `USER 99:100` (Unraid); `/config` must be writable by that user, and the app must handle the file being absent on first run.
- Existing tests (`ThumbnailsTest`, `WebhookClientMultipartTest`) must keep passing; update `README.md` and the Unraid template instructions (new volume, new port 45462).
- `Application` currently exposes config as public static fields (`DISCORD_WEBHOOK`, `UUID_1`, `UUID_2`, `macToDeviceName`) read by `WebhookController`; these should move to a settings store that the controller reads at request time so UI edits apply without restart.

## Left to the builder

- Package layout, templating approach, session/security library choice (Spring Security vs a minimal hand-rolled filter, with image size in mind), CSS details, how to run two ports in one Spring Boot app, tests.
