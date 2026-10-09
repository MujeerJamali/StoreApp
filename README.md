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
  own stock balance. Each item also carries an **Active/Inactive**
  label (Edit Item screen), with an All/Active Only/Inactive Only
  filter on the Items list; an inactive item is hidden from every
  item picker used to add a new Sale/Purchase line or Wanted Item, but
  stays fully visible/editable on the Items list itself and in past
  transactions. Each item also has a **Reorder Threshold** (Edit Item
  screen, 0 = no alert) - its total stock at or below that number is
  what the Low Stock report/notification flags. Add/Edit Item has a
  **"This is a shoe"** checkbox that swaps the plain Name field for 7
  structured fields - Gender, Type, Sole, Upper, Design, Color, Size -
  matching this shop's `"Shoes {Gender} {Type} {Sole} {Upper} {Design}
  {Color} {Size} - {Code}"` naming convention (see `ShoeIdentity`,
  which every shoes-only report already parses that name back apart
  with). Each field must be a single word (no spaces), since the name
  is rebuilt by joining them - the screen builds and validates it
  instead of the user typing the whole formatted string by hand.
  Editing an existing item that already matches the convention
  auto-checks the box and pre-fills the 7 fields from its current name.
  Long-pressing a row on the Items list opens **Copy Item** (duplicates
  name/prices/Reorder Threshold with a fresh code and zero stock, then
  opens the new item's Edit screen to finish setting it up - variety
  groups are deliberately not copied, since those are specific to
  whatever the new item turns out to be) and a quick **Mark Active/
  Inactive** toggle, without opening the Edit Item screen for either.
