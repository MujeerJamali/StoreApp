package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

// =====================
// Dead Stock Aging: every active item with stock that hasn't sold in 60+
// days (see DatabaseHelper.getDeadStockAging()), bucketed by how long,
// plus the Active Clearances section tracking items currently marked
// down (see startClearance()/endClearance()/getActiveClearances()).
// Starting a clearance from a dead-stock row moves it into the
// clearances section on the next load; ending one moves it back.
// =====================
public class DeadStockAgingReportActivity extends Activity {

	private static final int[] SHOES_FILTER_VALUES = {
		DatabaseHelper.SHOES_FILTER_ALL,
		DatabaseHelper.SHOES_FILTER_SHOES_ONLY,
		DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY
	};

	private static final String[] SHOES_FILTER_LABELS = {
		"All Items", "Shoes Only", "Non-Shoes Only"
	};

	private Spinner spinner_shoes_filter;

	private TextView tv_clearances_empty;
	private LinearLayout container_clearances;

	private TextView tv_dead_stock_empty;
	private LinearLayout container_dead_stock;
	private SimplePieChartView chart_dead_stock_aging_pie;
	private SimpleBarChartView chart_dead_stock_aging;

	private DatabaseHelper db;

	private int selectedShoesFilter = DatabaseHelper.SHOES_FILTER_ALL;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dead_stock_aging_report_activity);

		setTitle("Dead Stock Aging");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Dead Stock Aging",
			"Stock sitting unsold for 60+ effective days, bucketed into 60-89/90-119/120+ days (or Never Sold) - a non-shoe item gets 3x as long before counting as dead stock (configurable in Reorder Settings), since general merchandise naturally turns over slower than shoes. Start a clearance on any item to mark it down and track how much of it sells after - the percent shown is how much of the stock on hand when clearance began has sold since."
		);

		spinner_shoes_filter = findViewById(R.id.spinner_shoes_filter);

		tv_clearances_empty = findViewById(R.id.tv_clearances_empty);
		container_clearances = findViewById(R.id.container_clearances);

		tv_dead_stock_empty = findViewById(R.id.tv_dead_stock_empty);
		container_dead_stock = findViewById(R.id.container_dead_stock);
		chart_dead_stock_aging_pie = findViewById(R.id.chart_dead_stock_aging_pie);
		chart_dead_stock_aging = findViewById(R.id.chart_dead_stock_aging);

		db = new DatabaseHelper(this);

		ArrayAdapter<String> shoesFilterAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, SHOES_FILTER_LABELS
		);

		shoesFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_shoes_filter.setAdapter(shoesFilterAdapter);

		int rememberedShoesFilterPosition =
			FilterMemory.getInt(this, "DeadStockAgingReport", "shoes_filter", 0);

		spinner_shoes_filter.setSelection(rememberedShoesFilterPosition);
		selectedShoesFilter = SHOES_FILTER_VALUES[rememberedShoesFilterPosition];

		spinner_shoes_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedShoesFilter = SHOES_FILTER_VALUES[position];
					FilterMemory.setInt(
						DeadStockAgingReportActivity.this, "DeadStockAgingReport", "shoes_filter", position
					);
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
		final int shoesFilter_forQuery = selectedShoesFilter;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> deadStock =
						db.getDeadStockAging(DeadStockAgingReportActivity.this, shoesFilter_forQuery);
					final ArrayList<HashMap<String, Object>> clearances = db.getActiveClearances();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyClearances(clearances);
								applyDeadStock(deadStock);
							}
						}
					);
				}
			}
		).start();
	}

	private void applyClearances(ArrayList<HashMap<String, Object>> list) {

		container_clearances.removeAllViews();

		if (list.isEmpty()) {

			tv_clearances_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_clearances_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> row : list) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.clearance_row, container_clearances, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);
			Button btn_end_clearance = view.findViewById(R.id.btn_end_clearance);

			final int itemId = (Integer) row.get("item_id");

			tv_name.setText(String.valueOf(row.get("name")));

			double discountPercent = (Double) row.get("discount_percent");
			double currentBalance = (Double) row.get("current_balance");

			tv_detail.setText(
				"Started " + row.get("started_date") + " at " +
				AmountFormat.formatPlain(discountPercent) + "% off - " +
				AmountFormat.formatPlain(currentBalance) + " left in stock"
			);

			double percentCleared = (Double) row.get("percent_cleared");
			tv_badge.setText(AmountFormat.formatPlain(percentCleared) + "% cleared");

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							DeadStockAgingReportActivity.this, Itemviewactivity.class
						);

						intent.putExtra("item_id", itemId);
						startActivity(intent);
					}
				}
			);

			btn_end_clearance.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						db.endClearance(itemId);
						Toast.makeText(
							DeadStockAgingReportActivity.this, "Clearance ended", Toast.LENGTH_SHORT
						).show();
						loadReport();
					}
				}
			);

			container_clearances.addView(view);
		}
	}

	private void applyDeadStock(ArrayList<HashMap<String, Object>> list) {

		container_dead_stock.removeAllViews();

		// Stock quantity tied up per age bucket, for the chart below - same
		// four buckets getDeadStockAging() assigns, summed only over the
		// rows this method actually renders (an item already in clearance
		// is excluded, same as the list below).
		LinkedHashMap<String, Double> bucketTotals = new LinkedHashMap<String, Double>();
		bucketTotals.put("60-89 Days", 0.0);
		bucketTotals.put("90-119 Days", 0.0);
		bucketTotals.put("120+ Days", 0.0);
		bucketTotals.put("Never Sold", 0.0);

		if (list.isEmpty()) {

			tv_dead_stock_empty.setVisibility(View.VISIBLE);
			applyDeadStockChart(bucketTotals);
			return;
		}

		tv_dead_stock_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> row : list) {

			// An item already in clearance is shown above, not duplicated here.
			if (row.get("clearance") != null) {
				continue;
			}

			String ageBucket_forChart = String.valueOf(row.get("age_bucket"));
			double balance_forChart =
				row.get("balance") == null ? 0 : (Double) row.get("balance");

			Double existingBucketTotal = bucketTotals.get(ageBucket_forChart);

			bucketTotals.put(
				ageBucket_forChart,
				(existingBucketTotal == null ? 0.0 : existingBucketTotal) + balance_forChart
			);

			View view = LayoutInflater.from(this).inflate(
				R.layout.dead_stock_row, container_dead_stock, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);
			Button btn_start_clearance = view.findViewById(R.id.btn_start_clearance);

			final int itemId = (Integer) row.get("item_id");
			final String itemName = String.valueOf(row.get("name"));

			tv_name.setText(itemName);

			int daysSince = (Integer) row.get("days_since_sale");
			double balance = (Double) row.get("balance");

			tv_detail.setText(
				"Stock " + AmountFormat.formatPlain(balance) + " - " +
				(daysSince < 0 ? "never sold" : "last sold " + daysSince + " days ago")
			);

			String ageBucket = String.valueOf(row.get("age_bucket"));
			tv_badge.setText(ageBucket);

			final int defaultDiscountPercent;

			if ("60-89 Days".equals(ageBucket)) {
				defaultDiscountPercent = 20;
			} else if ("90-119 Days".equals(ageBucket)) {
				defaultDiscountPercent = 25;
			} else {
				defaultDiscountPercent = 30;
			}

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							DeadStockAgingReportActivity.this, Itemviewactivity.class
						);

						intent.putExtra("item_id", itemId);
						startActivity(intent);
					}
				}
			);

			btn_start_clearance.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						promptStartClearance(itemId, itemName, defaultDiscountPercent);
					}
				}
			);

			container_dead_stock.addView(view);
		}

		applyDeadStockChart(bucketTotals);
	}

	// Share of dead-stock quantity by age bucket - a Pie whenever at least
	// two buckets actually carry stock (a true share-of-a-whole), falling
	// back to a single-bucket Bar otherwise (a Pie can't plot fewer than 2
	// slices), and showing nothing at all when every bucket is empty. Same
	// dual-chart fallback pattern as ProfitSplitReportActivity.
	private void applyDeadStockChart(LinkedHashMap<String, Double> bucketTotals) {

		int[] bucketColors = {
			getResources().getColor(R.color.accent),
			getResources().getColor(R.color.primary),
			getResources().getColor(R.color.danger),
			getResources().getColor(R.color.text_secondary)
		};

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();

		int positiveCount = 0;
		int colorIndex = 0;

		for (String bucketLabel : bucketTotals.keySet()) {

			double value = bucketTotals.get(bucketLabel);

			if (value > 0) {
				positiveCount++;
			}

			chartEntries.add(new SimpleBarChartView.Entry(
				bucketLabel, value, bucketColors[colorIndex % bucketColors.length]
			));

			colorIndex++;
		}

		boolean usePie = positiveCount >= 2;
		boolean useBar = !usePie && positiveCount >= 1;

		chart_dead_stock_aging_pie.setVisibility(usePie ? View.VISIBLE : View.GONE);
		chart_dead_stock_aging.setVisibility(useBar ? View.VISIBLE : View.GONE);

		if (usePie) {
			chart_dead_stock_aging_pie.setEntries(chartEntries);
		} else if (useBar) {
			chart_dead_stock_aging.setEntries(chartEntries);
		}
	}

	private void promptStartClearance(
		final int itemId, String itemName, int defaultDiscountPercent
	) {

		final EditText input = new EditText(this);
		input.setHint("Discount %");
		input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		input.setText(String.valueOf(defaultDiscountPercent));

		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		input.setPadding(pad, pad, pad, pad);

		new AlertDialog.Builder(this)
			.setTitle("Start Clearance - " + itemName)
			.setMessage("Mark this item down and track how much sells before it's done.")
			.setView(input)
			.setPositiveButton("Start", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						double discountPercent;

						try {
							discountPercent = Double.parseDouble(input.getText().toString().trim());
						} catch (Exception e) {
							Toast.makeText(
								DeadStockAgingReportActivity.this,
								"Enter a valid discount percent",
								Toast.LENGTH_SHORT
							).show();
							return;
						}

						if (discountPercent < 0 || discountPercent > 100) {
							Toast.makeText(
								DeadStockAgingReportActivity.this,
								"Discount percent must be between 0 and 100",
								Toast.LENGTH_SHORT
							).show();
							return;
						}

						db.startClearance(itemId, discountPercent);
						Toast.makeText(
							DeadStockAgingReportActivity.this, "Clearance started", Toast.LENGTH_SHORT
						).show();
						loadReport();
					}
				}
			)
			.setNegativeButton("Cancel", null)
			.show();
	}
}
