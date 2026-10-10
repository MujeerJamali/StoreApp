package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Win-Back List report (approved feature list row #87, "Haven't visited
// in a while") - every party whose last Sale/Purchase/Payment/Expense/
// Party Transfer was at least the selected threshold ago, longest-gone
// first, reusing the exact same activity tracking the Parties list's own
// "Oldest Activity" sort already computes (see
// DatabaseHelper.getWinBackList()). A party that's never transacted at
// all is deliberately left off - there's no relationship to win back.
// =====================
public class WinBackListActivity extends Activity {

	Spinner spinner_win_back_threshold;
	TextView tv_win_back_empty;
	LinearLayout container_win_back_list;

	DatabaseHelper db;

	private static final int[] THRESHOLD_VALUES = {30, 60, 90, 180};
	private static final String[] THRESHOLD_LABELS = {
		"30+ days quiet", "60+ days quiet", "90+ days quiet", "180+ days quiet"
	};

	private int selectedThreshold = THRESHOLD_VALUES[0];

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.win_back_list_activity);

		setTitle("Win-Back List");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Win-Back List",
			"Every party whose last Sale/Purchase/Payment/Expense was at least " +
			"this many days ago - worth a call or a reminder before they drift " +
			"away entirely. A party that's never transacted isn't shown here; " +
			"there's no relationship yet to win back."
		);

		spinner_win_back_threshold = findViewById(R.id.spinner_win_back_threshold);
		tv_win_back_empty = findViewById(R.id.tv_win_back_empty);
		container_win_back_list = findViewById(R.id.container_win_back_list);

		db = new DatabaseHelper(this);

		ArrayAdapter<String> thresholdAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, THRESHOLD_LABELS
		);

		thresholdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_win_back_threshold.setAdapter(thresholdAdapter);

		spinner_win_back_threshold.setOnItemSelectedListener(
			new AdapterView.OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedThreshold = THRESHOLD_VALUES[position];
					loadWinBackList();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			}
		);

		loadWinBackList();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadWinBackList();
	}

	private void loadWinBackList() {

		ArrayList<HashMap<String, Object>> parties = db.getWinBackList(selectedThreshold);

		container_win_back_list.removeAllViews();

		if (parties.isEmpty()) {

			tv_win_back_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_win_back_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> party : parties) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_win_back_list, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText((String) party.get("name"));

			double balance = 0;

			if (party.get("balance") != null) {
				balance = (Double) party.get("balance");
			}

			int daysSince = (Integer) party.get("days_since");

			tv_detail.setText(
				"Balance: " + AmountFormat.format(balance)
			);

			tv_badge.setText(daysSince + "d");
			tv_badge.setTextColor(getResources().getColor(R.color.warning));

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							WinBackListActivity.this, Partyviewactivity.class
						);

						intent.putExtra("party_id", (Integer) party.get("id"));

						startActivity(intent);
					}
				}
			);

			container_win_back_list.addView(view);
		}
	}
}
