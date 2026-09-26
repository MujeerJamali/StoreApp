# BusinessERP

A native Android app for small businesses to manage parties (customers/suppliers),
inventory items, purchases, sales, payments, expenses, and reports — with bulk
import from Excel and Vyapar backups.

## Structure

```
BusinessERPyh/                  Gradle project root
├── settings.gradle
├── build.gradle                Top-level build config
└── app/
    ├── build.gradle            Module build config (applicationId com.mujeer.businesserp)
    ├── libs/jxl.jar             Excel (.xls) read/write for bulk import
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/mujeer/businesserp/   Activities, adapters, DatabaseHelper (SQLite)
        └── res/                            Layouts, drawables, and the shared design system
            ├── values/colors.xml           Brand palette + per-module accent colors
            ├── values/styles.xml           Shared button/input/card/theme styles
            └── values/dimens.xml           Spacing, radius, elevation, type scale
```

## Modules

- **Parties** — customers/suppliers, running balance.
- **Items** — inventory, purchase/sale price, optional **size/variety
  tracking** (e.g. shoe sizes), each variety+size combination keeps its
  own stock balance.
- **Purchases / Sales** — line-item transactions, size dropdowns on sale
  items only show sizes with stock, bulk Excel import for purchases.
- **Payments** — payment in/out against a party.
- **Expenses** — one-off and **recurring** (weekly/monthly/specific dates).
- **Cash** — a single ledger of all cash movement (sales, purchases,
  payments, expenses, manual adjustments); tapping a row opens its real
  source transaction.
- **Generate Entries** — bulk-create sale/purchase/payment/expense entries
  across a date range via a calendar tap UI.
- **Data tools** — bulk Excel import for purchases and items; full backup
  export/import in Vyapar's `.vyb` format (round-trips this app's own
  variety/combo and cash-adjustment data alongside standard Vyapar data).

## Reports

Total Sales · Sales by Party · Item Ranking · Party Ranking · Net Profit ·
Stock Worth · Item Monthly Rank (penalizes months an item didn't sell) ·
Average Cart Size/Amount · Profit: Cash Sale vs Party

All period-based reports share the same range selector: Today, Yesterday,
Week, Month, Quarter, Year, Custom Range.

## Building

Open `BusinessERPyh/` in Android Studio (compileSdk 29, minSdk 21) and run the
`app` module, or build from the CLI with a local Gradle/AGP 3.5.3 toolchain:

```
cd BusinessERPyh
gradle assembleDebug
```

Generated build output (`app/build/`) is not tracked in version control — see `.gitignore`.

The app is developed day-to-day by editing directly on-device in **AIDE**
(Android IDE app), not Android Studio — there is no `gradlew` wrapper
checked in for that reason. A plain incremental build/run is enough after
most code-only changes; a full rebuild is only needed after a change that
adds a new file, resource, or manifest entry, or touches `build.gradle`.
