![FoSellwands cover](docs/assets/cover.png)

# FoSellwands

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fosellwands) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fosellwands/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Editable sellwands for fast, protected container selling.

*Developed by Carrotio.*

## Features

- Creates editable sellwand tiers with multipliers and limited uses.
- Sells supported container contents through FoShop or ShopGUIPlus pricing.
- Supports cooldowns, permission controls, history, and sale breakdowns.
- Respects configured protection and WorldGuard rules.
- Includes AxSellwands migration and administrator give tools.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fosellwands). Check the [versions page](https://modrinth.com/plugin/fosellwands/versions) for supported Minecraft versions and server platforms.

- **Required:** `Vault`
- **Optional integrations:** `FoShop`, `ShopGUIPlus`, `WorldGuard`

## Screenshots

![Manage sellwand settings.](docs/assets/screenshots/fosellwands-admin-editor.png)

*Manage sellwand settings.*

![Configure supported containers.](docs/assets/screenshots/fosellwands-containers.png)

*Configure supported containers.*

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fosellwandsadmin <editor|reload|version|list|give> [player] [multiplier|tier] [uses] [amount]` — FoSellwands admin command.

### Permissions

- `fosellwands.admin` — Allows managing FoSellwands. Default: `op`.
- `fosellwands.use` — Allows using sellwands. Default: `true`.
- `fosellwands.use.basic` — Allows using the basic sellwand tier when per-tier permissions are enabled. Default: `true`.
- `fosellwands.use.rare` — Allows using the rare sellwand tier when per-tier permissions are enabled. Default: `true`.
- `fosellwands.use.legendary` — Allows using the legendary sellwand tier when per-tier permissions are enabled. Default: `true`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.
- `sellwands/*.yml` — Sellwand tier definitions.

Main config areas: `file-logging`, `settings`, `pricing`, `cooldown`, `permissions`, `breakdown`, `history`, `feedback`, `safety`, `protection`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoSellwands` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoSellwands</artifactId>
  <version>1.8</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foSellwands.api.SellwandApi;
import me.foesio.foSellwands.api.event.SellwandPreSaleEvent;

SellwandApi api = Bukkit.getServicesManager().load(SellwandApi.class);
if (api != null) api.tierIds();

@EventHandler
public void onSellwandPreSaleEvent(SellwandPreSaleEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `SellwandApi`: `tierIds`, `createTierWand`, `createCustomWand`, `readWand`.
- `SellPriceProvider`: `getSellPrice`.
- `SellItemFilter`: `canSell`.

**Other API types:** `SaleSnapshot`, `WandSnapshot`.

**Events:** `SellwandPreSaleEvent`, `SellwandSaleEvent`.

[Browse API source](src/main/java/me/foesio/foSellwands/api) for signatures and event details.

</details>
