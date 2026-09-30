package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

// =====================
// One row per distinct variety value (almost always "Size" for this
// shop, since that's the one group nearly every item has) with its
// total stock across every item that carries it - an Excel-style
// multi-select filter (see showValueFilterDialog()) narrows which
// values are shown, and tapping a row opens ComboStockItemsActivity to
// see which specific items carry it. See
// DatabaseHelper.getComboStockSummary()/getDistinctVarietyValueLabels().
// =====================
public class ComboStockReportActivity extends Activity {

	private Button btn_open_value_filter;
	private TextView tv_filter_status;
	private TextView tv_empty;
	private ListView lv_combo_stock;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> fullList =
		new ArrayList<HashMap<String, Object>>();

	private final ArrayList<HashMap<String, Object>> filteredList =
		new ArrayList<HashMap<String, Object>>();

	private ComboStockAdapter adapter;

	// null means "no filter applied - show everything"; once the user
	// opens the filter dialog and unchecks anything, this holds the set
	// of value labels still allowed through.
	private HashSet<String> selectedLabels = null;

	private ArrayList<String> allLabels = new ArrayList<String>();

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.combo_stock_report_activity);

		setTitle("Combo/Variety Stock");

		btn_open_value_filter = findViewById(R.id.btn_open_value_filter);
		tv_filter_status = findViewById(R.id.tv_filter_status);
		tv_empty = findViewById(R.id.tv_empty);
		lv_combo_stock = findViewById(R.id.lv_combo_stock);

		db = new DatabaseHelper(this);

		adapter = new ComboStockAdapter(this, filteredList);
		lv_combo_stock.setAdapter(adapter);

		btn_open_value_filter.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showValueFilterDialog();
				}
			});

		lv_combo_stock.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = filteredList.get(position);

					Intent intent = new Intent(
						ComboStockReportActivity.this, ComboStockItemsActivity.class
					);

					intent.putExtra("value_id", (Integer) row.get("value_id"));
					intent.putExtra("value_label", (String) row.get("value_label"));

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

	private void showValueFilterDialog() {

		if (allLabels.isEmpty()) {
			return;
		}

		final boolean[] checked = new boolean[allLabels.size()];

		for (int i = 0; i < allLabels.size(); i++) {
			checked[i] = selectedLabels == null || selectedLabels.contains(allLabels.get(i));
		}

		final String[] labelsArray = allLabels.toArray(new String[0]);

		new AlertDialog.Builder(this)
			.setTitle("Filter Values")
			.setMultiChoiceItems(labelsArray, checked, new DialogInterface.OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean isChecked) {
						checked[which] = isChecked;
					}
				})
			.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						HashSet<String> newSelection = new HashSet<String>();

						for (int i = 0; i < labelsArray.length; i++) {
							if (checked[i]) {
								newSelection.add(labelsArray[i]);
							}
						}

						selectedLabels = newSelection.size() == allLabels.size() ? null : newSelection;

						applyFilter();
					}
				})
			.setNeutralButton("Select All", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						selectedLabels = null;
						applyFilter();
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void applyFilter() {

		filteredList.clear();

		for (HashMap<String, Object> row : fullList) {

			String label = (String) row.get("value_label");

			if (selectedLabels == null || selectedLabels.contains(label)) {
				filteredList.add(row);
			}
		}

		adapter.notifyDataSetChanged();

		tv_filter_status.setText(
			selectedLabels == null ?
			"Showing all values" :
			"Showing " + selectedLabels.size() + " of " + allLabels.size() + " values"
		);

		if (filteredList.isEmpty()) {

			tv_empty.setVisibility(View.VISIBLE);
			lv_combo_stock.setVisibility(View.GONE);

		} else {

			tv_empty.setVisibility(View.GONE);
			lv_combo_stock.setVisibility(View.VISIBLE);
		}
	}

	private void loadReport() {

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result = db.getComboStockSummary();
					final ArrayList<String> labels = db.getDistinctVarietyValueLabels();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								fullList.clear();
								fullList.addAll(result);

								allLabels = labels;

								// Drop any previously-selected label that no longer
								// exists in the catalog, so a stale filter can't
								// silently hide everything after data changes.
								if (selectedLabels != null) {
									selectedLabels.retainAll(allLabels);
								}

								applyFilter();
							}
						});
				}
			}).start();
	}
}
