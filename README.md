# Eclipse SMP (Paper 1.21.11)

Ten abilities, each with an active and a passive, unlocked with Essence earned from kills.

## Getting the plugin jar (pick one)

**A. GitHub (no Java needed)**
1. Make a free repo on github.com and upload everything in this folder (including the hidden `.github` folder).
2. Open the **Actions** tab, wait about a minute for "Build EclipseSMP" to finish.
3. Open the finished run and download the **EclipseSMP-jar** artifact. Unzip it to get `EclipseSMP.jar`.

**B. Locally**
Install JDK 21 and Maven, open a terminal in this folder and run `mvn package`.
The jar appears at `target/EclipseSMP.jar`.

Put the jar in your server's `plugins` folder and restart.

## Resource pack
Use `EclipseSMP-ResourcePack.zip`.
- Server-wide: host the zip at a direct download link and set in `server.properties`:
  `resource-pack=<your link>` and `resource-pack-sha1=0b2ae5877a5ae9cd8b0c7004b9d50803d7d1349a`
- Just for you: drop the zip into your `.minecraft/resourcepacks` folder and enable it.
Without the pack the ability items show as echo shards and the essence as a nether star.

## How it works
- Kill a player: +1 Essence. Unlocking an ability costs 5 Essence.
- `/abilities` opens the menu: click to unlock, equip or unequip (free). You can equip 2 at a time.
- Equipped abilities appear as items in your inventory. **Right-click** with one in your main hand to use the active.
- **Left-click** in the air cycles to your other ability item.
- Passives are always on while the ability is equipped.
- Ability items can't be dropped or put in chests, and you get them back after death.

## Commands (admin permission: eclipse.admin)
- `/essence` shows your Essence. `/essence give|take|set <player> <n>`
- `/eclipse unlock <player> <ability|all>`, `/eclipse reset <player>`, `/eclipse reload`

Ability ids: phantom, crosscut, counter, singularity, chainreaction, overload, afterimage, rewind, soulchains, phoenix.

## Config
`plugins/EclipseSMP/config.yml` has max equipped abilities, unlock cost, Essence per kill, cooldowns and damage values.
