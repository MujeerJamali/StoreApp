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
// Every active item still carrying stock whose most recent Sale (if it
// has ever had one) falls before the selected window, oldest/never-sold
// first (see DatabaseHelper.getSlowMovingStock()). A never-sold item
// always qualifies regardless of how young it is. Tapping a row opens
// that item directly.
// =====================
public class SlowMovingStockReportActivity extends Activity {

	private static final int[] WINDOW_DAYS = {30, 60, 90, 180};

	private static final String[] WINDOW_LABELS = {
		"30 Days", "60 Days", "90 Days", "180 Days"
	};

	private Spinner spinner_window;
	private TextView tv_empty;
	private ListView lv_slow_moving_stock;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> slowMovingList =
		new ArrayList<HashMap<String, Object>>();

	private SlowMovingStockAdapter adapter;

	private int selectedWindowDays = WINDOW_DAYS[0];

	// A background result is only applied if it's still the most recent
	// request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.slow_moving_stock_report_activity);

		setTitle("Slow-Moving Stock");

		spinner_window = findViewById(R.id.spinner_window);
		tv_empty = findViewById(R.id.tv_empty);
		lv_slow_moving_stock = findViewById(R.id.lv_slow_moving_stock);

		db = new DatabaseHelper(this);

		adapter = new SlowMovingStockAdapter(this, slowMovingList);
		lv_slow_moving_stock.setAdapter(adapter);

		lv_slow_moving_stock.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = slowMovingList.get(position);

					Intent intent = new Intent(
						SlowMovingStockReportActivity.this, Itemviewactivity.class
					);

					intent.putExtra("item_id", (Integer) row.get("item_id"));

					startActivity(intent);
				}
			});

		ArrayAdapter<String> windowAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, WINDOW_LABELS
		);

		windowAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_window.setAdapter(windowAdapter);

		spinner_window.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedWindowDays = WINDOW_DAYS[position];
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

		final long myGeneration = ++loadGeneration;
		final int windowDays = selectedWindowDays;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getSlowMovingStock(windowDays);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								slowMovingList.clear();
								slowMovingList.addAll(result);

								adapter.notifyDataSetChanged();

								if (slowMovingList.isEmpty()) {

									tv_empty.setVisibility(View.VISIBLE);
									lv_slow_moving_stock.setVisibility(View.GONE);

								} else {

									tv_empty.setVisibility(View.GONE);
									lv_slow_moving_stock.setVisibility(View.VISIBLE);
								}
							}
						});
				}
			}).start();
	}
}
