package com.mujeer.businesserp;

import android.content.Context;
import android.content.SharedPreferences;

// =====================
// Remembers the last filter/sort/window choice made on a report screen
// (a Spinner position, almost always) so reopening that report starts
// right back where the user left it instead of resetting to "All
// Items"/the default sort every single time - the practical version of
// a "favorite filter" for a solo-owner shop that's almost always going
// to pick the same filter anyway. One shared SharedPreferences file,
// keyed by screenKey + fieldKey so every report's memory lives
// independently - never a per-row business record, same reasoning as
// ReorderSettings/LowStockNotifier's own prefs.
// =====================
class FilterMemory {

	private static final String PREFS_NAME = "filter_memory";

	private static SharedPreferences prefs(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	static int getInt(Context context, String screenKey, String fieldKey, int defaultValue) {
		return prefs(context).getInt(screenKey + "_" + fieldKey, defaultValue);
	}

	static void setInt(Context context, String screenKey, String fieldKey, int value) {
		prefs(context).edit().putInt(screenKey + "_" + fieldKey, value).apply();
	}

	static String getString(Context context, String screenKey, String fieldKey, String defaultValue) {
		return prefs(context).getString(screenKey + "_" + fieldKey, defaultValue);
	}

	static void setString(Context context, String screenKey, String fieldKey, String value) {
		prefs(context).edit().putString(screenKey + "_" + fieldKey, value).apply();
	}
}
