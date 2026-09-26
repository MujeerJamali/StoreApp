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
