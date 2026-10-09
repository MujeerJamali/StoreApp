package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// One search box for Parties/Items/Invoice numbers (Sale or Purchase),
// reached from the Dashboard's own search box, which just opens this
// screen rather than searching inline on an already-busy Dashboard.
// Parties/Items reuse their existing full-list DB methods, filtered
// client-side with the same SearchUtils every other list screen's
// search box already uses; Invoices get their own query (see
// DatabaseHelper.searchTransactionsByInvoice()) since they span two
// tables with no existing full-list method to reuse. Each of the
// three sections is hidden independently when it has no matches, and
// the whole results area is hidden until something is typed. While
// the box is empty, a Recent Searches list (see RecentSearches) offers
// the last few queries that actually led to a tapped result - tapping
// one re-runs it; "Clear" wipes the list.
// =====================
public class GlobalSearchActivity extends Activity {

	private static final int TYPE_PURCHASE = 0;
	private static final int TYPE_SALE = 1;

	private EditText et_global_search;
	private TextView tv_global_search_empty;
	private View container_results;

	private View container_recent_searches;
	private LinearLayout container_recent_searches_list;
	private TextView tv_clear_recent_searches;

	private TextView tv_parties_label;
	private ListView lv_parties;
	private final ArrayList<HashMap<String, Object>> partyResults =
		new ArrayList<HashMap<String, Object>>();
	private GlobalSearchAdapter partyAdapter;

	private TextView tv_items_label;
	private ListView lv_items;
	private final ArrayList<HashMap<String, Object>> itemResults =
		new ArrayList<HashMap<String, Object>>();
	private GlobalSearchAdapter itemAdapter;

	private TextView tv_invoices_label;
	private ListView lv_invoices;
	private final ArrayList<HashMap<String, Object>> invoiceResults =
		new ArrayList<HashMap<String, Object>>();
	private GlobalSearchAdapter invoiceAdapter;

	private DatabaseHelper db;

	// A background result is only applied if it's still the most
	// recent request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.global_search_activity);

		setTitle("Search");

		et_global_search = findViewById(R.id.et_global_search);
		tv_global_search_empty = findViewById(R.id.tv_global_search_empty);
		container_results = findViewById(R.id.container_results);

		container_recent_searches = findViewById(R.id.container_recent_searches);
		container_recent_searches_list = findViewById(R.id.container_recent_searches_list);
		tv_clear_recent_searches = findViewById(R.id.tv_clear_recent_searches);

		tv_parties_label = findViewById(R.id.tv_parties_label);
		lv_parties = findViewById(R.id.lv_parties);

		tv_items_label = findViewById(R.id.tv_items_label);
		lv_items = findViewById(R.id.lv_items);

		tv_invoices_label = findViewById(R.id.tv_invoices_label);
		lv_invoices = findViewById(R.id.lv_invoices);

		db = new DatabaseHelper(this);

		partyAdapter = new GlobalSearchAdapter(this, partyResults);
		lv_parties.setAdapter(partyAdapter);

		itemAdapter = new GlobalSearchAdapter(this, itemResults);
		lv_items.setAdapter(itemAdapter);

		invoiceAdapter = new GlobalSearchAdapter(this, invoiceResults);
		lv_invoices.setAdapter(invoiceAdapter);

		lv_parties.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = partyResults.get(position);

					RecentSearches.record(GlobalSearchActivity.this, et_global_search.getText().toString());

					Intent intent = new Intent(GlobalSearchActivity.this, Partyviewactivity.class);
					intent.putExtra("party_id", (Integer) row.get("party_id"));

