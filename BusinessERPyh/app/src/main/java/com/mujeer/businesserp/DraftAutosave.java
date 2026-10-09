package com.mujeer.businesserp;

import java.util.HashMap;

// =====================
// Thin wrapper around DatabaseHelper's saveAutosaveDraft()/
// getAutosaveDraft()/clearAutosaveDraft() - handles the DraftCodec
// encode/decode step so each editor Activity (Transactioneditactivity,
// Paymenteditactivity, Expenseeditactivity) doesn't repeat it, the same
// way DraftCodec itself already centralizes that step for the manual
// "Save as Draft" path. See DatabaseHelper.DATABASE_VERSION's comment
// for why this is a silent, separate-from-DraftsActivity safety net.
// =====================
class DraftAutosave {

    // Skipped (returns without writing) when data is null - an editor's
    // own buildDraftData()-equivalent method returns null when the
    // screen has nothing worth protecting yet (e.g. untouched and
    // blank), so a real autosave from a previous session is never
    // overwritten with an empty one just because the screen briefly
    // looked blank on the way to being filled in again.
    static void save(
        DatabaseHelper db, String type, HashMap<String, Object> data,
        String date, String time) {

        if (data == null) {
            return;
        }

        String encoded = DraftCodec.encode(data);

        if (encoded == null) {
            return;
        }

        db.saveAutosaveDraft(type, encoded, date, time);
    }

    // Null when there's no pending autosave for this type, or its data
    // somehow fails to decode (e.g. corrupted) - either way, treated as
    // "nothing to resume" rather than surfacing an error over it.
    static Pending getPending(DatabaseHelper db, String type) {

        HashMap<String, Object> row = db.getAutosaveDraft(type);

        if (row == null) {
            return null;
        }

        HashMap<String, Object> data = DraftCodec.decode((String) row.get("data"));

        if (data == null) {
            return null;
        }

        Pending pending = new Pending();
        pending.data = data;
        pending.date = (String) row.get("date");
        pending.time = (String) row.get("time");

        return pending;
    }

    static void clear(DatabaseHelper db, String type) {
        db.clearAutosaveDraft(type);
    }

    static class Pending {
        HashMap<String, Object> data;
        String date;
        String time;
    }
}
