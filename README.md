# Better Info

Book-style server info UI that opens in the center of the screen on a player's first join or when they run `/info`.

## Features
- Centered double-page "book" UI with a confirmation-style button
- Auto-open once per player (tracked in `BetterInfo/acknowledged.txt`)
- Admin-only editing commands: `/info editor|reload|list|set|add|remove|save`
- Configurable title, max lines, info text, and the `firstJoinPopup` auto-open toggle via `BetterInfo/config.yaml`

## Notes
- Players can reopen the info page anytime with `/info`.
- Permissions: editing subcommands require `betterinfo.admin`; viewing `/info` is open to everyone.
