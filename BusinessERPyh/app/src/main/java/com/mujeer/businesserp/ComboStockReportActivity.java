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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;

// =====================
// Single-screen Combo/Variety Stock report for shoes: a minimalist
// Gender+Size filter dropdown (built by parsing each item's name via
// ShoeIdentity, since Gender isn't part of the variety schema - only
// Size/etc. are) narrows the item list below it. Each item row shows
// its name, then every size it comes in with stock, the size matching
// the selected filter shown first and in bold. Tapping an item opens
// its item screen directly - there is no second screen (this replaces
// the old two-screen value-summary/items-for-value design). See
// DatabaseHelper.getShoeComboItems().
// =====================
public class ComboStockReportActivity extends Activity {

	// One selectable entry in the filter Spinner - "All Sizes" (gender
	// and label both null) plus one per distinct (gender, size label)
	// pair actually present in the catalog.
	private static class FilterOption {

		final String gender;
		final String label;
		final String display;

		FilterOption(String gender, String label, String display) {
			this.gender = gender;
			this.label = label;
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}

	private Spinner spinner_combo_filter;
	private TextView tv_empty;
	private ListView lv_combo_stock;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> fullList =
		new ArrayList<HashMap<String, Object>>();

	private final ArrayList<HashMap<String, Object>> filteredList =
		new ArrayList<HashMap<String, Object>>();

	private final ArrayList<FilterOption> filterOptions = new ArrayList<FilterOption>();

	private ComboStockAdapter adapter;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.combo_stock_report_activity);

		setTitle("Combo/Variety Stock");

		spinner_combo_filter = findViewById(R.id.spinner_combo_filter);
		tv_empty = findViewById(R.id.tv_empty);
		lv_combo_stock = findViewById(R.id.lv_combo_stock);

		db = new DatabaseHelper(this);

		adapter = new ComboStockAdapter(this, filteredList);
		lv_combo_stock.setAdapter(adapter);

		spinner_combo_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					applyFilter();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		lv_combo_stock.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = filteredList.get(position);

					Intent intent = new Intent(
						ComboStockReportActivity.this, Itemviewactivity.class
					);

					intent.putExtra("item_id", (Integer) row.get("item_id"));

					startActivity(intent);
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

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result = db.getShoeComboItems();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								fullList.clear();
								fullList.addAll(result);

								rebuildFilterOptions();
							}
						});
				}
			}).start();
	}

	@SuppressWarnings("unchecked")
	private void rebuildFilterOptions() {

		// Remember the previously selected option (by gender+label) so a
		// reload (e.g. onResume after a sale) doesn't silently reset the
		// user's filter back to "All Sizes".
		int previousPosition = spinner_combo_filter.getSelectedItemPosition();

		FilterOption previouslySelected =
			previousPosition >= 0 && previousPosition < filterOptions.size() ?
			filterOptions.get(previousPosition) : null;

		ArrayList<FilterOption> options = new ArrayList<FilterOption>();
		options.add(new FilterOption(null, null, "All Sizes"));

		ArrayList<FilterOption> genderSizeOptions = new ArrayList<FilterOption>();
		LinkedHashSet<String> seen = new LinkedHashSet<String>();

		for (HashMap<String, Object> item : fullList) {

			ShoeIdentity identity = ShoeIdentity.parse((String) item.get("name"));

			if (identity == null) {
				continue;
			}

			ArrayList<HashMap<String, Object>> combos =
				(ArrayList<HashMap<String, Object>>) item.get("combos");

			for (HashMap<String, Object> combo : combos) {

				String label = combo.get("label") == null ? "" : combo.get("label").toString();

				if (label.isEmpty()) {
					continue;
				}

				String key = identity.gender + "\u0001" + label;

				if (seen.add(key)) {
					genderSizeOptions.add(
						new FilterOption(identity.gender, label, identity.gender + " " + label)
					);
				}
			}
		}

		Collections.sort(genderSizeOptions, new Comparator<FilterOption>() {
				@Override
				public int compare(FilterOption a, FilterOption b) {

					int genderCompare = a.gender.compareTo(b.gender);

					if (genderCompare != 0) {
						return genderCompare;
					}

					return compareSizeLabels(a.label, b.label);
				}
			});

		options.addAll(genderSizeOptions);

		filterOptions.clear();
		filterOptions.addAll(options);

		ArrayAdapter<FilterOption> spinnerAdapter = new ArrayAdapter<FilterOption>(
			this, android.R.layout.simple_spinner_item, filterOptions
		);

		spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_combo_filter.setAdapter(spinnerAdapter);

		int restorePosition = 0;

		if (previouslySelected != null && previouslySelected.gender != null) {

			for (int i = 0; i < filterOptions.size(); i++) {

				FilterOption option = filterOptions.get(i);

				if (
					option.gender != null &&
					option.gender.equals(previouslySelected.gender) &&
					option.label.equals(previouslySelected.label)
				) {
					restorePosition = i;
					break;
				}
			}
		}

		spinner_combo_filter.setSelection(restorePosition);

		applyFilter();
	}

	// Numeric-aware size comparison so "10" doesn't sort before "7" -
	// falls back to plain string comparison for non-numeric labels (e.g.
	// an item with more than one variety group, where the label is
	// "9 / Red" rather than a bare size).
	private static int compareSizeLabels(String a, String b) {

		try {

			double numA = Double.parseDouble(a.trim());
			double numB = Double.parseDouble(b.trim());

			return Double.compare(numA, numB);

		} catch (NumberFormatException e) {

			return a.compareTo(b);
		}
	}

	@SuppressWarnings("unchecked")
	private void applyFilter() {

		int position = spinner_combo_filter.getSelectedItemPosition();

		FilterOption selected =
			position >= 0 && position < filterOptions.size() ?
			filterOptions.get(position) : null;

		filteredList.clear();

		for (HashMap<String, Object> item : fullList) {

			if (selected == null || selected.gender == null) {

				filteredList.add(item);
				continue;
			}

			ShoeIdentity identity = ShoeIdentity.parse((String) item.get("name"));

			if (identity == null || !identity.gender.equals(selected.gender)) {
				continue;
			}

			ArrayList<HashMap<String, Object>> combos =
				(ArrayList<HashMap<String, Object>>) item.get("combos");

			for (HashMap<String, Object> combo : combos) {

				String label = combo.get("label") == null ? "" : combo.get("label").toString();

				if (label.equals(selected.label)) {
					filteredList.add(item);
					break;
				}
			}
		}

		adapter.setSelectedFilter(selected == null ? null : selected.label);
		adapter.notifyDataSetChanged();

		if (filteredList.isEmpty()) {

			tv_empty.setVisibility(View.VISIBLE);
			lv_combo_stock.setVisibility(View.GONE);

		} else {

			tv_empty.setVisibility(View.GONE);
			lv_combo_stock.setVisibility(View.VISIBLE);
		}
	}
}