					startActivity(intent);
				}
			});

		lv_items.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = itemResults.get(position);

					RecentSearches.record(GlobalSearchActivity.this, et_global_search.getText().toString());

					Intent intent = new Intent(GlobalSearchActivity.this, Itemviewactivity.class);
					intent.putExtra("item_id", (Integer) row.get("item_id"));

					startActivity(intent);
				}
			});

		lv_invoices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = invoiceResults.get(position);

					RecentSearches.record(GlobalSearchActivity.this, et_global_search.getText().toString());

					Intent intent = new Intent(GlobalSearchActivity.this, Transactionviewactivity.class);

					intent.putExtra(
						"transaction_type",
						"sale".equals(row.get("transaction_type")) ? TYPE_SALE : TYPE_PURCHASE
					);

					intent.putExtra("transaction_id", (Integer) row.get("transaction_id"));

					startActivity(intent);
				}
			});

		tv_clear_recent_searches.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					RecentSearches.clear(GlobalSearchActivity.this);
					refreshRecentSearches();
				}
			});

		et_global_search.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					runSearch(s.toString());
				}
			});

		refreshRecentSearches();
	}

	// Shown only while the search box is empty - a query in progress
	// hides this in favor of the live results (or the "no matches"
	// message), same as the plain hint text it replaces.
	private void refreshRecentSearches() {

		ArrayList<String> recent = RecentSearches.getRecent(this);

		container_recent_searches_list.removeAllViews();

		if (recent.isEmpty()) {

			container_recent_searches.setVisibility(View.GONE);
			return;
		}

		container_recent_searches.setVisibility(View.VISIBLE);

		for (final String query : recent) {

			Button row = new Button(this, null, 0, R.style.ToolRow);
			row.setText(query);
			row.setAllCaps(false);

			row.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						et_global_search.setText(query);
						et_global_search.setSelection(query.length());
					}
				}
			);

			container_recent_searches_list.addView(row);
		}
	}

	private void runSearch(final String query) {

		final long myGeneration = ++loadGeneration;

		if (query.trim().length() == 0) {

			container_results.setVisibility(View.GONE);

			refreshRecentSearches();

			tv_global_search_empty.setText("Start typing to search.");
			tv_global_search_empty.setVisibility(
				container_recent_searches.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE
			);

			return;
		}

		container_recent_searches.setVisibility(View.GONE);

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> parties = searchParties(query);
					final ArrayList<HashMap<String, Object>> items = searchItems(query);

					final ArrayList<HashMap<String, Object>> invoices =
						db.searchTransactionsByInvoice(query.trim());

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyResults(query, parties, items, invoices);
							}
						});
				}
			}).start();
	}

	private ArrayList<HashMap<String, Object>> searchParties(String query) {

		ArrayList<HashMap<String, Object>> all = db.getPartiesWithActivity(
			DatabaseHelper.PARTY_SORT_NAME, true, DatabaseHelper.PARTY_BALANCE_FILTER_ALL
		);

		ArrayList<HashMap<String, Object>> matches = new ArrayList<HashMap<String, Object>>();

		for (HashMap<String, Object> row : all) {

			String name = (String) row.get("name");

			if (!SearchUtils.matchesTokensAcrossFields(query, name)) {
				continue;
			}

			HashMap<String, Object> result = new HashMap<String, Object>();
			result.put("party_id", (Integer) row.get("id"));
			result.put("primary", name);
			result.put("secondary", "Balance " + AmountFormat.format((Double) row.get("balance")));

			matches.add(result);
		}

		return matches;
	}

	private ArrayList<HashMap<String, Object>> searchItems(String query) {

		ArrayList<HashMap<String, Object>> all = db.getItems();

		ArrayList<HashMap<String, Object>> matches = new ArrayList<HashMap<String, Object>>();

		for (HashMap<String, Object> row : all) {

			String name = (String) row.get("name");
			String code = (String) row.get("code");

			if (!SearchUtils.matchesTokensAcrossFields(query, name, code)) {
				continue;
			}

			HashMap<String, Object> result = new HashMap<String, Object>();
			result.put("item_id", (Integer) row.get("id"));
			result.put("primary", name);
			result.put("secondary", "Code " + code + " · Stock " + AmountFormat.formatPlain((Double) row.get("balance")));

			matches.add(result);
		}

		return matches;
	}

	private void applyResults(
		String query,
		ArrayList<HashMap<String, Object>> parties,
		ArrayList<HashMap<String, Object>> items,
		ArrayList<HashMap<String, Object>> rawInvoices) {

		partyResults.clear();
		partyResults.addAll(parties);
		partyAdapter.notifyDataSetChanged();
		tv_parties_label.setVisibility(parties.isEmpty() ? View.GONE : View.VISIBLE);
		lv_parties.setVisibility(parties.isEmpty() ? View.GONE : View.VISIBLE);
		if (!parties.isEmpty()) {
			setListViewHeightBasedOnChildren(lv_parties);
		}

		itemResults.clear();
		itemResults.addAll(items);
		itemAdapter.notifyDataSetChanged();
		tv_items_label.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
		lv_items.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
		if (!items.isEmpty()) {
			setListViewHeightBasedOnChildren(lv_items);
		}

		invoiceResults.clear();

		for (HashMap<String, Object> row : rawInvoices) {

			HashMap<String, Object> result = new HashMap<String, Object>();
			result.put("transaction_type", row.get("transaction_type"));
			result.put("transaction_id", (Integer) row.get("transaction_id"));

			boolean isSale = "sale".equals(row.get("transaction_type"));

			result.put(
				"primary",
				"Invoice #" + row.get("invoice") + " (" + (isSale ? "Sale" : "Purchase") + ")"
			);

			result.put(
				"secondary",
				row.get("party_name") + " · " + row.get("date") + " · " +
				AmountFormat.format((Double) row.get("grand_total"))
			);

			invoiceResults.add(result);
		}

		invoiceAdapter.notifyDataSetChanged();
		tv_invoices_label.setVisibility(invoiceResults.isEmpty() ? View.GONE : View.VISIBLE);
		lv_invoices.setVisibility(invoiceResults.isEmpty() ? View.GONE : View.VISIBLE);
		if (!invoiceResults.isEmpty()) {
			setListViewHeightBasedOnChildren(lv_invoices);
		}

		boolean anyResults = !parties.isEmpty() || !items.isEmpty() || !invoiceResults.isEmpty();

		container_results.setVisibility(anyResults ? View.VISIBLE : View.GONE);

		tv_global_search_empty.setText("No matches for \"" + query.trim() + "\".");
		tv_global_search_empty.setVisibility(anyResults ? View.GONE : View.VISIBLE);
	}

	// A ListView inside a ScrollView doesn't scroll on its own, so it
	// needs to be sized to wrap all of its rows instead of clipping -
	// same fix used in NetProfitReportActivity and others.
	private void setListViewHeightBasedOnChildren(ListView listView) {

		android.widget.ListAdapter listAdapter = listView.getAdapter();

		if (listAdapter == null || listAdapter.getCount() == 0) {
			return;
		}

		int listViewWidth = listView.getWidth();

		if (listViewWidth <= 0) {

			android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
			int paddingPx = (int) (32 * metrics.density);
			listViewWidth = metrics.widthPixels - paddingPx;
		}

		int widthSpec = View.MeasureSpec.makeMeasureSpec(listViewWidth, View.MeasureSpec.EXACTLY);
		int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

		int totalHeight = 0;

		for (int i = 0; i < listAdapter.getCount(); i++) {

			View listItem = listAdapter.getView(i, null, listView);
			listItem.measure(widthSpec, heightSpec);
			totalHeight += listItem.getMeasuredHeight();
		}

		android.view.ViewGroup.LayoutParams params = listView.getLayoutParams();

		params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));

		listView.setLayoutParams(params);
		listView.requestLayout();
	}
}
