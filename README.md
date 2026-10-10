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
  what the Low Stock report/notification flags, and what the Items
  list's stock figure itself now colors amber for (red still means
  actually at/below zero; amber means "at or below its own reorder
  threshold but still in stock"; an item with no threshold set just
  never shows amber - see `ItemAdapter.getView()`). Add/Edit Item has a
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
  Edit Item also has a plain free-form **Locations** field (e.g.
  "Shelf A, Bin 12") for tagging where an item physically sits -
  deliberately one comma-separated text field rather than a separate
  bin/shelf table, so "multi-bin" just means multiple comma-separated
  words in it; shown read-only on the Item view screen when set, hidden
  entirely when it isn't. That screen's own Transactions list is capped
  to the 30 most-recent (date+time desc) at a time rather than ever
  loading an item's entire lifetime history at once - a "Load More"
  button reveals 30 more per tap, hidden once nothing's left
  (`DatabaseHelper.getTransactionsByItem(itemId, limit, offset)`/
  `getTransactionCountByItem()`).
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
- **Stock Take** (Dashboard Tools card, pinnable as a Favorite) — a
  physical count reconciliation mode. Starting a session snapshots
  every active item's (and, for a varied item, every one of its
  variety combos') current stock as its "expected" quantity; you then
  walk the shop tapping each row to enter what you actually counted -
  a plain number entry, same pattern as the reorder-threshold
  checklist above. Finishing the session applies every counted line's
  difference from expected as a real stock adjustment, through the
  exact same mechanism a Purchase/Sale line already uses
  (`adjustItemBalance()`/`adjustComboBalance()`), so items.balance
  stays correct either way; an item you never got to counting is left
  untouched entirely rather than being treated as zero. At most one
  session is open at a time, and it can be cancelled with no stock
  effect if started by mistake. **Past Stock Takes** lists every
  finished session with its date and discrepancy count, tapping one
  shows exactly what differed from expected that time (see
  `DatabaseHelper`'s Stock Take section:
  `startStockTake()`/`setStockTakeLineCount()`/`finishStockTake()`).
- **Quick Sale** (Dashboard Tools card, pinnable as a Favorite) — one
  screen, the biggest buttons, the least navigation, for a fast
  walk-up cash sale. Deliberately skips everything the full Sale
  screen asks for that a quick sale never needs: no party picker
  (always the existing "Cash Sale" placeholder party, same one the
  full Sale screen already falls back to), no partial payment or due
  date (always paid in full), no discount/other-charges fields, and no
  variety/size picker - only plain, size-less items show up here at
  all, since picking a specific size is inherently not "quick" (a
  varied item still sells normally through the full Sale screen).
  **Quick Picks** are large buttons for whichever items sold most
  recently (falling back to the first few items alphabetically if
  nothing has sold yet) - tapping one adds a unit straight to the cart;
  an **"+ Add Any Item"** search covers anything not already a Quick
  Pick. Tapping a cart row offers +1/-1/Remove. **Complete Sale** saves
  it exactly like the full Sale screen would
  (`insertSale()`/`insertSaleItem()`) and then clears the cart, ready
  for the next customer without ever leaving this screen (see
  `QuickSaleActivity`, `DatabaseHelper.getSimpleItemsForQuickSale()`).
