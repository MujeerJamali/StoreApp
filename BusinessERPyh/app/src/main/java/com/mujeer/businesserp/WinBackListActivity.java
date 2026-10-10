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
// Party Transfer was at least the selected threshold ago, reusing the
// exact same activity tracking the Parties list's own "Oldest Activity"
// sort already computes (see DatabaseHelper.getWinBackList()). A party
// that's never transacted at all, or one we've only ever bought from (a
// supplier, with Purchases but zero Sales), is deliberately left off -
// there's no customer relationship to win back in either case. Sortable
// by longest-quiet-first (default) or by lifetime sales profit, so the
// quiet customers most worth a call surface first.
// =====================
public class WinBackListActivity extends Activity {

	Spinner spinner_win_back_threshold;
	Spinner spinner_win_back_sort;
	TextView tv_win_back_empty;
	LinearLayout container_win_back_list;

	DatabaseHelper db;

	private static final int[] THRESHOLD_VALUES = {30, 60, 90, 180};
	private static final String[] THRESHOLD_LABELS = {
		"30+ days quiet", "60+ days quiet", "90+ days quiet", "180+ days quiet"
	};

	private static final int[] SORT_VALUES = {
		DatabaseHelper.WINBACK_SORT_DAYS_SINCE, DatabaseHelper.WINBACK_SORT_PROFIT_DESC
	};

	private static final String[] SORT_LABELS = {
		"Longest Quiet First", "Highest Profit First"
	};

	private int selectedThreshold = THRESHOLD_VALUES[0];
	private int selectedSort = SORT_VALUES[0];

	// A background result is only applied if it's still the most recent
	// request by the time it comes back - same convention used
	// throughout the app (e.g. TodayActionsActivity, BudgetPlannerActivity).
	private long loadGeneration = 0;

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
			"away entirely. A party that's never transacted, or one we've only " +
			"ever bought from (a supplier), isn't shown here; there's no " +
			"customer relationship to win back. Sort by how long they've been " +
			"quiet, or by their lifetime profit to call the highest-value " +
			"customers first."
		);

		spinner_win_back_threshold = findViewById(R.id.spinner_win_back_threshold);
		spinner_win_back_sort = findViewById(R.id.spinner_win_back_sort);
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

		ArrayAdapter<String> sortAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, SORT_LABELS
		);

		sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_win_back_sort.setAdapter(sortAdapter);

		spinner_win_back_sort.setOnItemSelectedListener(
			new AdapterView.OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedSort = SORT_VALUES[position];
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

		final long myGeneration = ++loadGeneration;
		final int threshold = selectedThreshold;
		final int sort = selectedSort;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> parties =
						db.getWinBackList(threshold, sort);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyWinBackList(parties);
							}
						}
					);
				}
			}
		).start();
	}

	private void applyWinBackList(ArrayList<HashMap<String, Object>> parties) {

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

			double lifetimeProfit = 0;

			if (party.get("lifetime_profit") != null) {
				lifetimeProfit = (Double) party.get("lifetime_profit");
			}

			int daysSince = (Integer) party.get("days_since");

			tv_detail.setText(
				"Balance: " + AmountFormat.format(balance) +
				" - Lifetime profit: " + AmountFormat.format(lifetimeProfit)
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
