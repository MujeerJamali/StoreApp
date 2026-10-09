package com.mujeer.businesserp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;

// =====================
// The last few Global Search queries that actually led somewhere (a
// tapped result), most recent first, so reopening Search can offer
// them back instead of starting from a blank box every time. Recorded
// only when a result is tapped - not on every keystroke, which would
// fill this with partial text ("s", "sh", "sho") as the user types -
// so what's remembered is always a search that was actually useful.
// Stored as one newline-joined string in its own SharedPreferences,
// same reasoning as FilterMemory/ReorderSettings for not needing a
// database table.
// =====================
class RecentSearches {

	private static final String PREFS_NAME = "recent_searches";
	private static final String KEY_QUERIES = "queries";
	private static final int MAX_ENTRIES = 8;

	static ArrayList<String> getRecent(Context context) {

		String stored = prefs(context).getString(KEY_QUERIES, "");

		ArrayList<String> list = new ArrayList<String>();

		if (stored.isEmpty()) {
			return list;
		}

		for (String query : stored.split("\n")) {

			if (!query.trim().isEmpty()) {
				list.add(query);
			}
		}

		return list;
	}

	static void record(Context context, String query) {

		String trimmed = query.trim();

		if (trimmed.length() < 2) {
			return;
		}

		ArrayList<String> list = getRecent(context);

		for (int i = list.size() - 1; i >= 0; i--) {

			if (list.get(i).equalsIgnoreCase(trimmed)) {
				list.remove(i);
			}
		}

		list.add(0, trimmed);

		while (list.size() > MAX_ENTRIES) {
			list.remove(list.size() - 1);
		}

		save(context, list);
	}

	static void clear(Context context) {
		prefs(context).edit().remove(KEY_QUERIES).apply();
	}

	private static void save(Context context, ArrayList<String> list) {

		StringBuilder joined = new StringBuilder();

		for (int i = 0; i < list.size(); i++) {

			if (i > 0) {
				joined.append("\n");
			}

			joined.append(list.get(i));
		}

		prefs(context).edit().putString(KEY_QUERIES, joined.toString()).apply();
	}

	private static SharedPreferences prefs(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}
}
