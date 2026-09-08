# FoShop final-price integration

When the configured FoShop provider exposes getPriceApi(), sellwands request quote(player, item, wandMultiplier) and use that final unit price for the stack total. The wand multiplier is not applied again. A denied or failed API quote does not fall through to another shop provider.

Older FoShop versions retain the existing reflected price hook. ShopGUIPlus support and configured provider order remain intact. Public API methods are resolved once per plugin instance and cleared on reload; no compile-time FoShop dependency is introduced.

Actual stacks sold through the new API are copied into the sale result and reported only after payment, inventory and wand changes commit. Stacks priced through other providers are not attributed to FoShop. Reporting failures cannot undo or repeat payments and warnings are rate-limited.

A sale visits at most 2,048 slots including nested containers, keeping reporting below FoShop's 8,192-stack bound. Remaining items stay in place for a later use.

Requires the companion FoShop external-pricing API change to activate the new path. Compiles with Java 21 against the inspected runtime dependencies using an external validation build. The optional compatibility adaptation needs runtime tests for old FoShop, API-enabled FoShop, ShopGUIPlus-first configuration, denied prices, failed payments and nested containers. Original metadata, version, item keys and Maven configuration are unchanged.