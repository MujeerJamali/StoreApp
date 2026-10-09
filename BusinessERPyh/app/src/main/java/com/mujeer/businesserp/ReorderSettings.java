package com.mujeer.businesserp;

import android.content.Context;
import android.content.SharedPreferences;

// Every input the reorder-suggestion formula uses (see
// DatabaseHelper.getReorderSuggestions()), stored as its own SharedPreferences
// rather than a database table - this is a single set of global knobs, not a
// per-row business record, same reasoning as LowStockNotifier/
// BackupReminderNotifier's own prefs. Each getter's default is what the
// formula uses until the user visits ReorderSettingsActivity and changes it.
class ReorderSettings {

	private static final String PREFS_NAME = "reorder_settings";

	private static final String KEY_VELOCITY_WINDOW_DAYS = "velocity_window_days";
	private static final String KEY_SAFETY_STOCK_PERCENT = "safety_stock_percent";
	private static final String KEY_DEFAULT_LEAD_TIME_DAYS = "default_lead_time_days";
	private static final String KEY_MIN_ORDER_QTY = "min_order_qty";
	private static final String KEY_MAX_ORDER_QTY = "max_order_qty";
	private static final String KEY_CASH_AWARE_ENABLED = "cash_aware_enabled";
	private static final String KEY_MANUAL_CASH_OVERRIDE_ENABLED = "manual_cash_override_enabled";
	private static final String KEY_MANUAL_CASH_OVERRIDE_AMOUNT = "manual_cash_override_amount";
	private static final String KEY_NON_SHOE_TURNOVER_MULTIPLIER = "non_shoe_turnover_multiplier";

	// Defaults - chosen to be reasonable for a small single-store shop
	// re-ordering every week or two, not tuned to any real sales data yet.
	static final int DEFAULT_VELOCITY_WINDOW_DAYS = 30;
	static final double DEFAULT_SAFETY_STOCK_PERCENT = 20;
	static final int DEFAULT_LEAD_TIME_DAYS = 7;
	static final double DEFAULT_MIN_ORDER_QTY = 1;
	static final double DEFAULT_MAX_ORDER_QTY = 0; // 0 = no cap
	static final boolean DEFAULT_CASH_AWARE_ENABLED = true;

	// This shop's general merchandise (Clothes/Toys/Home/Tools) naturally
	// turns over slower than its shoes (70% of volume, fast-moving) - a
	// tool sitting 90 days isn't "dead stock" the way a shoe would be at
	// that age. 3.0 means a non-shoe item gets 3x as long before Slow-
	// Moving Stock/Discount This Week/Dead Stock Aging treat it the same
	// as a shoe would at a given cutoff - see
	// DatabaseHelper.getSlowMovingStock().
	static final double DEFAULT_NON_SHOE_TURNOVER_MULTIPLIER = 3.0;

	private static SharedPreferences prefs(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}

	// How many past days of sales the velocity (average units sold/day)
	// is calculated over.
	static int getVelocityWindowDays(Context context) {
		return prefs(context).getInt(KEY_VELOCITY_WINDOW_DAYS, DEFAULT_VELOCITY_WINDOW_DAYS);
	}

	static void setVelocityWindowDays(Context context, int days) {
		prefs(context).edit().putInt(KEY_VELOCITY_WINDOW_DAYS, days).apply();
	}

	// Extra buffer on top of pure lead-time demand, as a percent (20 = 20%).
	static double getSafetyStockPercent(Context context) {
		return prefs(context).getFloat(
			KEY_SAFETY_STOCK_PERCENT, (float) DEFAULT_SAFETY_STOCK_PERCENT);
	}

	static void setSafetyStockPercent(Context context, double percent) {
		prefs(context).edit().putFloat(KEY_SAFETY_STOCK_PERCENT, (float) percent).apply();
	}

