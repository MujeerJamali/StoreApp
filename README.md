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
  200; changing Total to 100 sets Price to 25). A Purchase's "+ Add
  Purchase Cost" button attaches one or more **Purchase Costs** to it
  (see below) — works even before the purchase itself is saved.
- **Cost Items / Purchase Costs** — a reusable category list (Petrol,
  Shipping, Packaging, ...), shared between Purchase Costs and the
  Expense screen's Item field. A Purchase Cost is one cost event (a Cost
  Item + a total amount + when it happened) that splits proportionally
  by value across one or more purchases, each with its own choice of
  whether that share adds to the supplier's owed balance or is a cash
  outflow now — either way it's blended into that purchase's line
  items' **Extra Cost/Unit** by weighted average against current stock
  (see "Landed cost" below). Replaces the older single-purchase "Other
  Charges" field (still present on old purchases, just unused by new
  ones) with something reusable and splittable across purchases, and
  one that actually reaches the Cash screen when tracked purely as a
  cost — the old field's gap was that a "tracked purely as cost" charge
  never appeared anywhere in cash tracking. Once created, a Purchase
  Cost's amount and purchase split aren't retroactively editable or
  reversible (same simplification as Extra Cost/Unit and Purchase Price
  themselves) — only its category/date/notes can be changed afterward,
  and deleting one removes the record without undoing its already-
  applied cash/balance/landed-cost effects. Upgrading to this version
  seeds a Cost Item for every distinct item text already used by an
  existing Expense or Recurring Expense rule, one-time, so the Item
  autocomplete starts already populated instead of empty.
- **Payments** — payment in/out against a party.
- **Expenses** — one-off and **recurring** (weekly/monthly/specific
  dates); the Item field is a Cost Item autocomplete (typing a new name
  simply adds it to the list) rather than free text.
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
  export/import in Vyapar's `.vyb` format (round-trips this app's own
  variety/combo and cash-adjustment data alongside standard Vyapar data).

## Landed cost (Extra Cost/Unit)

Every item can carry an **Extra Cost/Unit** (transport, shipping, etc.)
alongside its Purchase Price, maintained automatically as a running
weighted average — a Purchase Cost's share of a purchase (see "Cost
Items / Purchase Costs" above) is split across that purchase's line
items by value and blended in against the item's stock at the time.
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