- **Swipe navigation** — on the plain Add Sale / Add Purchase / Add
  Expense screens (never while editing an existing one, so a swipe
  can't be mistaken for navigating away from in-progress edits), a
  long, fast, mostly-vertical swipe cycles Sale → Purchase → Expense →
  Sale on swipe up, reverse on swipe down (see `SwipeNavigationHelper`).
  Tuned (distance + velocity thresholds) so ordinary scrolling or
  tapping inside the form is never mistaken for the gesture, and it
  always opens a fresh screen rather than finishing the current one,
  so nothing typed is ever lost to a swipe - the previous screen just
  sits in the back stack, reachable with a normal Back press.
- **Bulk Item Update** (Dashboard Tools card, pinnable as a Favorite) —
  applies a price change or a new Reorder Threshold across every
  active item whose name or code contains a typed filter. There's no
  separate category field on items, so this substring match is the
  closest thing to one (typing "Shoes Men" matches every men's shoe,
  since that's how this shop's naming convention already groups
  them). The match count updates live as the filter is typed, and
  both actions - a ± percent price change (Purchase Price and/or Sale
  Price, floored at 0) and setting a new Reorder Threshold - confirm
  with that exact count before touching anything.
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
  saved, and either way nothing is written until Save/Update Transaction
  is actually pressed: a pick made while editing an already-saved
  Purchase shows in the Linked Expenses list right away, tagged
  "(pending - applies on Update)", exactly like a new Purchase's picks
  do, rather than committing to the database the moment it's picked.
  A Sale or Purchase that isn't fully paid gets a **Due Date** field
  (defaults to the transaction date + 3 days the moment it becomes a
  credit transaction, editable from there) - see the Credit Due report
  below (Sales only - Purchases don't have an equivalent report yet).
  The Purchases/Sales list also supports **bulk select**: long-pressing
  any row enters selection mode (every row gets a checkbox, replacing
  that row's own single-delete confirmation) and a bar appears with a
  live "N selected" count plus Delete/Cancel; Delete confirms once,
  then loops the same per-row delete call for each selected row (see
  Recently Deleted below for what that call now does), so every
  balance-reversal side effect that already happens for a single
  delete still happens for each one in the batch. This is the first
  screen to get it, not the last - every other delete-capable list
  (Payments, Expenses, Wanted Items, Drafts, ...) is a candidate to
  pick up the same pattern later, the same way charts and info bubbles
  are being rolled out incrementally rather than everywhere at once.
- **Recently Deleted / Undo** — deleting a Purchase or Sale (single or
  bulk) no longer just deletes it: `DatabaseHelper.
  snapshotAndDeletePurchase()`/`snapshotAndDeleteSale()` first dump
  the full row plus its line items as one JSON blob into a
  `recently_deleted` table, then delete exactly as before. The
  Recently Deleted screen (Dashboard Tools card, pinnable as a
  Favorite) lists every entry still in the trash; tapping one restores
  it - re-inserting the row(s) with their *original* ids and
  re-applying the exact mirror of whatever the delete reversed (stock
  back out for a Sale/back in for a Purchase, the party balance shift
  redone) - and long-pressing one purges it immediately. Anything
  still there after 30 days is auto-purged on app open
  (`purgeOldRecentlyDeleted()`), so the trash doesn't grow forever.
  Deliberately **not** part of the Vyapar backup round-trip - it's
  already-deleted data's temporary echo with no analog in Vyapar, not
  standing user data like a Draft, and its snapshot's embedded
  party_id/item_id values are only meaningful in this exact database.
  **Undo-beyond-delete**: editing a Purchase or Sale snapshots the
  pre-edit row + line items into the same trash the instant before
  Update applies (`snapshotSaleBeforeEdit()`/
  `snapshotPurchaseBeforeEdit()`, type `sale_edit`/`purchase_edit`), so
  an accidental edit is just as undoable as a delete - the Recently
  Deleted screen shows these mixed in with deleted entries ("Edited"
  vs "Deleted"), and tapping one (`undoSaleEdit()`/
  `undoPurchaseEdit()`) deletes the current edited row exactly like a
  normal delete would, then re-inserts the pre-edit snapshot the same
  way restoring a deletion does - so undoing an edit and restoring a
  delete share the same re-insertion code underneath.
- **Reorder List** (Dashboard Tools card, pinnable as a Favorite) —
  what's worth restocking right now, computed by `DatabaseHelper.
  getReorderSuggestions()` from each item's (or, for a variety item,
  each individual combo's - a size 8 and size 9 of the same shoe are
  reordered separately) recent sales speed vs its current stock, using
  every input in **Reorder Settings** (gear icon on the same screen):
  the sales-speed window, a safety-stock %, a default supplier lead
  time, and a min/max order quantity - all user-editable, with
  reasonable defaults, not hardcoded. Reorder Settings also holds the
  non-shoe turnover multiplier (default 3x) that Slow-Moving Stock/
  Discount This Week/Dead Stock Aging share - see those reports below. A unit with no sales history
  still qualifies if its own manual Reorder Threshold (Edit Item
  screen) says it's low, the same signal the Low Stock report uses.
  Two more signals adjust a suggestion before it's shown: a seasonal
  check (`getSeasonalMultiplier()`) compares this calendar month's
  average sales in past years against what the recent velocity alone
  would project, and scales the suggested quantity up (capped at 2x)
  when a past-years pattern says this month typically sells faster -
  a shop with no history yet simply gets no adjustment rather than a
  guess; and each row shows a plain-language "runs out around
  [date]" estimate from dividing current stock by recent velocity, so
  the urgency doesn't require doing that math yourself. Nothing on
  this screen is final: each suggestion's quantity is a
  plain editable field, a checkbox excludes it, and "Ignore" dismisses
  it outright - only checked rows get used by **Convert Checked to
  Draft Purchase(s)**, which groups them by their inferred supplier
  (each item's most recent Purchase's party) and creates one draft
  Purchase per supplier, still needing to be opened from Drafts and
  actually saved before it touches stock or cash. If cash-awareness is
  on (Reorder Settings, default on), converting a batch that would
  exceed the available cash - either the real live balance or a
  manually entered figure, the user's choice - asks for confirmation
  first rather than silently blocking it. Every accept/ignore decision
  is logged to `reorder_suggestion_log` for a future pass to read back
  and improve future suggestions from (e.g. a suggestion that was
  ignored repeatedly, or that led to overstock); that log is
  deliberately **not** part of the Vyapar backup round-trip, same
  reasoning as Recently Deleted above - it's the automation's own
  operational memory, not a business record.
- **Loyalty Points** (Dashboard Tools card, pinnable as a Favorite) —
  every named customer earns 1 point per ₹100 spent on a Sale (floored;
  `DatabaseHelper.LOYALTY_POINTS_PER_RUPEES`), skipped for the "Cash
  Sale" placeholder party since it isn't a trackable customer.
  Deliberately an append-only ledger (`loyalty_points_ledger`), not a
  running total column - a party's current balance is always the sum
  of their own ledger rows (`getLoyaltyPointsBalance()`), and points
  earned are **not** clawed back if the originating Sale is later
  edited or deleted (once earned, a reward stays earned, same as most
  real loyalty programs); a genuine correction is a manual adjustment
  instead. Crossing a milestone (100/250/500/1000/2500/5000/10000
  points) shows a one-time "`<party>` just reached `<N>` loyalty
  points!" toast right after the Sale that crossed it - the rate and
  milestones aren't yet exposed as settings, reasonable defaults for
  now like `DailyDigestScheduler`'s fixed 9 PM. The Loyalty Points
  screen ranks every party with a non-zero balance, highest first,
  tapping through to that party's own screen - which also shows the
  balance directly and an **Adjust** action (a signed whole number +
  reason, e.g. a goodwill credit or a redemption) for manual
  corrections. Real standing user data (a customer's earned rewards),
  so it's included in the Vyapar backup round-trip.
- **Party Appearance / Description** — an optional free-form notes field
  (e.g. "Tall, beard, usually wears a blue cap") to help recognize a
  walk-in customer later. Editable only from the full Party edit screen
  (`Partieseditactivity`), deliberately left off the quick "+Add New
  Party" flow so that stays name-only and fast, same reasoning as the
  Varieties/appearance fields kept off quick-add elsewhere in the app.
  Shown read-only on the Party view screen in its own card, which is
  hidden entirely when a party has no notes. Real standing user data, so
  it's included in the Vyapar backup round-trip
  (`kb_names.full_name_appearance` — gated by `columnExists()` on
  import so a backup made before this field existed still restores
  cleanly, with imported parties simply getting no notes).
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
  cost (see above). The Party field is mandatory (defaults to "Cash
  Expenses" when there's no real party to bill it to) and is fully
  functional, not decorative — an unpaid/paid Expense affects that
  party's balance exactly like a Sale/Purchase (paid in full is a
  no-op; an unpaid portion makes the shop owe the party, same
  direction as an unpaid Purchase), and a party-linked Expense shows
  up alongside their Sales/Purchases on that party's own screen. Adding
  a new Expense also shows a compact row of **quick-add chips** for
  the most frequently logged expense items in the last 7 days (moved
  here from the Expenses list screen, where two rows of full-size
  cards took too much space above the list for what's meant to be a
  quick shortcut) - tapping one prefills Item/Amount exactly like
  picking the item from the Item autocomplete already does; the user
  still reviews and hits Save themselves, there's no direct insert
  from the chip. **Unusually-high flag**: saving an Expense whose
  amount is more than 2.5x the average of that same Cost Item's last
  3+ entries (`DatabaseHelper.getExpenseAmountStatsForItem()`) shows a
  one-tap "Save Anyway?" warning naming the usual average - a warning,
  not a rejection, since a genuinely bigger expense is entirely
  possible; a brand-new Cost Item with fewer than 3 prior entries is
  never flagged, since an average of that few data points wouldn't
  mean anything yet.
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
  across a date range via a calendar tap UI. A generated Sale or Purchase
  is always a 100% credit transaction (no paid-amount concept in this
  bulk tool), so each gets the same "transaction date + 3 days" default
  Due Date a manually-entered credit Sale/Purchase does.
- **Wanted Items** — log something a customer asked for that's out of
  stock or not in the catalog; not tied to a transaction. Tapping a row
  reopens the same Add dialog prefilled for editing, with a Delete
  option; a checkbox can mark it fulfilled once restocked/handled.
- **Display Shoes / Sample Shoes** — a free-form grid (Display, the
  right-foot shoe on the shelf - insert/remove rows and columns to
  mirror the actual shelf layout, landscape-locked) and a plain list
  (Sample, the left-foot shoe kept out to try on) of "Code - Size"
  references. Adding a shoe to either is a pure reference overlay - it
  never reserves or removes stock, and multiple identical Code+Size
  entries are allowed (interchangeable - removing one for a sale just
  removes any one of them, since they're identical). After a Sale
  saves: if that was the last unit of a Size that had a Display/Sample
  entry, the entry is removed automatically (there's no stock left for
  it to reference); if stock remains, the app asks "was this the
  Display/Sample one?" for each board that has an entry.
- **Notifications** — once per calendar day, on the app's first open that
  day (tracked in its own SharedPreferences per notifier, not the
  business database - see `BusinessERPApplication`), two independent
  checks each post their own notification when there's something to
  flag: `CreditDueNotifier` for any credit Sale whose Due Date is today
  or already past (overdue ones listed first, party + balance per
  line), opening that Sale directly, or the Credit Due report on its
  Overdue range if anything is overdue, Today otherwise; and
  `LowStockNotifier` for any active item at or below its
  own Reorder Threshold (name + current stock per line, opens that item
  directly or the Low Stock report). A third, `BackupReminderNotifier`,
  is allowed to repeat daily rather than check once and stop: once 7
  days pass with no successful Vyapar backup export, it nags every day
  (opening straight to Export Vyapar Backup) until one actually
  happens, which resets its countdown. A brand-new install gets the
  same 7-day grace period an overdue backup would, rather than nagging
  on day one. Alongside the reminder, `AutoBackupScheduler` also
  actually runs a backup automatically every 7 days (a plain
  AlarmManager repeating alarm, re-registered - harmlessly, not
  duplicated - on every app open) via `AutoBackupReceiver`, which does
  the whole export itself in the background rather than opening Export
  Vyapar Backup (Android blocks a background trigger like this from
  starting an Activity on API 29+ unless the app is already visible).
  It writes one rolling file, overwritten each run, to this app's own
  external-files folder - a safety net against data loss, not an
  archive; the manual export is still what moving a backup to another
  device is for. A successful automatic backup resets the reminder's
  countdown too, so in normal use the reminder should rarely ever
  actually need to nag. A fourth, `ReorderDigestNotifier`, checks weekly
  rather than daily - a fresh Reorder List isn't worth a notification
  every single day - and opens straight to Reorder List when there's
  anything in it. A fifth, `MarginErosionNotifier`, checks daily like
  the first two, for any item whose margin % has dropped 5+ points
  this month vs last (name + point drop per line, opens that item
  directly or the Margin & Profit Alerts report). A sixth,
  `DailyDigestNotifier`, is fixed-time rather than once-per-app-open:
  `DailyDigestScheduler` fires it via a plain AlarmManager alarm at a
  fixed 9 PM local time every day (re-registered - harmlessly, not
  duplicated - on every app open), posting today's Sales/Purchases/
  Expenses/Net Cash Movement (the same figures Day Close shows) as one
  notification that opens straight to today's Day Close. It always
  posts, even on a zero-activity day - confirming "nothing happened
  today" is still useful, not something to silently skip the way the
  alert-style notifiers above do when there's nothing to flag - but
  still guards against posting twice for the same date.
- **Trending flags** (Items screen) — each item row shows a small ▲/▼
  next to its stock when `DatabaseHelper.getItemTrends()` finds its last
  7 days of sales meaningfully faster (▲, an early stock-out warning,
  possibly before it's even hit its Reorder Threshold) or slower (▼, an
  early overbuy warning) than the 7 days before that. An item with no
  sales in the earlier window has nothing to compare against and stays
  unflagged. Computed in one pass across every item, not a query per
  row, so it doesn't slow the list down.
- **Data tools** — bulk Excel import for purchases and items; full backup
  export/import in Vyapar's `.vyb` format. Alongside standard Vyapar data
  (parties, items, purchases, sales, payments, expenses, party transfers),
  it round-trips every one of this app's own extensions: item variety/
  combo tracking, cash adjustments, each item's Extra Cost/Unit, each
  expense's paid/credit split, each credit Sale/Purchase's Due Date, Cost Items,
  Expense-to-Purchase landed-cost links, recurring expense rules, Drafts (a Purchase/Sale draft's
  party/item/size/linked-expense selections are re-mapped to the restored
  device's new ids; a Payment/Expense draft has none to remap), Wanted
  Items, and Display/Sample Shoes (each entry's item+size re-mapped the
  same way, dropped rather than left dangling if either no longer exists
  in the backup). Restoring is a full replace, not a merge, for every one
  of these too.

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

Total Sales · Sales by Party (also doubles as Top Customers - its sort
spinner has Profit: High to Low/Low to High alongside Amount/Name/
Number of Sales, ranked by what each party actually contributed to
profit rather than just how much they spent, and every row always
shows both amount and profit regardless of which one it's sorted by) ·
Item Ranking · Party Ranking · Net Profit
(a merged Item Profitability report - the per-item breakdown has a
This Period/Standing Margin mode toggle: This Period is actual
profit/loss for every item with a sale in the period; Standing Margin
is every active item's own current margin per unit and margin %
straight from its purchase/sale price, period-independent and
including items that have never sold. Either mode is sortable
high-to-low or low-to-high with an All/Shoes Only/Non-Shoes Only
filter) · Stock Worth ·
Party Balances (zero/non-zero balance filter, same 6-way sort as the
Parties screen - Recent/Oldest Activity, Balance High-Low/Low-High,
Name A-Z/Z-A) · Credit Due (every unpaid/partially-paid Sale whose
Due Date falls in the selected period - Overdue/Today/Tomorrow/
Next 3 Days/Next 7 Days/This Month/All/Custom Range, default Today,
forward-looking unlike
every other report's period selector since a due date is something
still coming rather than something that already happened; tapping a
row opens that sale) · Low Stock (every active item at or below its
own Reorder Threshold, lowest stock first, no period selector - always
right now; tapping a row opens that item) · Item
Monthly Rank (penalizes months an item didn't sell) · Average Cart
Size/Amount · Profit: Cash Sale vs Party · Shoes vs Non-Shoes
(Sale/Profit toggle, same period selector, % split between shoe items
and everything else) · Combo/Variety Stock (shoes only - a single
screen: a Gender + Size dropdown filter, built by parsing each item's
name, narrows the item list below it and shows the total stock of the
selected size across every item just under the filter; each row shows
the item's name and every size it comes in with stock, the size
matching the filter shown first, bold, and (when a size is selected)
used to sort items highest-stock-first; tapping an item opens it
directly) · Day Close (one day's Sales/Purchases/Expenses/Payments In/
Payments Out totals plus how much that day actually moved the running
cash balance, a date picker defaulting to today but able to review any
past day; each row taps through to that category's own list,
pre-filtered to that one date - Sales/Purchases open Transactionactivity,
Expenses opens Expensesactivity, Payments In/Out open Paymentactivity,
and Net Cash Movement opens the Cash ledger - plus, only when the
selected date is today, a "Tomorrow's Reorder Prep" card showing how
many items the Reorder List currently has, tapping through to it) ·
Cash Flow Forecast
(today's real cash balance projected forward day by day using only
Sales/Purchases already due on a future date - never a prediction from
history, so a day with nothing due just carries the balance forward
unchanged; a Next 7/14/30/60 Days horizon spinner, default 30 Days) ·
Cash Projection (today's cash balance projected across a from/to
period you pick, defaulting to today through +30 days - adds Sales/
Purchases due in that exact window, plus recurring expenses expected
to fall in it, simulated day by day with the same due-check logic
that actually generates them, not an average; a separate "What If"
line shows what restocking everything currently on the Reorder List
would cost and what cash would be left after, since that's not
committed yet - see `DatabaseHelper.getCashProjection()`) ·
Slow-Moving Stock (every active item still carrying stock that hasn't
sold within a 30/60/90/180 Days window, oldest/never-sold first - a
never-sold item always qualifies regardless of how young it is; a
non-shoe item's days-since-sale is divided by Reorder Settings' non-
shoe turnover multiplier (default 3x) before being compared against
the window, since this shop's general merchandise (Clothes/Toys/Home/
Tools) naturally turns over slower than its shoes - the window shown
is always the item's real, truthful last-sold date, only the cutoff
comparison is adjusted; tapping a row opens that item) · Discount &
Stop-Restocking (two independent lists on one screen: Discount This
Week reuses the same slow-moving stock as above but with a suggested
discount tier - 10% at 30-59 effective days since last sale, 20% at
60-89, 30% at 90+ or never sold, same category-adjusted effective days
as Slow-Moving Stock - and Stop Restocking is every active item
currently selling at or below its own cost basis, i.e. restocking it
at today's prices would be a loss; tapping a row opens that item) · Stock Value (where current stock
value - balance x purchase price, every active item with stock - is
tied up, sliced two ways from the same total: by category, the first
word of each item's name since there's no separate category field
(naturally groups every "Shoes ..." item the same way Stock Worth
does), and by age, days since last sale in the same 0-30/31-60/
61-90/90+/Never Sold buckets Slow-Moving Stock uses; both lists
sorted highest-value-first) · Month-over-Month (This Month, 1st through
today, vs the full previous calendar month, for Sales/Net Profit/
Expenses - each with its change and % change, colored green/red by
whether that change is actually an improvement; Expenses is the one
metric where a decrease is the improvement; below that, a Sales by
Category breakdown of the same two periods - category is the first
word of each sold item's name, same proxy as Stock Value's - listing
every category seen in either period sorted by the size of its
change, not just a top few) · Cross-Sell Insight (search and pick any
item, see what it's frequently bought together with - other items
that showed up in the same Sale, ranked by how often - and, the
reverse direction, what it's rarely bought together with - every
other sold item, ranked by that same co-occurrence count ascending, a
possible missed cross-sell opportunity) · Dead Stock Aging (every
active item with stock that hasn't sold in 60+ effective days - a
stricter cutoff than Slow-Moving Stock's 30-day default, same category-
adjusted effective days as above - bucketed into 60-89/90-119/120+ days
or Never Sold; tap Start Clearance on any row to mark it down
at a chosen discount %, which moves it into an Active Clearances
section tracking how much of the stock on hand when clearance began
has sold since, until End Clearance or it's all gone) · Margin &
Profit Alerts (This Month vs Last Month at the item level, with a
shoes/non-shoes filter: Margin Erosion lists every item that sold in
both months whose margin % dropped 5+ points, biggest drop first;
Biggest Profit Swings lists the top 10 items by the size of their
profit change vs last month, up or down; see Notifications below for
the daily alert this same check also feeds) · Size-Curve Analysis
(shoes only, lifetime not period-based: every shoe size's share of
sales vs its share of current stock - Size is parsed straight out of
each item's name via `ShoeIdentity`, not a variety group, since every
real shoe item here is already its own exact size; a size selling more
than its share of stock is flagged as a stockout risk, one stocked
more than it sells as cash sitting idle, anything within 3 points
either way shown as Balanced) · Expense Ratio Trend (Expenses as a %
of Sales for each of the last 6 calendar months, oldest first - a
rising ratio means expenses are growing faster than sales, worth
watching even when both totals are individually growing; each month's
badge is colored by whether its ratio improved or worsened vs the
month before it)

Every report list row that represents an item or a party is tappable
and opens that item's or party's own screen (Net Profit, Item Ranking,
Party Ranking, Sales by Party, Item Monthly Rank, Party Balances,
Combo/Variety Stock, Low Stock, Slow-Moving Stock).

The Parties list itself also sorts (Recent/Oldest Activity, Balance
High-Low/Low-High, Name A-Z/Z-A) using the same underlying query as
the Party Balances report.

All period-based reports share the same range selector: Today, Yesterday,
Week, Month, Quarter, Year, Custom Range.

**Charts**: Net Profit, Stock Worth, Shoes vs Non-Shoes, Profit: Cash
Sale vs Party, and Month-over-Month each show a small bar chart
alongside their summary figures, drawn by a dependency-free custom view
(`SimpleBarChartView` - no charting library is wired into the project).
This is an ongoing pass, not a finished one - more reports get charts
over time.

The Dashboard also has a Sales Trend sparkline (daily sales total for
the last 7/30 days, including a day with zero sales - a quick "is the
shop busy lately" glance, not a profit report) using the same
`SimpleBarChartView`. Past 10 entries it switches to a sparser
rendering (no per-bar value text, only every few bars labeled) so a
30-point chart doesn't turn into overlapping text - every other chart
above has far fewer entries and renders exactly as before.

**Dashboard Favorites**: long-press any Tool Row on the Dashboard
(either the Modules card - Parties/Items/Purchases/Sales/Payments/
Expenses/Reports - or the Tools card below it) to pin it into a new
Favorites card at the very top of the Dashboard, above the cash
summary; long-pressing a pinned row there unpins it the same way. Pin
order is preserved across app opens (`DashboardFavorites`,
`SharedPreferences`); the Favorites card and its label are hidden
entirely when nothing is pinned.

**Global Search**: a tappable search box at the top of the Dashboard
opens a dedicated Search screen rather than searching inline on an
already-busy Dashboard. One box searches Parties, Items, and invoice
numbers (Sale or Purchase) at once, each in its own section, hidden
independently when it has no matches; tapping a result opens that
party/item/transaction directly. Parties and Items reuse their own
existing full-list DB methods, filtered client-side with the same
`SearchUtils` token-matching every other list screen's search box
already uses; invoice numbers get their own query
(`DatabaseHelper.searchTransactionsByInvoice()`) since Sales and
Purchases are two separate tables with no existing combined list to
filter. While the search box is empty, a Recent Searches list
(`RecentSearches`) offers the last 8 queries that actually led to a
tapped result (not every keystroke, which would fill it with partial
text like "s"/"sh"/"sho") - tapping one re-runs it, "Clear" wipes the
list.

**Sticky report filters** (`FilterMemory`): the shoes/non-shoes filter,
sort order, and similar stable preference spinners on Net Profit, Item
Ranking, Item Monthly Rank, Margin & Profit Alerts, and Slow-Moving
Stock's window remember the last choice made and restore it the next
time that report opens - the practical version of a "favorite filter"
for a shop that's almost always going to pick the same one. Deliberately
**not** applied to any date/period range spinner (Today/This Month/
Custom Range, etc.) - silently reapplying an old date range would show
stale data without the user realizing it, so those always reset to
their own sensible default instead.

**Info Bubbles**: a reusable minimalist "i" badge (`InfoBubbleView` -
a small circle, text "i", not an image icon, since this app removed
decorative icons everywhere else; this is a functional tap
affordance, exempt the same way a Spinner's dropdown arrow would be)
that shows an explanation dialog on tap. One call wires it:
`infoBubble.setInfo(title, description)`. First-pass coverage: every
report under Reports (all 18, plus the Reports list itself) now has
one next to its title, and the Dashboard has three (Cash in Hand,
Sales Trend, Favorites). This is a first pass, not a finished one -
every other screen in the app is a candidate to pick up the same
badge later, the same way charts and bulk select were rolled out
incrementally rather than everywhere at once.

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
