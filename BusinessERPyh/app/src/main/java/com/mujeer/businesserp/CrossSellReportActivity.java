package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Pick an item, see what it's frequently bought together with (other
// items that showed up in the same Sale, ranked by how often) and,
// the reverse direction, what it's rarely bought together with (every
// other item that has sold at all, ranked by the same co-occurrence
// count ascending - a possible missed cross-sell opportunity). See
// DatabaseHelper.getCrossSellInsight().
// =====================
public class CrossSellReportActivity extends Activity {

	private static final int[] SHOES_FILTER_VALUES = {
		DatabaseHelper.SHOES_FILTER_ALL,
		DatabaseHelper.SHOES_FILTER_SHOES_ONLY,
		DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY
	};

	private static final String[] SHOES_FILTER_LABELS = {
		"All Items", "Shoes Only", "Non-Shoes Only"
	};

	private EditText et_search_item;
	private ListView lv_search_results;
	private TextView tv_back_to_all_items;
	private TextView tv_selected_item;

	private Spinner spinner_shoes_filter;
	private int selectedShoesFilter = DatabaseHelper.SHOES_FILTER_ALL;

	private LinearLayout container_all_items_summary;

	private LinearLayout container_cross_sell_results;
	private TextView tv_frequently_empty;
	private SimpleBarChartView chart_frequently_together;
	private LinearLayout container_frequently_together;
	private TextView tv_rarely_empty;
	private LinearLayout container_rarely_together;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> allSoldItems =
		new ArrayList<HashMap<String, Object>>();

	private ArrayAdapter<String> searchAdapter;
	private final ArrayList<HashMap<String, Object>> searchMatches =
		new ArrayList<HashMap<String, Object>>();

