package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;

// =====================
// Single app-wide on/off switch for the swipe-left-to-reveal Edit/Delete
// gesture on the Purchases/Sales, Payments, and Expenses lists (see
// SwipeGestureSettings) - writes immediately on toggle, same as
// CloudBackupSettings' connect/disconnect, since there's only the one
// setting here and a separate Save button would just be one more tap.
// Turning it off doesn't remove Edit/Delete from those lists - it falls
// them back to this app's pre-swipe convention (tap to open, long-press
// to delete with confirmation), which every one of those three screens
// still wires up independently of swipe (see e.g. Transactionactivity's
// own lv_transactions.setOnItemLongClickListener).
// =====================
public class SwipeGestureSettingsActivity extends Activity {

	private CheckBox cb_swipe_gestures_enabled;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.swipe_gesture_settings_activity);

		setTitle("Swipe Gesture Settings");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Swipe Gesture Settings",
			"Turns the swipe-left-to-reveal Edit/Delete gesture on or off for the Purchases/Sales, Payments, and Expenses lists. Off doesn't remove Edit/Delete from those screens - it falls them back to tap-to-open and long-press-to-delete (with a confirmation), the same as every other list in the app."
		);

		cb_swipe_gestures_enabled = findViewById(R.id.cb_swipe_gestures_enabled);
		cb_swipe_gestures_enabled.setChecked(SwipeGestureSettings.isEnabled(this));

		cb_swipe_gestures_enabled.setOnCheckedChangeListener(
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					SwipeGestureSettings.setEnabled(SwipeGestureSettingsActivity.this, isChecked);
				}
			}
		);
	}
}