- **Purchases / Sales** — line-item transactions, size dropdowns on sale
  items only show sizes with stock, bulk Excel import for purchases. The
  Add/Edit Item dialog's Quantity, Price and Total fields are linked live:
  editing Total back-solves Price at the current Quantity (e.g. 4 @ 50 =
  200; changing Total to 100 sets Price to 25). The Add Item dialog's
  item dropdown always carries a "+ Add New Item" row at the bottom,
  even when what's typed already has (wrong) matches — tapping it jumps
  straight to creating the item and returns with it selected, no need
  to first type something that fails to match. That same dropdown also
  pins whatever items were sold most recently to the very top (see
  `ItemPickerUtils.pinRecentlySoldItemsFirst()`/`DatabaseHelper.
  getRecentlySoldItemIds()`), on both the Sale and Purchase screens,
  since a fast-selling item is exactly the one worth restocking too -
  everything else stays in its usual alphabetical order behind them.
  The same pinning applies to every other screen that lets the user
  browse/pick from the full items list - Generate Entries' own item
  picker, and Quick Sale's quick-pick grid (which fills from the most
  recently sold items directly rather than reordering a browsable list,
  since it has no list to browse in the first place). A Purchase's "+ Select
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
  A Sale (not Purchase - there's no discount concept on that side)
  also has a **Discount** card with 5%/10% quick-preset buttons plus
  a plain editable percent field for any other value ("Custom" just
  focuses it) - the Grand Total, the live Estimated Profit preview,
  and the default Amount Paid all update immediately as the percent
  changes, and editing an already-saved Sale back-computes the
  percent from its stored absolute discount amount to prefill the
  field (the sales table itself still only stores one absolute
  amount, same as before).
  A Sale also shows a **Loyalty Points** chip live while it's being
  entered, whenever a real (non-"Cash Sale") party is selected - that
  party's current points balance plus a "(+N)" preview of how many
  this exact sale would add, using the same formula
  (`DatabaseHelper.getLoyaltyPointsPreview()`) the actual award on save
  uses, so the two numbers never disagree. Updates immediately as
  items, the discount, or the party itself change. The milestone-
  reached toast after saving is still separate - a one-time
  celebration, not the only place a balance ever shows.
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
  Separately, the Purchases/Sales list, Payments list, and Expenses
  list now also support **swipe-left-to-reveal Edit/Delete** on a
  single row (`SwipeRevealLayout` - a plain View/MotionEvent custom
  ViewGroup, not RecyclerView's `ItemTouchHelper`, since every list in
  this app is a ListView/BaseAdapter and no RecyclerView dependency is
  wired into `build.gradle`) - a faster one-swipe path for "just this
  one" than long-press-into-bulk-select. Delete still shows the usual
  confirmation dialog; on the Purchases/Sales list it reuses the exact
  same Recently-Deleted-restorable snapshot delete the bulk-select bar
  uses, swipe is disabled while bulk-select is active on that same
  screen, and it stays a no-op everywhere else `TransactionAdapter` is
  reused read-only (Partyviewactivity's/Itemviewactivity's transaction
  history). This is the first pass, not the last - every other
  delete-capable list is a candidate to pick up the same gesture later.
  A **Swipe Gesture Settings** screen (Dashboard Tools card, pinnable
  as a Favorite; `SwipeGestureSettings`) adds a single app-wide on/off
  switch for this gesture across those three lists - off doesn't
  remove Edit/Delete, it falls them back to this app's pre-swipe
  convention (tap to open, long-press to delete with confirmation),
  which each of those screens already wires up independently of swipe.
  Each adapter's own `setSwipeEnabled(false)` hides that row's actions
  panel, which `SwipeRevealLayout` already treats as "no gesture here
  at all" (its `isSwipeEnabled()` check), so every touch falls straight
  through to the owning list's normal row click/long-click - the exact
  same mechanism `TransactionAdapter` already used to stay inert on
  its own read-only reuses, just now driven by a user preference too.
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
  time, and an app-wide min/max order quantity floor/cap - all
  user-editable, with reasonable defaults, not hardcoded. A suggestion's
  actual minimum is the larger of that app-wide floor and its own
  per-item one (`getEffectiveMinOrderQty()`, see the **Min Order
  Quantities** screen below) - 6 for a shoe item by default, or whatever
  that item/combo was last Purchased in, unless explicitly overridden -
  so an item normally bought by the dozen never gets suggested a
  quantity of 1-2 just because the formula alone would land there.
  Reorder Settings also holds the
  non-shoe turnover multiplier (default 3x) that Slow-Moving Stock/
  Discount This Week/Dead Stock Aging share - see those reports below. A unit with no sales history
  still qualifies if its own manual Reorder Threshold (Edit Item
  screen) says it's low, the same signal the Low Stock report uses.
  Five more signals adjust a suggestion before it's shown: a seasonal
  check (`getSeasonalMultiplier()`) compares this calendar month's
  average sales in past years against what the recent velocity alone
  would project, and scales the suggested quantity up (capped at 2x)
  when a past-years pattern says this month typically sells faster -
  a shop with no history yet simply gets no adjustment rather than a
  guess; a **Seasonal Calendar** (Reorder Settings) lets the user
  manually mark their own busy periods too (e.g. a festival season) -
  a month plus a quantity-boost %, applied app-wide for that month on
  top of the auto-detected per-item pattern above, since the user's own
  calendar knowledge can cover a pattern too new or too irregular for
  sales history alone to have caught yet. A manual **Holiday/Gift Item**
  checkbox (Edit Item screen) is a separate, item-level version of that
  same idea for an item with no sales history to auto-detect a pattern
  from in the first place (a toy that's never been sold here before,
  say) - checking it applies a flat 50% boost during Oct/Nov/Dec
  (`getHolidaySeasonalMultiplier()`), shown as a "Holiday/Gift Item"
  badge on the Item view screen. A **Set Reorder Thresholds**
  onboarding checklist (Reorder Settings) lists every active item still
  at the default threshold of 0 ("not set") so the Reorder List and Low
  Stock report can flag it even before it has enough sales history to
  judge speed from - tapping a row prompts for a number right there and
  it drops off the list once set, with a running "N of M items have a
  threshold set" progress line; an item deliberately left at 0 simply
  stays on the list, which is expected (`getItemsMissingReorderThreshold()`,
  `getActiveItemCount()`, `setItemReorderThreshold()`). A simple per-item sales
  forecast (`getForecastedWeeklyVelocity()`)
  weighs the last 4 individual weeks of sales most-recent-heaviest
  (4/3/2/1) rather than one flat average, so a genuine recent trend
  shows up as its own number - shown on the row as "Forecast: ~N over
  the next 2 weeks" - and as a further adjustment
  (`getForecastMultiplier()`, the ratio of that trend-weighted rate to
  the flat average, clamped 0.6x-1.6x) on top of the seasonal one; and
  each row shows a plain-language "runs out around [date]" estimate
  from dividing current stock by recent velocity, so the urgency
  doesn't require doing that math yourself. Every row also shows a
  plain-language **"why this suggestion"** line (`buildReorderWhyExplanation()`),
  built from the exact same inputs the calculation just used so it can
  never drift out of sync - whether it's velocity- or manual-threshold-
  triggered, plus a note for each adjustment above that actually fired
  (seasonal boost, manual busy-period boost, holiday-item boost, trend,
  or learning nudge) and by how much. Nothing on
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
  is logged to `reorder_suggestion_log`, and this is an actual closed
  learning loop, not just a log: every time the Reorder List loads,
  `resolvePendingReorderOutcomes()` first judges every decision old
  enough to fairly judge (21 days - roughly a lead time plus a selling
  window) - an "accepted" suggestion where most of what was ordered is
  still sitting unsold gets upgraded to "overstocked"; an "ignored"
  one where stock has since hit zero gets upgraded to
  "ran_out_before_restock"; anything that didn't go wrong is simply
  left as "accepted"/"ignored" forever, which is this loop's way of
  recording "that call was right." `getLearningAdjustmentMultiplier()`
  is what actually reads this back: each item/combo's last 5 resolved
  decisions nudge its own future suggested quantity - down a step per
  "overstocked", up a step per "ran_out_before_restock" (clamped to
  0.5x-1.75x so a run of either can't spiral), applied on top of the
  seasonal multiplier above. That log is deliberately **not** part of
  the Vyapar backup round-trip, same reasoning as Recently Deleted
  above - it's the automation's own operational memory, not a business
  record. Reorder Settings' **View / Edit History** button opens a
  dedicated **Reorder Learning History** screen
  (`ReorderLearningHistoryActivity`) listing this raw log 30 rows at a
  time ("Load More" for the rest), newest first; tapping a row opens
  an edit dialog to correct its item, suggested date/quantity, or
  outcome, or delete it outright (with confirmation) - useful for
  fixing a wrong outcome or removing a bad entry without it keeping
  skewing future suggestions. **+ Add Entry** backfills a decision the
  log missed - item-level only (it can't pick a specific variety
  combo, unlike a row the Reorder List itself already logged against
  one). Reorder Settings also keeps its own **Export History (.csv)**
  button (`DatabaseHelper.getReorderSuggestionLogForExport()`), for
  taking the whole log to Excel/Sheets via the same system file-picker
  pattern Export Vyapar Backup uses. Same reasoning as the log itself:
  this is the automation's own history, not business data, so both the
  in-app screen and the export are deliberately separate from the
  Vyapar round-trip.
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
- **Customer Tagging** — a plain Spinner on the full Party edit screen
  classifies a party as Regular (the default), One-Time, or Wholesale.
  Same "+Add New Party stays name-only" reasoning as Appearance /
  Description above, so it's not offered on quick-add either - every
  new party starts Regular until someone tags it from the edit screen.
  A tagged (non-Regular) party shows its tag as a small label on the
  Parties list row and on its own Party view screen; a Regular party
  shows neither, so the common case stays visually quiet. The Parties
  list also gets a second filter dropdown (All Types/Regular/One-
  Time/Wholesale) alongside its existing sort dropdown, both plain
  `@style/FilterSpinner` controls. Real standing user data, so it's
  included in the Vyapar backup round-trip
  (`kb_names.full_name_customer_type` — gated by `columnExists()` on
  import exactly like `full_name_appearance`, so an older backup still
  restores cleanly with every imported party defaulting to Regular).
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
- **Expense Category Budgets** — each Cost Item's own edit screen has a
  Monthly Budget field (0 = no budget/alert, the default). Actual spend
  against it is this calendar month's `expenses.item` rows matching
  that category's name (plain text, same join the Expense screen's own
  autocomplete already relies on - a category renamed here just starts
  fresh under the new name). The Cost Items list shows a "% of budget"
  status badge next to any category that has one set, red once spend
  passes 100%; a category with no budget shows nothing extra. A daily
  check (`ExpenseBudgetNotifier`, same once-per-calendar-day pattern as
  Margin Erosion's own alert) posts one notification listing every
  category currently over its budget, spent vs budget - never posted
  for a category with no budget set at all. Real standing user data, so
  it's included in the Vyapar backup round-trip
  (`businesserp_cost_items.cost_item_monthly_budget` — gated by
  `columnExists()` on import exactly like the other Cost Items fields,
  so a backup made before this existed still restores cleanly with
  every imported category defaulting to no budget).
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
- **Crash-Safe Draft Autosave** — a silent, separate safety net on top
  of the explicit Drafts feature above: while composing a brand-new
  Sale, Purchase, Payment, or Expense (never while editing an existing
  committed one), that editor screen quietly autosaves what's on it
  every 20 seconds, as long as there's at least a party or an
  item/amount entered. If the app is killed or crashes mid-entry, the
  next time that same kind of "Add" screen is opened fresh it offers to
  **Resume** or **Discard** whatever was last autosaved. A real Save or
  an explicit "Save as Draft" clears the autosave immediately - it's
  only there to protect against never reaching either one. It's one
  slot per entry type (`DatabaseHelper.DRAFT_TYPE_*`), overwritten in
  place rather than piling up, stored in the same `drafts` table as a
  real parked draft but flagged `is_autosave=1` so it never shows up in
  the Drafts list itself. Being the automation's own safety net rather
  than a business record the user created, it's deliberately **not**
  part of the Vyapar backup round-trip, same reasoning as the reorder
  suggestion log and Recently Deleted.
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
  actually need to nag. The Export Vyapar Backup screen also shows a
  visible, always-there **backup health check** status line above the
  Export button ("Last successful backup: X days ago" / "Never backed
  up yet"), not just the silent notification - same 7-day threshold,
  turning red once that overdue (`BackupReminderNotifier.
  getDaysSinceLastBackup()`).
- **Cloud Backup (Google Drive)** — a "Cloud Backup" card on the Export
  Vyapar Backup screen with a single **Connect Google Drive** button.
  This app has no Google Sign-In/Drive API Maven dependency - AIDE's
  on-device build can't reliably resolve a new one (same reasoning as
  `SimpleBarChartView` avoiding a charting library) - so "connect" means
  picking a folder through the system's own storage chooser
  (`Intent.ACTION_OPEN_DOCUMENT_TREE`), which already lets the user
  navigate into "Drive" and pick/create a folder under whichever Google
  account (`mujeerahmed001@gmail.com`) is signed into the Drive app on
  the device, then persisting read/write access to exactly that folder
  (`takePersistableUriPermission`, `CloudBackupSettings`). Once
  connected, every backup - the manual "Export to .vyb File" button
  and every `AutoBackupScheduler` run alike (the latter fires roughly
  every 7 days, armed on every app open by `AutoBackupScheduler.
  ensureScheduled()`) - also copies a timestamped `.vyb` into that
  folder (`CloudBackupWriter`, built on plain framework
  `DocumentsContract` calls, not a third-party Drive SDK), on top of,
  never instead of, wherever the user already saves/writes it. Once
  connected, the Cloud Backup card shows its own **Last cloud backup:
  X days ago** status line (red once 7+ days overdue, same threshold
  as the local backup health check above it) - proof the automatic
  side is actually landing in Drive, not just that a folder was picked
  once (`CloudBackupSettings.getLastBackupAt()`, updated by
  `CloudBackupWriter` on every successful copy, manual or automatic). A
  **Disconnect** option (the same button, relabeled once connected)
  releases the permission and stops future backups from copying there,
  without deleting anything already uploaded. The connected folder
  choice itself is this device's own configuration, not business data,
  so it is deliberately **not** part of the Vyapar backup round-trip -
  same reasoning as Reorder Settings. A fourth, `ReorderDigestNotifier`, checks weekly
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
Budget Planner (approved feature list row #77 - a dedicated planning
screen, distinct from Cash Projection's one-line "What If" below: the
same idea - what buying everything on the current Reorder List would
cost - but broken down by supplier, each with its own subtotal and
item count, against the effective cash limit Reorder Settings already
uses for its own convert-to-draft warning, so the two screens never
disagree about what's affordable; purely a planning view, nothing here
commits anything) ·
Scenario Check (approved feature list row #78 - "what if I stocked X%
more of category Y" one-shot calculator: pick a category and a
percentage, get a straight-line estimate - extra units (current
category stock x that %), extra cash needed (at the category's stock-
weighted average purchase price), and estimated extra profit, from
that category's own recent sales speed and margin over Reorder
Settings' sales-speed window; shows "not enough sales history to
estimate" rather than a false zero when the category hasn't sold
anything in that window - see `DatabaseHelper.getCategoryScenarioEstimate()`)
·
Min Order Quantities (approved feature list row #22 - flashcard-style
bulk setter, one item at a time with a dialer-style +/- stepper instead
of a keyboard, for `items.min_order_qty` - the explicit per-item floor
the Reorder List's suggestion engine now respects. A card's starting
value is whatever's already explicitly set, or otherwise
`DatabaseHelper.getEffectiveMinOrderQty()`'s own suggested default: 6
for a shoe item, else whatever quantity that item was last Purchased
in, else 1) ·
Cash Projection (today's cash balance projected across a from/to
period you pick, defaulting to today through +30 days - adds Sales/
Purchases due in that exact window, plus recurring expenses expected
to fall in it, simulated day by day with the same due-check logic
that actually generates them, not an average, plus an Estimated New
Sales figure from the shop's own trailing average daily sales x the
period length (the same velocity window Reorder Settings already
uses); three checkboxes - Sales/Expenses/Reorder, all on by default -
toggle each category in or out of the totals without hiding its own
raw figure, just dimming it, so unchecking one still shows the swing
it would cause; a separate "What If" line shows what restocking
everything currently on the Reorder List would cost and what cash
would be left after, since that's not committed yet - see
`DatabaseHelper.getCashProjection()`) ·
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
Profit Alerts (This Period vs the prior period at the item level - a
period spinner picks Month/Week/Quarter/Year, defaulting to This
Month vs Last Month, alongside a shoes/non-shoes filter: Margin
Erosion lists every item that sold in both periods whose margin %
dropped 5+ points, biggest drop first; Biggest Profit Swings lists the
top 10 items by the size of their profit change vs the prior period,
up or down; see Notifications below for the daily alert this same
check also feeds, which always compares Month vs Month regardless of
this screen's own spinner) · Size-Curve Analysis
(shoes only, lifetime not period-based: every shoe size's share of
sales vs its share of current stock - Size is parsed straight out of
each item's name via `ShoeIdentity`, not a variety group, since every
real shoe item here is already its own exact size; a size selling more
than its share of stock is flagged as a stockout risk, one stocked
more than it sells as cash sitting idle, anything within 3 points
either way shown as Balanced) · Expense Ratio Trend (a mode spinner
picks Expenses as a % of Sales (default) or as a % of gross profit
(Sales minus item cost), for each of the last 6 calendar months,
oldest first - a rising ratio means expenses are growing faster than
sales/profit, worth watching even when the totals are individually
growing; both ratios are always computed (`DatabaseHelper.
getExpenseRatioTrend()`) so switching modes just re-renders, no second
query; each month's badge is colored by whether its ratio improved or
worsened vs the month before it) · Win-Back List (every party whose
last Sale/Purchase/Payment/Expense/Party Transfer was at least a
chosen threshold ago - 30/60/90/180+ days quiet - excluding a party
we've only ever bought from (a supplier, with Purchases but zero
Sales: no sales relationship there to win back) and one that's never
transacted at all; a sort spinner picks Longest Quiet First (default)
or Highest Profit First, the latter by each party's lifetime sales
profit, to call the highest-value quiet customers first)

**What To Do Today** (Dashboard Tools card) is a single screen combining
three of the reports above - Reorder Needed, Payments Due, and
Slow-Moving Stock - via `DatabaseHelper.getTodayActionSummary()`, which
just calls each report's own existing method rather than recomputing
anything. Each section shows up to 5 rows and a "View All" link to that
report's full screen for the rest; a section with nothing to show is
hidden entirely, and an "All caught up" message shows once all three
are empty.

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
above has far fewer entries and renders exactly as before. Below the
chart, a spinner (`DatabaseHelper.getSalesTrendComparison()`) picks
the comparison shown below it: **Today vs Last Week** (the default -
today's sales so far against the same calendar weekday one week ago,
since a Monday is naturally busier or quieter than a Sunday, so
comparing to a week ago rather than yesterday isolates a real trend
instead of just that day-of-week mismatch), **This Week vs Last
Week**, or **This Month vs Last Month** - the latter two compare an
*elapsed* window (this week/month so far) against the same number of
elapsed days of the prior one, never a partial period against a full
one. The percent change is hidden (shown as "No sales in last
week/month to compare") when the comparison period had no sales at
all, since "0% change from zero" would be misleading. Below
that, a streak badge (`DatabaseHelper.getSalesStreak()`) counts how
many days in a row have had at least one sale, plus the best streak
ever reached - a small motivational nudge, not a report. Today doesn't
break the current streak just because no sale has been made yet this
minute; it only actually breaks once a full day passes with nothing
sold.

**Dashboard Favorites**: long-press any Tool Row on the Dashboard
(either the Modules card - Parties/Items/Purchases/Sales/Payments/
Expenses/Reports - or the Tools card below it) to pin it into a new
Favorites card at the very top of the Dashboard, above the cash
summary; long-pressing a pinned row there unpins it the same way. Pin
order is preserved across app opens (`DashboardFavorites`,
`SharedPreferences`); the Favorites card and its label are hidden
entirely when nothing is pinned. The same card also shows every report
pinned from the Reports screen's own long-press-to-favorite (so any
report - not just a Tool Row - can become a Dashboard shortcut too):
`MainActivity#refreshFavoritesCard()` merges `DashboardFavorites` with
`ReportFavorites`, resolving a report's key/target through
`Reportsactivity.getLabelForFavoriteKey()`/`getTargetForFavoriteKey()`
rather than duplicating that lookup table.

**Customize Dashboard** (Dashboard Tools card): reorders and/or hides
the Dashboard's three glanceable info-card blocks - Favorites, Cash
Summary, Sales Trend - via `DashboardCardOrder`/`DashboardCustomizeActivity`
(plain Up/Down buttons per row, no drag-and-drop library). Deliberately
scoped to just those three; the Modules/Tools navigation cards below
them are left out, since letting the user hide core navigation could
lock them out of the rest of the app with no way back in.
`MainActivity#applyDashboardCardOrder()` physically re-parents the
three card views into the saved order on every resume, so a change
takes effect immediately on returning from that settings screen.

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
report under Reports (all 19, plus the Reports list itself) now has
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
