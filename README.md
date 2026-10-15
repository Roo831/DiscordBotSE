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
| `/<game>_watch` | `nickname`: notify once when the player is observed online |
| `/<game>_watchlist` | your subscriptions in the current channel |
| `/<game>_unwatch` | `subscription_id` from the watchlist; the confirmation also has a cancel button |
| `/<game>_similar` | `nickname`, optional `days` (default 7): ranked activity overlap |

### Watchlist notifications

Use `/rust_watch nickname:Roo` in the channel where the notification should appear. Management replies are private. When the backend observes the player, the bot posts a green online card and mentions only the subscriber. The subscription is one-shot; an already-online player is queued immediately when the backend has a fresh snapshot. There are at most 25 active subscriptions per user and game across channels. Duplicate registration preserves the existing subscription and pending event. Cancellation is restricted to the requesting user and current channel; a message already being sent may still arrive.

Each enabled bot polls every 30 seconds. It reads pending events in pages of 100 and continues after failed deliveries so inaccessible channels do not block later subscribers. A successful Discord send is saved to a local receipt before acknowledging the event to the backend. If acknowledgement fails, retries (including after bot restart) acknowledge the same event without sending it again. Failed sends stay pending and can be cancelled with `unwatch`.

The backend keeps subscriptions and pending events in an ordinary JSON file. No database or database driver is used. Bot receipts are stored under `WATCHLIST_RECEIPTS_DIRECTORY` (default `./bot_logs`, `/app/logs` in Compose) and the existing `./bot_logs:/app/logs` mount preserves them on rebuild. Keep both applications' data directories. Run one bot instance per game with one backend instance: the queue does not lease events to multiple competing consumers. A crash after Discord accepts a message but before its receipt is saved can still cause a duplicate; this is at-least-once delivery, not an exactly-once guarantee.

Tracking is by normalized nickname, not SteamID. Name changes/collisions and player-list visibility on the monitored server limit accuracy. The backend polls once per minute; the additional 30-second bot poll means this is not an instant join alert. The card shows observation time.

### Similar activity

`/rust_similar nickname:Roo days:7` opens a purple summary and paginated ranked cards. Each candidate shows shared time, shared dates, both directional overlap percentages, shared recorded sessions, schedule overlap (Jaccard), and a separate ranking score. A shared session requires at least 15 minutes together within one target session; brief incidental encounters are filtered out. Score = Jaccard percentage × shared sessions. The backend sorts by score, Jaccard, overlap duration, then name; the bot preserves this order. The score is measured in points and may exceed 100, while the percentage remains schedule overlap, not a teammate probability. Equal join/exit timestamps are not used as team evidence because monitor restarts can generate them.

This is **co-presence inferred from activity logs**, not proof of friendship or a Rust team. No Steam API lookup is performed: the existing logs do not provide trustworthy SteamIDs. Missing polling periods, unmatched connections and historical nickname changes can affect the result. Durations are calculated by the backend and formatted only in this bot.

Nicknames accept 1–128 characters; periods accept 1–365 days. Activity report replies are visible in the channel; watchlist management replies are private. Only the person who requested a report can switch its pages. Pages are snapshots held in memory for 15 minutes, with a maximum of 100 reports per bot; older reports or reports lost on restart need a fresh command. Buttons switch pages without contacting the backend again. Times use the backend's time zone, shown in the footer.

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

The current Dockerfile packages a JAR from `build/libs`. Build a clean artifact first with `sh ./gradlew clean test bootJar` (or `gradlew.bat` on Windows); then build/publish the Docker image through the project's existing release process. Compose uses the published `roo831/discord-bot-se` image and does not compile checked-out source.

After deploying the paired backend and publishing the updated bot image:

```sh
docker compose pull discord-bot
docker compose up -d --no-deps --force-recreate discord-bot
docker compose logs --tail=100 discord-bot
```

Use `docker-compose` if that is the installed command. A restart or `up --build` does not publish a new remote image. Keep `.env`, `bot_logs`, and the existing proxy configuration.

If reports contain literal JSON, formatting is absent, or `/rust_all_nicknames` logs `Content may not be longer than 2000 characters`, the running bot is using the older text renderer. Rebuild the image from the updated source and recreate its container. The JSON renderer sends coloured embeds and paginates player lists; it never sends the entire nickname response as plain text.

The backend base URL is `http://${SERVER_IP}:${SERVER_PORT}/api/v1/analytics`; clients append `/rust` or `/engineers`. HTTP requests use a 5-second connection timeout and a 10-second read timeout. Commands are acknowledged before requesting data. Unavailable services produce a red error embed.

## Operational logging

Application and library logs use Log4j 2 (the Spring Boot starter replaces Logback). Output goes to the console and `APPLICATION_LOG_FILE`, defaulting to `./bot_logs/discord-bot.log`; Compose sets `/app/logs/discord-bot.log` in the existing mount. Receipts remain separate `.properties` files. Set `logging.level.*` to adjust verbosity. Bot tokens and API keys are not deliberately logged.

## Coordinated upgrade

This version expects structured JSON, not the previous Markdown responses. Pause the bot, deploy the paired GameActivityMonitoringService change, then deploy/restart this bot. Slash command names remain the same. Deploy the backend first to enable the revised ranking and session evidence; the bot can still decode older reports without these optional fields.

Tests exercise local HTTP JSON decoding, nickname URL encoding, embed limits, complete pagination, empty reports and navigation ownership. They do not log into Discord or query real game servers.