	private long loadGeneration = 0;
	private long summaryLoadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cross_sell_report_activity);

		setTitle("Cross-Sell Insight");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Cross-Sell Insight",
			"Pick an item to see what it's frequently bought together with (other items that showed up in the same Sale, ranked by how often) and, the reverse, what it's rarely bought together with - every other sold item, ranked the same way ascending - a possible missed cross-sell opportunity."
		);

		et_search_item = findViewById(R.id.et_search_item);
		lv_search_results = findViewById(R.id.lv_search_results);
		tv_back_to_all_items = findViewById(R.id.tv_back_to_all_items);
		tv_selected_item = findViewById(R.id.tv_selected_item);

		spinner_shoes_filter = findViewById(R.id.spinner_shoes_filter);
		container_all_items_summary = findViewById(R.id.container_all_items_summary);

		container_cross_sell_results = findViewById(R.id.container_cross_sell_results);
		tv_frequently_empty = findViewById(R.id.tv_frequently_empty);
		chart_frequently_together = findViewById(R.id.chart_frequently_together);
		container_frequently_together = findViewById(R.id.container_frequently_together);
		tv_rarely_empty = findViewById(R.id.tv_rarely_empty);
		container_rarely_together = findViewById(R.id.container_rarely_together);

		db = new DatabaseHelper(this);

		searchAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_list_item_1, new ArrayList<String>()
		);

		lv_search_results.setAdapter(searchAdapter);

		loadSoldItems();

		et_search_item.addTextChangedListener(new TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(Editable s) {
					filterResults(s.toString());
				}
			}
		);

		lv_search_results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					selectItem(searchMatches.get(position));
				}
			}
		);

		tv_back_to_all_items.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showAllItemsSummary();
				}
			}
		);

		ArrayAdapter<String> shoesFilterAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, SHOES_FILTER_LABELS
		);

		shoesFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_shoes_filter.setAdapter(shoesFilterAdapter);

		int rememberedShoesFilterPosition =
			FilterMemory.getInt(this, "CrossSellReport", "shoes_filter", 0);

		spinner_shoes_filter.setSelection(rememberedShoesFilterPosition);
		selectedShoesFilter = SHOES_FILTER_VALUES[rememberedShoesFilterPosition];

		spinner_shoes_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedShoesFilter = SHOES_FILTER_VALUES[position];
					FilterMemory.setInt(
						CrossSellReportActivity.this, "CrossSellReport", "shoes_filter", position
					);
					loadAllItemsSummary();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		loadAllItemsSummary();
	}

	// Landing view (approved feature list row #159's own follow-up
	// request) - every sold item with its top 3 cross-sell partners at
	// a glance, instead of making the user search one item at a time to
	// find out. Tapping a row drills into the same full frequently/
	// rarely-together breakdown the search box's own picks open (see
	// selectItem()).
	private void loadAllItemsSummary() {

		final long myGeneration = ++summaryLoadGeneration;
		final int shoesFilter_forQuery = selectedShoesFilter;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> summary =
						db.getTopCrossSellSummary(3, shoesFilter_forQuery);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != summaryLoadGeneration || isFinishing()) {
									return;
								}

								applyAllItemsSummary(summary);
							}
						}
					);
				}
			}
		).start();
	}

	private void applyAllItemsSummary(ArrayList<HashMap<String, Object>> summary) {

		container_all_items_summary.removeAllViews();

		for (final HashMap<String, Object> row : summary) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_all_items_summary, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("name")));

			@SuppressWarnings("unchecked")
			ArrayList<HashMap<String, Object>> topPartners =
				(ArrayList<HashMap<String, Object>>) row.get("top_partners");

			if (topPartners.isEmpty()) {

				tv_detail.setText("Nothing else sold alongside this item yet");
				tv_badge.setText("");

			} else {

				StringBuilder detail = new StringBuilder("Also bought: ");

				for (int i = 0; i < topPartners.size(); i++) {

					if (i > 0) {
						detail.append(", ");
					}

					HashMap<String, Object> partner = topPartners.get(i);

					detail.append(partner.get("name"))
						.append(" (").append(partner.get("co_occurrence_count")).append(")");
				}

				tv_detail.setText(detail.toString());
				tv_badge.setText(String.valueOf(topPartners.get(0).get("co_occurrence_count")));
			}

			tv_badge.setTextColor(getResources().getColor(R.color.text_secondary));

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						selectItem(row);
					}
				}
			);

			container_all_items_summary.addView(view);
		}
	}

	private void showAllItemsSummary() {

		et_search_item.setText("");

		container_cross_sell_results.setVisibility(View.GONE);
		tv_back_to_all_items.setVisibility(View.GONE);
		tv_selected_item.setVisibility(View.GONE);

		container_all_items_summary.setVisibility(View.VISIBLE);
		spinner_shoes_filter.setVisibility(View.VISIBLE);
	}

	private void loadSoldItems() {

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> items = db.getItems(
						DatabaseHelper.ITEM_ACTIVE_FILTER_ACTIVE_ONLY
					);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								allSoldItems.clear();
								allSoldItems.addAll(items);

								// A query typed while this background load was
								// still running would otherwise match against
								// an empty list and never be re-run once the
								// data actually arrives - re-filter now against
								// whatever's currently typed (a no-op if the
								// box is still empty).
								filterResults(et_search_item.getText().toString());
							}
						}
					);
				}
			}
		).start();
	}

	private void filterResults(String query) {

		searchMatches.clear();

		if (query.trim().length() >= 1) {

			for (HashMap<String, Object> item : allSoldItems) {

				String code = String.valueOf(item.get("code"));
				String name = String.valueOf(item.get("name"));

				if (SearchUtils.matchesTokensAcrossFields(query, code, name)) {
					searchMatches.add(item);
				}
			}
		}

		ArrayList<String> labels = new ArrayList<String>();

		for (HashMap<String, Object> item : searchMatches) {
			labels.add(item.get("code") + " - " + item.get("name"));
		}

		searchAdapter.clear();
		searchAdapter.addAll(labels);
		searchAdapter.notifyDataSetChanged();

		// selectItem() hides this list after a pick - show it again on
		// the next search, otherwise every search after the first one
		// quietly populates an invisible list and looks like searching
		// stopped returning results at all.
		lv_search_results.setVisibility(query.trim().length() >= 1 ? View.VISIBLE : View.GONE);
	}

	private void selectItem(HashMap<String, Object> item) {

		// Rows from the search box (db.getItems()) key the id as "id";
		// rows from the all-items summary (db.getTopCrossSellSummary())
		// key it as "item_id" - accept either so this one method can
		// drill into a selection from both sources.
		final int itemId = (Integer) (
			item.get("id") != null ? item.get("id") : item.get("item_id")
		);

		et_search_item.setText("");
		lv_search_results.setVisibility(View.GONE);

		container_all_items_summary.setVisibility(View.GONE);
		spinner_shoes_filter.setVisibility(View.GONE);
		tv_back_to_all_items.setVisibility(View.VISIBLE);

		tv_selected_item.setText("Selected: " + item.get("name"));
		tv_selected_item.setVisibility(View.VISIBLE);

		container_cross_sell_results.setVisibility(View.VISIBLE);

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> result = db.getCrossSellInsight(itemId);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyResult(result);
							}
						}
					);
				}
			}
		).start();
	}

	@SuppressWarnings("unchecked")
	private void applyResult(HashMap<String, Object> result) {

		ArrayList<HashMap<String, Object>> frequentlyTogether =
			(ArrayList<HashMap<String, Object>>) result.get("frequently_together");

		ArrayList<HashMap<String, Object>> rarelyTogether =
			(ArrayList<HashMap<String, Object>>) result.get("rarely_together");

		fillRows(container_frequently_together, frequentlyTogether, true);
		tv_frequently_empty.setVisibility(frequentlyTogether.isEmpty() ? View.VISIBLE : View.GONE);
		container_frequently_together.setVisibility(frequentlyTogether.isEmpty() ? View.GONE : View.VISIBLE);

		// Top 8 cross-sell partners by how often they've co-occurred with
		// the selected item - frequentlyTogether is already sorted
		// descending by co_occurrence_count (see getCrossSellInsight()),
		// so this just takes the first few rather than re-sorting.
		// "Rarely Bought Together" below is deliberately NOT charted - it's
		// every other sold item in the shop (often dozens), not a bounded
		// top-N ranking, so a bar chart of it would be unreadable and
		// mostly zero-height bars.
		chart_frequently_together.setVisibility(frequentlyTogether.isEmpty() ? View.GONE : View.VISIBLE);

		if (!frequentlyTogether.isEmpty()) {

			ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();

			int chartCount = Math.min(8, frequentlyTogether.size());

			for (int i = 0; i < chartCount; i++) {

				HashMap<String, Object> row = frequentlyTogether.get(i);

				chartEntries.add(new SimpleBarChartView.Entry(
					String.valueOf(row.get("name")),
					(Integer) row.get("co_occurrence_count"),
					getResources().getColor(R.color.primary)
				));
			}

			chart_frequently_together.setEntries(chartEntries);
		}

		fillRows(container_rarely_together, rarelyTogether, false);
		tv_rarely_empty.setVisibility(rarelyTogether.isEmpty() ? View.VISIBLE : View.GONE);
		container_rarely_together.setVisibility(rarelyTogether.isEmpty() ? View.GONE : View.VISIBLE);
	}

	private void fillRows(
		LinearLayout container, ArrayList<HashMap<String, Object>> rows, boolean highlightPositive) {

		container.removeAllViews();

		for (int i = 0; i < rows.size(); i++) {

			HashMap<String, Object> row = rows.get(i);

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("name")));
			tv_detail.setText("Code " + row.get("code"));

			int count = (Integer) row.get("co_occurrence_count");
			tv_badge.setText(String.valueOf(count));

			tv_badge.setTextColor(
				getResources().getColor(
					(highlightPositive && count > 0) ? R.color.success : R.color.text_secondary
				)
			);

			container.addView(view);

			if (i < rows.size() - 1) {

				View divider = new View(this);

				divider.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, 1
				));

				divider.setBackgroundColor(getResources().getColor(R.color.stroke));

				container.addView(divider);
			}
		}
	}
}
