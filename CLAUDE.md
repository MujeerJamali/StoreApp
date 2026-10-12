# Working rules for this repo

- When fixing a bug, don't stop at the one reported case. Search the whole
  codebase for every other place the same pattern exists (same kind of
  parsing, same kind of assumption, same kind of call) and fix those too
  before considering the fix done. Example: the Purchase-save crash was
  caused by an unguarded `Double.parseDouble()` on user/import input - the
  fix pass audited every `Double.parseDouble()`/`Integer.parseInt()` call
  site in the project, not just that one.
- This sandbox has no Android SDK/Gradle wrapper, so real compilation
  isn't possible here. Substitute rigorous static checks instead: brace/
  paren balance, XML well-formedness, R.id cross-referencing - and say so
  explicitly rather than implying the app was actually built or run.
- Keep README.md current. Whenever a change adds/removes/renames a
  module, report, or user-facing feature, update the README's Modules/
  Reports sections in the same commit - don't let it drift and require a
  separate catch-up pass later.
- The app is developed via AIDE on-device, not Android Studio. When a
  change needs a full rebuild (new file, new resource, manifest edit,
  build.gradle edit) versus when a plain incremental Run is enough, say
  so explicitly so the user isn't stuck guessing whether to rebuild.
- Keep the Vyapar (`.vyb`) backup round-trip complete. Whenever a change
  adds, renames, or changes the meaning of a table/column that holds
  real user data (a new feature's schema, a new field on an existing
  table), update BOTH `ExportVyaparActivity` and `ImportVyaparActivity`
  in the same commit so a fresh backup actually carries that data and a
  restore actually brings it back - in both directions, not just one.
  Gate a new table/column the same way existing optional ones already
  are (`tableExists`/`columnExists` in `ImportVyaparActivity`) so an
  older backup made before the feature existed still restores cleanly
  instead of failing. This was missed for an entire session's worth of
  features before being caught and fixed in one pass - don't let it
  drift again and require another catch-up audit later.
