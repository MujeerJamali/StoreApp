package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Every party's balance alongside how long it's been since their last
// Sale/Purchase/Payment/Expense/Transfer (see
// DatabaseHelper.getPartiesWithActivity()) - a zero/non-zero balance
// filter plus a sort by that "days since" figure, so the parties whose
// balance has been sitting untouched the longest float to the top.
// =====================
public class PartyBalanceReportActivity extends Activity {

	private Button btn_balance_filter_all;
	private Button btn_balance_filter_nonzero;
	private Button btn_balance_filter_zero;

	private Button btn_due_sort_most_overdue;
	private Button btn_due_sort_least_overdue;

	private TextView tv_no_parties;
	private ListView lv_parties;

	private DatabaseHelper db;

	private int selectedBalanceFilter = DatabaseHelper.PARTY_BALANCE_FILTER_ALL;
	private boolean selectedSortAscending = false;

	private final ArrayList<HashMap<String, Object>> partyList =
		new ArrayList<HashMap<String, Object>>();

	private PartyAdapter adapter;

	// Bumped on every loadReport() call; a background result is only
	// applied if it's still the most recent request by the time it
	// comes back - same convention as the other reports.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.party_balance_report_activity);

		setTitle("Party Balances");

		btn_balance_filter_all = findViewById(R.id.btn_balance_filter_all);
		btn_balance_filter_nonzero = findViewById(R.id.btn_balance_filter_nonzero);
		btn_balance_filter_zero = findViewById(R.id.btn_balance_filter_zero);

		btn_due_sort_most_overdue = findViewById(R.id.btn_due_sort_most_overdue);
		btn_due_sort_least_overdue = findViewById(R.id.btn_due_sort_least_overdue);

		tv_no_parties = findViewById(R.id.tv_no_parties);
		lv_parties = findViewById(R.id.lv_parties);

		db = new DatabaseHelper(this);

		adapter = new PartyAdapter(this, partyList);
		lv_parties.setAdapter(adapter);

		lv_parties.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> party = (HashMap<String, Object>) adapter.getItem(position);
					int partyId = (Integer) party.get("id");

					Intent intent = new Intent(
						PartyBalanceReportActivity.this, Partyviewactivity.class
					);

					intent.putExtra("party_id", partyId);
					startActivity(intent);
				}
			});

		btn_balance_filter_all.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectBalanceFilter(DatabaseHelper.PARTY_BALANCE_FILTER_ALL);
				}
			});

		btn_balance_filter_nonzero.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectBalanceFilter(DatabaseHelper.PARTY_BALANCE_FILTER_NONZERO);
				}
			});

		btn_balance_filter_zero.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectBalanceFilter(DatabaseHelper.PARTY_BALANCE_FILTER_ZERO);
				}
			});

		btn_due_sort_most_overdue.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectSort(false);
				}
			});

		btn_due_sort_least_overdue.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectSort(true);
				}
			});

		loadReport();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
	}

	private void selectBalanceFilter(int filter) {

		selectedBalanceFilter = filter;

		Button[] buttons = {btn_balance_filter_all, btn_balance_filter_nonzero, btn_balance_filter_zero};

		int[] filters = {
			DatabaseHelper.PARTY_BALANCE_FILTER_ALL,
			DatabaseHelper.PARTY_BALANCE_FILTER_NONZERO,
			DatabaseHelper.PARTY_BALANCE_FILTER_ZERO
		};

		for (int i = 0; i < buttons.length; i++) {

			if (filters[i] == filter) {

				buttons[i].setBackgroundResource(R.drawable.bg_button_primary);
				buttons[i].setTextColor(getResources().getColor(R.color.text_on_primary));

			} else {

				buttons[i].setBackgroundResource(R.drawable.bg_button_outline);
				buttons[i].setTextColor(getResources().getColor(R.color.primary));
			}
		}

		loadReport();
	}

	private void selectSort(boolean ascending) {

		selectedSortAscending = ascending;

		if (ascending) {

			btn_due_sort_least_overdue.setBackgroundResource(R.drawable.bg_button_primary);
			btn_due_sort_least_overdue.setTextColor(getResources().getColor(R.color.text_on_primary));
			btn_due_sort_most_overdue.setBackgroundResource(R.drawable.bg_button_outline);
			btn_due_sort_most_overdue.setTextColor(getResources().getColor(R.color.primary));

		} else {

			btn_due_sort_most_overdue.setBackgroundResource(R.drawable.bg_button_primary);
			btn_due_sort_most_overdue.setTextColor(getResources().getColor(R.color.text_on_primary));
			btn_due_sort_least_overdue.setBackgroundResource(R.drawable.bg_button_outline);
			btn_due_sort_least_overdue.setTextColor(getResources().getColor(R.color.primary));
		}

		loadReport();
	}

	private void loadReport() {

		final int filter_forQuery = selectedBalanceFilter;
		final boolean ascending_forQuery = selectedSortAscending;
		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result = db.getPartiesWithActivity(
						DatabaseHelper.PARTY_SORT_LATEST_TXN, ascending_forQuery, filter_forQuery
					);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								partyList.clear();
								partyList.addAll(result);

								adapter.notifyDataSetChanged();

								if (partyList.isEmpty()) {

									tv_no_parties.setVisibility(View.VISIBLE);
									lv_parties.setVisibility(View.GONE);

								} else {

									tv_no_parties.setVisibility(View.GONE);
									lv_parties.setVisibility(View.VISIBLE);
								}
							}
						});
				}
			}).start();
	}
}
