package com.mujeer.businesserp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;

// =====================
// Persists which reports show as live mini-chart widget cards on the
// Dashboard, and their display order - see DashboardChartWidgetLoader
// for the actual per-report chart data and DashboardChartWidgetsActivity
// for the picker screen. Same ordered-list + per-key-flag shape as
// DashboardCardOrder (see its own comment for why a plain Set can't
// hold display order), just keyed by DashboardChartWidgetLoader's
// SUPPORTED_KEYS instead of the three fixed glanceable cards - and
// opt-IN by default (a report's chart being added to its own page does
// not also turn it into a Dashboard widget; the user picks explicitly
// from DashboardChartWidgetsActivity), unlike DashboardCardOrder's
// opt-out default for its three always-shown cards.
// =====================
public class DashboardChartWidgets {

	private static final String PREFS = "dashboard_chart_widgets";
	private static final String KEY_ORDER = "order";
	private static final String KEY_SHOWN_PREFIX = "shown_";

	// Comma-separated report keys, most-visible-first. Any key missing
	// from a saved order (a future newly-supported report) is appended
	// at the end, so an old saved order never silently drops a newer
	// one from this picker screen.
	public static ArrayList<String> getOrder(Context context) {

		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		String saved = prefs.getString(KEY_ORDER, null);

		ArrayList<String> order = new ArrayList<String>();

		if (saved != null && saved.length() > 0) {

			for (String key : saved.split(",")) {

				if (Arrays.asList(DashboardChartWidgetLoader.SUPPORTED_KEYS).contains(key) && !order.contains(key)) {
					order.add(key);
				}
			}
		}

		for (String key : DashboardChartWidgetLoader.SUPPORTED_KEYS) {
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

	public static boolean isVisible(Context context, String key) {

		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			.getBoolean(KEY_SHOWN_PREFIX + key, false);
	}

	public static void setVisible(Context context, String key, boolean visible) {

		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			.edit()
			.putBoolean(KEY_SHOWN_PREFIX + key, visible)
			.apply();
	}

	// Whether at least one widget is currently turned on - lets
	// MainActivity collapse the whole Chart Widgets section instead of
	// showing an empty heading when nothing's picked yet.
	public static boolean hasAnyVisible(Context context) {

		for (String key : DashboardChartWidgetLoader.SUPPORTED_KEYS) {

			if (isVisible(context, key)) {
				return true;
			}
		}

		return false;
	}
}
