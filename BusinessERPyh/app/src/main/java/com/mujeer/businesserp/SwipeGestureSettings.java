package com.mujeer.businesserp;

import android.content.Context;
import android.content.SharedPreferences;

// =====================
// Single app-wide on/off switch for the swipe-left-to-reveal Edit/Delete
// gesture (see SwipeRevealLayout) on the Purchases/Sales, Payments, and
// Expenses lists - SwipeGestureSettingsActivity is the settings screen
// that reads/writes this. Defaults true (the gesture's existing
// behavior before this setting existed), so an upgrading install sees
// no change until the user actually turns it off. Each adapter's own
// setSwipeEnabled(boolean) is what actually acts on this value, by
// hiding that row's swipe_actions panel - see SwipeRevealLayout's own
// isSwipeEnabled(), which already treats a hidden actions panel as "no
// gesture here at all," falling every touch straight through to the
// owning ListView's normal OnItemClickListener.
// =====================
public class SwipeGestureSettings {

	private static final String PREFS = "swipe_gesture_settings";
	private static final String KEY_ENABLED = "enabled";

	public static boolean isEnabled(Context context) {

		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			.getBoolean(KEY_ENABLED, true);
	}

	public static void setEnabled(Context context, boolean enabled) {

		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			.edit()
			.putBoolean(KEY_ENABLED, enabled)
			.apply();
	}
}
