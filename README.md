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
  items only show sizes with stock, bulk Excel import for purchases. The
  Add/Edit Item dialog's Quantity, Price and Total fields are linked live:
  editing Total back-solves Price at the current Quantity (e.g. 4 @ 50 =
  200; changing Total to 100 sets Price to 25). The Add Item dialog's
  item dropdown always carries a "+ Add New Item" row at the bottom,
  even when what's typed already has (wrong) matches — tapping it jumps
  straight to creating the item and returns with it selected, no need
  to first type something that fails to match. A Purchase's "+ Select
  Expenses" button links one or more existing **Expenses** to it as
  landed cost (see below) — works even before the purchase itself is
  saved.
- **Cost Items / Linking Expenses to Purchases** — Expenses double as the
  source of a purchase's landed costs (petrol, shipping, packaging, ...);
  there's no separate "Purchase Cost" record to create. Cost Items is
  just the reusable category list (Petrol, Shipping, Packaging, ...) the
  Expense screen's Item field picks from — it must resolve to an
  existing Cost Item, and, like the Item dropdown above, always carries
  a "+ Add New Cost Item" row at the bottom to create one on the spot
  rather than silently creating one from an unrecognized typed name.
  From a Purchase's "+ Select Expenses" button, pick an existing,
  not-yet-linked Expense (or create a new one there and then) and choose
  which purchase(s) to split its amount across, proportionally by value
  — that share blends into each linked purchase's line items' **Extra
  Cost/Unit** by weighted average against current stock (see "Landed
  cost" below). A linked Expense keeps its own cash/party-balance effect
  entirely unchanged (its own amount/paid_amount/party fields, exactly
  as a standalone expense) — linking only decides how much of it counts
  as a purchase's landed cost, and once linked it drops out of the plain
  Expenses list and Net Profit/dashboard expense totals so it isn't
  double counted. Once linked, an Expense's split across purchase(s)
  isn't retroactively editable (same simplification as Extra Cost/Unit
  and Purchase Price themselves) — "Remove" on a linked-expense row
  deletes the link without undoing its already-applied landed-cost
  effect, and the expense becomes linkable again. Upgrading to this
  version seeds a Cost Item for every distinct item text already used
  by an existing Expense or Recurring Expense rule, one-time, so the
  Item autocomplete starts already populated instead of empty.
- **Payments** — payment in/out against a party.
- **Expenses** — one-off and **recurring** (weekly/monthly/specific
  dates); the Item field is a Cost Item autocomplete rather than free
  text — it must resolve to an existing Cost Item, typing an
  unrecognized name and saving anyway is rejected (see "+ Add New" row
  above). Any Expense can later be linked to a Purchase as its landed
  cost (see above).
- **Cash** — a single ledger of all cash movement (sales, purchases,
  payments, expenses, manual adjustments); tapping a row opens its real
  source transaction. **A transaction is rejected if it would take the
  cash balance below 0** — Purchase/Payment Out/Expense amounts and a
  negative Cash Adjustment are all checked before saving; a Sale,
  Payment In, or positive adjustment is never blocked, since it only
  ever adds cash.
- **Drafts** — a Sale, Purchase, Payment or Expense can be parked
  mid-entry via a "Save as Draft" button on its editor, skipping that
  screen's normal validation. The Drafts screen lists every parked
  entry; tapping one reopens the right editor prefilled from it, and
  the draft is deleted once it's actually saved for real.
- **Generate Entries** — bulk-create sale/purchase/payment/expense entries
  across a date range via a calendar tap UI.
- **Data tools** — bulk Excel import for purchases and items; full backup
  export/import in Vyapar's `.vyb` format. Alongside standard Vyapar data
  (parties, items, purchases, sales, payments, expenses, party transfers),
  it round-trips every one of this app's own extensions: item variety/
  combo tracking, cash adjustments, each item's Extra Cost/Unit, each
  expense's paid/credit split, Cost Items, Expense-to-Purchase landed-cost
  links, recurring expense rules, and Drafts (a Purchase/Sale draft's
  party/item/size/linked-expense selections are re-mapped to the restored
  device's new ids; a Payment/Expense draft has none to remap). Restoring
  is a full replace, not a merge, for every one of these too.

## Landed cost (Extra Cost/Unit)

Every item can carry an **Extra Cost/Unit** (transport, shipping, etc.)
alongside its Purchase Price, maintained automatically as a running
weighted average — a linked Expense's share of a purchase (see "Cost
Items / Linking Expenses to Purchases" above) is split across that
purchase's line items by value and blended in against the item's stock
at the time.
**Net Profit, the Cash Sale vs Party profit split, and Item Monthly Rank
by Profit all use Purchase Price + Extra Cost/Unit as the cost basis.
Stock Worth deliberately does not** — it's quantity × Purchase Price
only, same as before. Like Purchase Price itself, this is a single
current figure per item, not a per-batch/lot cost (stock isn't lot-
tracked); editing or deleting a past Purchase doesn't retroactively
re-blend it.

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
