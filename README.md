# titchy_bot

Chat bot for Spotify–Twitch interaction. A small desktop app (Kotlin, Compose for Desktop) that:

- shows the track currently playing on your Spotify and the next track in the queue, on a black background you can capture as an OBS overlay
- joins your Twitch chat and lets viewers add songs to your Spotify queue

## Chat commands

| Command | What it does |
|---|---|
| `!add <song>` | Searches Spotify and adds the top match to the queue. The bot always replies to the viewer. |
| `!queue` | Posts the current Spotify queue to chat. |

Replies to `!add`:

| Situation | Reply |
|---|---|
| Song added | `@viewer added to queue: <song> by <artists>` |
| No song name given | `@viewer usage: !add <song name>` |
| No search match | `@viewer couldn't find a song matching "<query>"` |
| No active Spotify player | `@viewer couldn't add <song> by <artists>: no active Spotify player` |
| Search or add failed | `@viewer couldn't search Spotify right now, try again later` / `@viewer couldn't add <song> by <artists> right now, try again later` |

## Requirements

- JDK (developed and tested with JDK 17)
- A Spotify app registered in the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
- A Twitch app registered in the [Twitch Developer Console](https://dev.twitch.tv/console)
- An IRC OAuth token (`oauth:...`) for the Twitch account the bot chats as. Twitch shows the bot's messages under that account's name.
- Port 8080 free on your machine. The app runs a local server there to receive the OAuth callbacks.

Spotify only accepts queue changes while a player is active, so have Spotify open and playing on some device when viewers use `!add`.

## Setup

1. In both the Spotify and the Twitch app settings, add this redirect URI:
   ```
   http://localhost:8080/callback
   ```
2. Copy `.env.example` to `.env` in the project root and fill it in:

   | Key | Value |
   |---|---|
   | `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` | From your Spotify app |
   | `SPOTIFY_REDIRECT_URI` | `http://localhost:8080/callback` |
   | `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET` | From your Twitch app |
   | `TWITCH_REDIRECT_URI` | `http://localhost:8080/callback` |
   | `TWITCH_CHANNEL` | The channel the bot joins, without `#` |
   | `BOT_OAUTH_TOKEN` | The bot account's IRC token, including the `oauth:` prefix |

   `.env` is gitignored. Never commit it.

## Running

```
./gradlew run
```

(On Windows PowerShell: `.\gradlew.bat run`.)

1. Click **Authenticate with Spotify** and log in through the browser window that opens.
2. Click **Authenticate with Twitch** and do the same. The app window freezes until each login finishes.
3. When both are done, the window shows the now-playing and next-in-queue text, and the bot joins your channel.

### Using it in OBS

Add a Window Capture source for the app window. The red border and the checkbox in the bottom-right corner help you frame the window. Untick the checkbox to hide the border.

## Development

```
./gradlew test                                          # all tests
./gradlew test --tests "ui.MainViewModelTest"           # one test class
./gradlew build                                         # compile and test
./gradlew packageDistributionForCurrentOS               # native installer (Msi/Dmg/Deb)
```

The tests run against a fake Spotify API and a local mock server. They don't need a `.env` file or network access.
