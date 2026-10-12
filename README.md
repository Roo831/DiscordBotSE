# DiscordBotSE

Discord activity reports for Rust and Space Engineers, using JSON from [GameActivityMonitoringService](https://github.com/Roo831/GameActivityMonitoringService).

Rust reports use orange ☢️ embeds; Space Engineers reports use blue 🚀 embeds. Player statistics open with total online time, active days, peak hour and a small daily activity chart. Full session timelines, raw events and player lists are paginated with **Back / Overview / Next** buttons, preserving every returned entry within Discord embed limits.

## Commands

Replace `<game>` with `rust` or `space_engineers`:

| Command | Options |
| --- | --- |
| `/<game>_stats_week` | `nickname` (7 days) |
| `/<game>_stats_month` | `nickname` (30 days) |
| `/<game>_raw_data` | `nickname`, optional `days` (default 3) |
| `/<game>_all_nicknames` | optional `days` (default 7) |

Nicknames accept 1–128 characters; periods accept 1–365 days. Replies are visible in the channel. Only the person who requested a report can switch its pages. Pages are snapshots held in memory for 15 minutes, with a maximum of 100 reports per bot; older reports or reports lost on restart need a fresh command. Buttons switch pages without contacting the backend again. Times use the backend's time zone, shown in the footer.

## Build and run

Requires Java 21. On Windows:

```powershell
./gradlew.bat test bootJar
$env:DISCORD_ENGINEERS_API_KEY = 'engineers-bot-token'
$env:DISCORD_RUST_API_KEY = 'rust-bot-token'
$env:SERVER_IP = '127.0.0.1'
$env:SERVER_PORT = '8083'
$env:SERVER_API_KEY = 'same-key-as-backend'
./gradlew.bat bootRun
```

On Linux/macOS use `sh ./gradlew`. Docker Compose reads these variables from `.env`; Spring does not automatically read `.env` when running directly. Set an unused bot's token to `NOT_SET` to skip starting it. Invite each bot with the `bot` and `applications.commands` scopes and permission to send messages and embed links in the target channel.

### Docker deployment

The Dockerfile builds and tests the current source with Java 21 inside the builder stage, then copies that JAR into the runtime image. A local `build/libs` JAR is neither required nor included. `.env` and local logs are excluded from the build context; Compose still supplies `.env` at runtime.

After updating the backend to the JSON API, run these commands in the bot's repository (with a clean working tree on `main`):

```sh
git pull --ff-only origin main
docker compose build discord-bot
docker compose up -d --no-deps --force-recreate discord-bot
docker compose logs --tail=100 discord-bot
```

A restart alone does not rebuild the image. The build needs access to the Gradle distribution and Maven dependencies and fails if tests fail. Existing `.env` settings and `bot_logs` are preserved.

If your installation uses `docker-compose`, use that spelling in the commands above. `docker-compose up -d --build discord-bot` also builds from source with this Dockerfile.

If reports contain literal JSON, formatting is absent, or `/rust_all_nicknames` logs `Content may not be longer than 2000 characters`, the running bot is using the older text renderer. Rebuild the image from the updated source and recreate its container. The JSON renderer sends coloured embeds and paginates player lists; it never sends the entire nickname response as plain text.

The backend base URL is `http://${SERVER_IP}:${SERVER_PORT}/api/v1/analytics`; clients append `/rust` or `/engineers`. HTTP requests use a 5-second connection timeout and a 10-second read timeout. Commands are acknowledged before requesting data. Unavailable services produce a red error embed.

## Coordinated upgrade

This version expects structured JSON, not the previous Markdown responses. Pause the bot, deploy the paired GameActivityMonitoringService change, then deploy/restart this bot. Slash command names and environment variable names remain the same.

Tests exercise local HTTP JSON decoding, nickname URL encoding, embed limits, complete pagination, empty reports and navigation ownership. They do not log into Discord or query real game servers.
