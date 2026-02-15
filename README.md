# DiscordMC

Discord Rich Presence client for Fabric `1.21.5`.

## Features
- Dimension-aware large images (`overworld`, `nether`, `end`)
- Multiplayer status with server name/address (optional)
- Player count parsing from server status (`online/max`) when available
- MOTD-based details (optional)
- Privacy mode (`Playing on a private server`)
- In-game client command settings via `/discordmc ...`
- Config persisted in `config/discordmc.json`

## Quick Start
1. Set your Discord app ID in `config/discordmc.json` (`applicationId`) or use `/discordmc appId <id>`.
2. Upload your Discord image assets with keys that match config values.
3. Launch the game and verify Rich Presence updates.

## Command Examples
- `/discordmc enabled true`
- `/discordmc private true`
- `/discordmc privateState Playing on a private server`
- `/discordmc showPlayerCount true`
- `/discordmc images overworld overworld`
- `/discordmc images nether nether`
- `/discordmc images end end`
- `/discordmc status`