	// Used when an item has no supplier-specific lead time of its own
	// (there is no such per-item field yet - every item uses this same
	// default lead time for now).
	static int getDefaultLeadTimeDays(Context context) {
		return prefs(context).getInt(KEY_DEFAULT_LEAD_TIME_DAYS, DEFAULT_LEAD_TIME_DAYS);
	}

	static void setDefaultLeadTimeDays(Context context, int days) {
		prefs(context).edit().putInt(KEY_DEFAULT_LEAD_TIME_DAYS, days).apply();
	}

	static double getMinOrderQty(Context context) {
		return prefs(context).getFloat(KEY_MIN_ORDER_QTY, (float) DEFAULT_MIN_ORDER_QTY);
	}

	static void setMinOrderQty(Context context, double qty) {
		prefs(context).edit().putFloat(KEY_MIN_ORDER_QTY, (float) qty).apply();
	}

	// 0 = no maximum.
	static double getMaxOrderQty(Context context) {
		return prefs(context).getFloat(KEY_MAX_ORDER_QTY, (float) DEFAULT_MAX_ORDER_QTY);
	}

	static void setMaxOrderQty(Context context, double qty) {
		prefs(context).edit().putFloat(KEY_MAX_ORDER_QTY, (float) qty).apply();
	}

	// Whether suggestions get capped/flagged against cash-in-hand at all.
	static boolean isCashAwareEnabled(Context context) {
		return prefs(context).getBoolean(KEY_CASH_AWARE_ENABLED, DEFAULT_CASH_AWARE_ENABLED);
	}

	static void setCashAwareEnabled(Context context, boolean enabled) {
		prefs(context).edit().putBoolean(KEY_CASH_AWARE_ENABLED, enabled).apply();
	}

	// When disabled (default), cash-awareness uses the app's own real
	// getCashBalance(). When enabled, it uses manualCashOverrideAmount
	// instead - for when the real cash-in-hand figure doesn't reflect what
	// the user actually wants to allocate to restocking.
	static boolean isManualCashOverrideEnabled(Context context) {
		return prefs(context).getBoolean(KEY_MANUAL_CASH_OVERRIDE_ENABLED, false);
	}

	static void setManualCashOverrideEnabled(Context context, boolean enabled) {
		prefs(context).edit().putBoolean(KEY_MANUAL_CASH_OVERRIDE_ENABLED, enabled).apply();
	}

	static double getManualCashOverrideAmount(Context context) {
		return prefs(context).getFloat(KEY_MANUAL_CASH_OVERRIDE_AMOUNT, 0f);
	}

	static void setManualCashOverrideAmount(Context context, double amount) {
		prefs(context).edit().putFloat(KEY_MANUAL_CASH_OVERRIDE_AMOUNT, (float) amount).apply();
	}

	// How many times longer a non-shoe item gets before Slow-Moving
	// Stock/Discount This Week/Dead Stock Aging treat it the same as a
	// shoe would at a given cutoff - see DatabaseHelper.getSlowMovingStock().
	static double getNonShoeTurnoverMultiplier(Context context) {
		return prefs(context).getFloat(
			KEY_NON_SHOE_TURNOVER_MULTIPLIER, (float) DEFAULT_NON_SHOE_TURNOVER_MULTIPLIER);
	}

	static void setNonShoeTurnoverMultiplier(Context context, double multiplier) {
		prefs(context).edit().putFloat(KEY_NON_SHOE_TURNOVER_MULTIPLIER, (float) multiplier).apply();
	}

	// The actual cash figure the reorder engine should respect right now,
	// or Double.MAX_VALUE if cash-awareness is turned off entirely (i.e.
	// never cap/flag anything).
	static double getEffectiveCashLimit(Context context, double realCashBalance) {

		if (!isCashAwareEnabled(context)) {
			return Double.MAX_VALUE;
		}

		if (isManualCashOverrideEnabled(context)) {
			return getManualCashOverrideAmount(context);
		}

		return realCashBalance;
	}
}
