package com.mujeer.businesserp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;

// =====================
// Persists the Dashboard's top three info cards' order and per-card
// visibility - approved feature "Customizable dashboard - pick which
// cards show first". Deliberately scoped to just Favorites/Cash
// Summary/Sales Trend, not the Modules/Tools navigation cards below
// them - those are primary navigation, not glanceable widgets, and
// letting the user hide them entirely would risk locking themselves
// out of the rest of the app with no way back in. See
// DashboardCustomizeActivity (the settings screen) and
// MainActivity#applyDashboardCardOrder() (where this is read back and
// applied by physically reordering the card views).
// =====================
public class DashboardCardOrder {

	public static final String CARD_FAVORITES = "favorites";
	public static final String CARD_CASH_SUMMARY = "cash_summary";
	public static final String CARD_SALES_TREND = "sales_trend";

	public static final String[] ALL_CARDS = {
		CARD_FAVORITES, CARD_CASH_SUMMARY, CARD_SALES_TREND
	};

	public static final String[] ALL_CARD_LABELS = {
		"Favorites", "Cash Summary", "Sales Trend"
	};

	private static final String PREFS = "dashboard_card_order";
	private static final String KEY_ORDER = "order";
	private static final String KEY_HIDDEN_PREFIX = "hidden_";

	// Comma-separated card keys, most-visible-first. Any key missing
	// from a saved order (e.g. a future new card) is appended at the
	// end, so an old saved order never silently drops a newer card.
	public static ArrayList<String> getOrder(Context context) {

		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		String saved = prefs.getString(KEY_ORDER, null);

		ArrayList<String> order = new ArrayList<String>();

		if (saved != null && saved.length() > 0) {

			for (String key : saved.split(",")) {

				if (Arrays.asList(ALL_CARDS).contains(key) && !order.contains(key)) {
					order.add(key);
				}
			}
		}

		for (String key : ALL_CARDS) {
			if (!order.contains(key)) {
				order.add(key);
			}
		}

		return order;
	}

	public static void saveOrder(Context context, ArrayList<String> order) {

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < order.size(); i++) {

			if (i > 0) {
				sb.append(",");
			}

			sb.append(order.get(i));
		}

		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			.edit()
			.putString(KEY_ORDER, sb.toString())
			.apply();
	}

	public static boolean isVisible(Context context, String cardKey) {

		return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			.getBoolean(KEY_HIDDEN_PREFIX + cardKey, false);
	}

	public static void setVisible(Context context, String cardKey, boolean visible) {

		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			.edit()
			.putBoolean(KEY_HIDDEN_PREFIX + cardKey, !visible)
			.apply();
	}
}
