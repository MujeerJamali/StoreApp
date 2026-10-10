package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Onboarding checklist for setting reorder thresholds (approved feature
// list row #69) - every active item still at the default
// reorder_threshold=0 ("not set"), so the user can quickly work through
// them without opening the full Edit Item screen per item. Tapping a
// row prompts for a number and sets it right there; the row then drops
// off the list (it no longer qualifies as "missing") and the progress
// line updates. Purely a setup nudge, not a report - an item correctly
// left at 0 (e.g. one with no plan to ever auto-flag) simply never
// leaves this list, which is expected.
// =====================
public class ReorderThresholdChecklistActivity extends Activity {

	private TextView tv_progress;
	private TextView tv_all_done;
	private LinearLayout container_checklist;

	private DatabaseHelper db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reorder_threshold_checklist_activity);

		tv_progress = findViewById(R.id.tv_progress);
		tv_all_done = findViewById(R.id.tv_all_done);
		container_checklist = findViewById(R.id.container_checklist);

		db = new DatabaseHelper(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadChecklist();
	}

	private void loadChecklist() {

		ArrayList<HashMap<String, Object>> missing = db.getItemsMissingReorderThreshold();
		int totalActive = db.getActiveItemCount();
		int done = totalActive - missing.size();

		tv_progress.setText(done + " of " + totalActive + " items have a threshold set");

		container_checklist.removeAllViews();

		if (missing.isEmpty()) {

			tv_all_done.setVisibility(View.VISIBLE);
			return;
		}

		tv_all_done.setVisibility(View.GONE);

		for (final HashMap<String, Object> item : missing) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_checklist, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText((String) item.get("name"));

			double balance = (Double) item.get("balance");

			tv_detail.setText(
				"Code " + item.get("code") + " - Stock " + AmountFormat.formatPlain(balance)
			);

			tv_badge.setText("Set");
			tv_badge.setTextColor(getResources().getColor(R.color.primary));

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						promptSetThreshold((Integer) item.get("id"), (String) item.get("name"));
					}
				}
			);

			container_checklist.addView(view);
		}
	}

	private void promptSetThreshold(final int itemId, String itemName) {

		final EditText input = new EditText(this);
		input.setHint("e.g. 5");
		input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		input.setPadding(pad, pad, pad, pad);

		new AlertDialog.Builder(this)
			.setTitle("Reorder Threshold")
			.setMessage(
				"\"" + itemName + "\" - stock at or below this number will show up on the " +
				"Reorder List and Low Stock report."
			)
			.setView(input)
			.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						double threshold;

						try {
							threshold = Double.parseDouble(input.getText().toString().trim());
						} catch (Exception e) {
							Toast.makeText(
								ReorderThresholdChecklistActivity.this,
								"Enter a valid number", Toast.LENGTH_SHORT
							).show();
							return;
						}

						if (threshold <= 0) {
							Toast.makeText(
								ReorderThresholdChecklistActivity.this,
								"Enter a number greater than 0", Toast.LENGTH_SHORT
							).show();
							return;
						}

						db.setItemReorderThreshold(itemId, threshold);
						loadChecklist();
					}
				})
			.setNegativeButton("Skip", null)
			.show();
	}
}
