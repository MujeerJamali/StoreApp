package com.mujeer.businesserp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;

// =====================
// Pinned Dashboard shortcuts - long-press any Tool Row (Modules or
// Tools card) to pin/unpin it; long-pressing a pinned row in the
// Favorites card itself unpins it the same way.
//
// Stored as an ordered, comma-joined string of shortcut keys (the
// pinning order) rather than a plain Set - SharedPreferences'
// StringSet has no guaranteed iteration order, and losing pin order on
// every reload would be a visible regression (favorites jumping
// around between app opens).
// =====================
public class DashboardFavorites {

	private static final String PREFS_NAME = "dashboard_favorites";
	private static final String KEY_ORDER = "favorite_keys_ordered";
	private static final String SEPARATOR = ",";

	public static ArrayList<String> getFavorites(Context context) {

		SharedPreferences prefs =
			context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

		String joined = prefs.getString(KEY_ORDER, "");

		ArrayList<String> list = new ArrayList<String>();

		if (joined == null || joined.trim().length() == 0) {
			return list;
		}

		list.addAll(Arrays.asList(joined.split(SEPARATOR)));

		return list;
	}

	public static boolean isFavorite(Context context, String key) {
		return getFavorites(context).contains(key);
	}

	// Returns true if the key is now pinned (was just added), false if
	// it was just unpinned.
	public static boolean toggleFavorite(Context context, String key) {

		ArrayList<String> list = getFavorites(context);

		boolean nowFavorite;

		if (list.contains(key)) {

			list.remove(key);
			nowFavorite = false;

		} else {

			list.add(key);
			nowFavorite = true;
		}

		SharedPreferences prefs =
			context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

		prefs.edit().putString(KEY_ORDER, join(list)).apply();

		return nowFavorite;
	}

	private static String join(ArrayList<String> list) {

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < list.size(); i++) {

			if (i > 0) {
				sb.append(SEPARATOR);
			}

			sb.append(list.get(i));
		}

		return sb.toString();
	}
}
