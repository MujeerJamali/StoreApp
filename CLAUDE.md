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
