package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// =====================
// Where stock value is tied up right now, sliced two ways from the
// exact same total (see DatabaseHelper.getStockValueByCategoryAndAge()):
// by category (the first word of each item's name - there's no formal
// category field, so this is the closest proxy, and it naturally groups
// every "Shoes ..." item the same way Stock Worth already does) and by
// age (days since the item's last sale, in the same buckets Slow-Moving
// Stock uses). Both lists are sorted highest-value-first.
// =====================
public class StockValueReportActivity extends Activity {

	private TextView tv_total_value;
	private LinearLayout container_by_category;
	private LinearLayout container_by_age;

	private DatabaseHelper db;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stock_value_report_activity);

		setTitle("Stock Value");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Stock Value",
			"Where your money is tied up in current stock (balance x purchase price for every active item with stock), sliced by category - the first word of each item's name, since there's no separate category field - and by days since last sale, same buckets as Slow-Moving Stock."
		);

		tv_total_value = findViewById(R.id.tv_total_value);
		container_by_category = findViewById(R.id.container_by_category);
		container_by_age = findViewById(R.id.container_by_age);

		db = new DatabaseHelper(this);

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

					final HashMap<String, Object> result = db.getStockValueByCategoryAndAge();

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

		double totalValue = (Double) result.get("total_value");
		tv_total_value.setText(AmountFormat.format(totalValue));

		LinkedHashMap<String, Double> byCategory =
			(LinkedHashMap<String, Double>) result.get("by_category");

		LinkedHashMap<String, Double> byAge = (LinkedHashMap<String, Double>) result.get("by_age");

		fillBuckets(container_by_category, sortedByValueDescending(byCategory));
		fillBuckets(container_by_age, sortedByValueDescending(byAge));
	}

	private ArrayList<Map.Entry<String, Double>> sortedByValueDescending(
		LinkedHashMap<String, Double> map) {

		ArrayList<Map.Entry<String, Double>> entries =
			new ArrayList<Map.Entry<String, Double>>(map.entrySet());

		java.util.Collections.sort(
			entries,
			new java.util.Comparator<Map.Entry<String, Double>>() {
				@Override
				public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
					return Double.compare(b.getValue(), a.getValue());
				}
			}
		);

		return entries;
	}

	private void fillBuckets(LinearLayout container, ArrayList<Map.Entry<String, Double>> entries) {

		container.removeAllViews();

		for (int i = 0; i < entries.size(); i++) {

			Map.Entry<String, Double> entry = entries.get(i);

			View row = LayoutInflater.from(this).inflate(
				R.layout.stock_value_bucket_row, container, false
			);

			TextView tv_label = row.findViewById(R.id.tv_bucket_label);
			TextView tv_value = row.findViewById(R.id.tv_bucket_value);

			tv_label.setText(entry.getKey());
			tv_value.setText(AmountFormat.format(entry.getValue()));

			container.addView(row);

			if (i < entries.size() - 1) {

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
