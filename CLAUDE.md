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
- Charts use `SimpleBarChartView` (plain Canvas drawing, no third-party
  library) - `build.gradle` has no charting dependency, and AIDE's
  on-device build has no reliable way to resolve a new Maven dependency,
  so don't add one (e.g. MPAndroidChart). Adding charts to more reports
  is an ongoing, incremental effort ("current, past, and future" per the
  user) - not a one-time checklist item - so pick it up again whenever
  touching a report that would benefit from one, instead of treating a
  partial pass as finished.
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
