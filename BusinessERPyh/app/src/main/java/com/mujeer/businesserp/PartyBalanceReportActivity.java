package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Every party's balance alongside how long it's been since their last
// Sale/Purchase/Payment/Expense/Transfer (see
// DatabaseHelper.getPartiesWithActivity()) - a zero/non-zero balance
// filter plus the same 6-way sort as the Parties screen (Recent/Oldest
// Activity, Balance High-Low/Low-High, Name A-Z/Z-A), so a balance
// sitting untouched the longest - or any other ordering the Parties
// screen offers - can be found here too.
// =====================
public class PartyBalanceReportActivity extends Activity {

	private Spinner spinner_balance_filter;
	private Spinner spinner_party_sort;

	private TextView tv_no_parties;
	private ListView lv_parties;

	private DatabaseHelper db;

	private static final int[] BALANCE_FILTER_VALUES = {
		DatabaseHelper.PARTY_BALANCE_FILTER_ALL,
		DatabaseHelper.PARTY_BALANCE_FILTER_NONZERO,
		DatabaseHelper.PARTY_BALANCE_FILTER_ZERO
	};

	private static final String[] BALANCE_FILTER_LABELS = {
		"All Parties", "Non-Zero Balance", "Zero Balance"
	};

	// Same sort options, same order, as Partiesactivity's Spinner - kept
	// in sync by hand since each screen owns its own Spinner instance.
	private static final int[] PARTY_SORT_VALUES = {
		DatabaseHelper.PARTY_SORT_LATEST_TXN, DatabaseHelper.PARTY_SORT_LATEST_TXN,
		DatabaseHelper.PARTY_SORT_BALANCE, DatabaseHelper.PARTY_SORT_BALANCE,
		DatabaseHelper.PARTY_SORT_NAME, DatabaseHelper.PARTY_SORT_NAME
	};

	private static final boolean[] PARTY_SORT_ASCENDING = {
		false, true, false, true, true, false
	};

	private static final String[] PARTY_SORT_LABELS = {
		"Recent Activity", "Oldest Activity",
		"Balance: High to Low", "Balance: Low to High",
		"Name: A-Z", "Name: Z-A"
	};

	private int selectedBalanceFilter = DatabaseHelper.PARTY_BALANCE_FILTER_ALL;
	private int selectedPartySort = DatabaseHelper.PARTY_SORT_LATEST_TXN;
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

		spinner_balance_filter = findViewById(R.id.spinner_balance_filter);
		spinner_party_sort = findViewById(R.id.spinner_party_sort);

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

		ArrayAdapter<String> balanceFilterAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, BALANCE_FILTER_LABELS
		);

		balanceFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_balance_filter.setAdapter(balanceFilterAdapter);

		spinner_balance_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedBalanceFilter = BALANCE_FILTER_VALUES[position];
					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		ArrayAdapter<String> partySortAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, PARTY_SORT_LABELS
		);

		partySortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_party_sort.setAdapter(partySortAdapter);

		spinner_party_sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedPartySort = PARTY_SORT_VALUES[position];
					selectedSortAscending = PARTY_SORT_ASCENDING[position];

					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		loadReport();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
	}

	private void loadReport() {

		final int sort_forQuery = selectedPartySort;
		final int filter_forQuery = selectedBalanceFilter;
		final boolean ascending_forQuery = selectedSortAscending;
		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result = db.getPartiesWithActivity(
						sort_forQuery, ascending_forQuery, filter_forQuery
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