- Keep the Record of Changes audit trail complete. Whenever a change
  adds a new `DatabaseHelper` write method that changes real user data
  (a new feature's insert/update/delete, not a bulk/import variant or
  an internal step of another action), call `logAction(actionType,
  entityType, label)` from inside that method itself (never from the
  calling Activity - this is what guarantees every caller gets logged,
  the same reasoning `ActionLogActivity`'s own doc comment gives) in
  the same commit, matching the pattern already used throughout
  (Parties/Items/Purchases/Sales/Payments/Expenses/Cash Adjustments/
  Cost Items/Varieties/Wanted Items/Recurring Expenses/Seasonal
  Calendar/explicit Drafts/Recently Deleted's restore-undo-permanent-
  delete actions). Deliberately skip: a bulk/import variant (e.g.
  `insertXBulk()`), autosave, and a save that's bundled into another
  action's own click handler and would just duplicate its log entry
  (e.g. `updateItemLocations()`/`updateItemHolidaySeasonal()`, which
  ride along with the same Save click `updateItem()` already logs).
  When an internal call to another write method would double-log
  something that isn't really a separate user action (e.g.
  `undoSaleEdit()` calling `deleteSale()` as a "remove the post-edit
  row" step, not a real user Delete), give that method a quiet/logIt
  overload instead of letting the inner call log - see
  `deleteSale(String, boolean)`/`deletePurchase(int, boolean)` for the
  pattern. This was built as its own dedicated screen (Record of
  Changes, Dashboard Tools card) deliberately separate from Recently
  Deleted - that screen is for actually restoring/undoing a specific
  Sale/Purchase; this one is a permanent, view-only record with no
  edit/delete UI at all, by design, so never add one. Whenever a new
  `logAction()` call site introduces an `entityType` string that isn't
  already in `DatabaseHelper.ACTION_LOG_ENTITY_TYPES`, add it to that
  array in the same commit - it's the fixed list `ActionLogActivity`'s
  Show filter offers, so a type missing from it can never be selected
  there even though it's really being logged. Reuse an existing entry
  (e.g. map a new draft type to "Sale"/"Purchase"/"Payment"/"Expense"
  the same way `draftEntityLabel()` already does) rather than adding a
  near-duplicate bucket when the new action is really about the same
  kind of thing an existing entry already covers.
- SUPERSEDED (see the next rule) - every filter/toggle row used to be a
  horizontally-scrollable row of buttons; that pattern is being replaced
  app-wide by plain dropdowns. Left here so the reasoning for the change
  is on record, not because the button-row pattern should still be used
  for anything new.
- Every filter/sort/toggle control (report filters, Show/Sort-by rows,
  Excel-style multi-select filters, the Parties list's sort, etc.) is a
  plain `Spinner` styled `@style/FilterSpinner` (a minimalist input-
  style dropdown, not a button) - never a row of buttons, scrollable or
  not, and never wider than it needs to be ("not bigger than
  necessary"). This replaced an earlier button-row convention after the
  user asked for the whole app to be more minimalist. Apply it to every
  new filter, and convert an existing button-row filter to match
  whenever you're touching that screen anyway.
  **Exception for a genuine multi-select** (pick any combination, not
  just one row) - a real `Spinner` can only ever select one row, so it
  physically can't drive this. Use a plain `TextView` styled
  `@style/FilterSpinner` instead (same minimalist look, it just ignores
  the `Spinner`-only `popupBackground` attribute harmlessly) that opens
  an `AlertDialog.setMultiChoiceItems()` on tap and shows a short summary
  of the current selection as its own text ("All Types" / "3 Types
  Selected" / the one type's name when exactly one is picked) - see
  `CashActivity`'s Show filter. Still never a button or a row of
  checkboxes sitting directly on the screen.
- Charts use `SimpleBarChartView`/`SimplePieChartView`/`SimpleLineChartView`
  (plain Canvas drawing, no third-party library, all three sharing the
  same `SimpleBarChartView.Entry(label, value, color)` data-point class)
  - `build.gradle` has no charting dependency, and AIDE's on-device
  build has no reliable way to resolve a new Maven dependency, so don't
  add one (e.g. MPAndroidChart). Pick the type by the data's own shape,
  not by habit: **Bar** for comparing distinct categories/rows (it
  handles a loss via a zero baseline, so it's also the safe fallback);
  **Pie** only for a true share-of-a-whole where you're confident every
  value will be positive (it silently drops non-positive entries and
  renders nothing at all below 2 positive ones - see
  `ProfitSplitReportActivity`/`ShoesVsNonShoesReportActivity` for the
  pattern of wiring BOTH a Pie and a Bar and toggling which is visible,
  for data that's a share-of-a-whole most of the time but can go
  negative); **Line** for an ordered trend over time/periods (also
  renders nothing below 2 points). Don't add a chart at all when a
  report's own default view is a single aggregate number, or has no
  single clean comparable numeric axis - say so plainly rather than
  forcing a misleading chart. Adding/upgrading charts across reports is
  an ongoing, incremental effort ("current, past, and future" per the
  user) - not a one-time checklist item - so pick it up again whenever
  touching a report that would benefit from one or from a better-fitting
  type, instead of treating a partial pass as finished. The Dashboard's
  "live mini-chart widget cards" (`DashboardChartWidgetsActivity`/
  `DashboardChartWidgetLoader`) reuse this same `Entry` class and the
  same per-report `DatabaseHelper` query a report's own full page
  already calls (just with a fixed "today"/shortest-default period) -
  extending that widget picker to a newly-charted report is a small,
  separate `loadXxx()` addition to `DashboardChartWidgetLoader`, not a
  new design.
- Every `SimpleBarChartView`/`SimpleLineChartView` in a layout MUST be
  wrapped in a plain `<HorizontalScrollView>` (the chart itself
  `android:layout_width="wrap_content"` inside it, same
  `android:scrollbars="none"` convention as every other scroll view in
  this app) - never place one directly in a non-scrolling parent. Both
  views compute their own minimum width from their entry count
  (`MIN_SLOT_WIDTH_DP`) and report that as their measured width when
  it's wider than the available space; without a `HorizontalScrollView`
  parent, that wider-than-available width just gets clipped instead of
  becoming scrollable, silently breaking the "always show every value
  and label" guarantee below. Caught from real on-device screenshots:
  Month-over-Month's 6 bars ("Sales (Last)/Sales (This)/Profit
  (Last)/...") overlapping into unreadable text, and Item Monthly
  Rank's 10 item-name labels overlapping into a solid unreadable block
  - both reports already had a chart, the chart just wasn't readable.
  Every bar/point always shows its own value and label now (the
  earlier "thin labels out past 10 entries" approach is gone - it
  still let long labels overlap at any entry count); a label still too
  long for its own slot wraps onto a second line instead of
  overlapping its neighbor (see either view's `wrapLabel()`). This is a
  correctness requirement, not a style preference - a chart whose text
  can't be read is a regression, not a finished feature, whatever its
  data or type.
- Swipe-left-to-reveal Edit/Delete on a list row uses `SwipeRevealLayout`
  (plain View/MotionEvent custom ViewGroup) - not RecyclerView's
  `ItemTouchHelper`. Every list in this app is a ListView/BaseAdapter and
  `build.gradle` has no RecyclerView dependency wired in (same AIDE-
  on-device-build constraint as the no-new-charting-library rule above),
  so don't add one. Currently wired into the Purchases/Sales list
  (`TransactionAdapter`, gated behind `setSwipeEnabled()` so the same
  adapter stays inert where it's reused read-only in Partyviewactivity/
  Itemviewactivity), the Payments list (`PaymentAdapter`), and the
  Expenses list (`ExpenseAdapter`). Like charts and info bubbles, this is
  an incremental rollout, not a one-time checklist item - pick it up again
  for another delete-capable list whenever touching that screen anyway.
  All three already respect a single app-wide on/off switch
  (`SwipeGestureSettings`, surfaced via the Swipe Gesture Settings
  screen) - each adapter's own `setSwipeEnabled(boolean)` hides that
  row's `swipe_actions` panel, which `SwipeRevealLayout.isSwipeEnabled()`
  already treats as "no gesture here at all" (same mechanism
  `TransactionAdapter` already used to stay inert on its read-only
  reuses). Wire any newly-adopted list into this same setting too,
  not just its own `setSwipeEnabled()` plumbing - don't let a new
  adopter silently sit outside the user's own on/off control.
- AIDE's on-device compiler does NOT accept an "effectively final" local
  variable or method parameter captured by an anonymous inner class
  (`new Foo() { ... }`) the way modern desktop javac does under Java 8 -
  it errors with "This variable must be final to be used in a local
  class" unless the variable is explicitly declared `final`. Never rely
  on effectively-final inference in this project: explicitly mark
  `final` anything (a method parameter, a loop variable, a local) that
  gets referenced inside an anonymous `OnClickListener`/`TextWatcher`/
  adapter callback/etc., even though the sandbox's own desktop-side
  static checks won't catch this (AIDE's compiler is stricter here than
  the javac used anywhere else). Caught once in `ReorderListAdapter
  .getView()`, where the `position` parameter was used directly inside
  an anonymous `OnClickListener` without being `final` - the established
  safe pattern elsewhere in the codebase (see every other `Adapter
  .getView()`) is to capture a parameter into a `final` local copy right
  away (e.g. `final HashMap<String, Object> row = list.get(position);`)
  and reference only that local inside any anonymous class, or mark the
  parameter itself `final` in the method signature.
