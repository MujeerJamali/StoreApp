package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Dead Stock Aging: every active item with stock that hasn't sold in 60+
// days (see DatabaseHelper.getDeadStockAging()), bucketed by how long,
// plus the Active Clearances section tracking items currently marked
// down (see startClearance()/endClearance()/getActiveClearances()).
// Starting a clearance from a dead-stock row moves it into the
// clearances section on the next load; ending one moves it back.
// =====================
public class DeadStockAgingReportActivity extends Activity {

	private TextView tv_clearances_empty;
	private LinearLayout container_clearances;

	private TextView tv_dead_stock_empty;
	private LinearLayout container_dead_stock;

	private DatabaseHelper db;

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

		tv_clearances_empty = findViewById(R.id.tv_clearances_empty);
		container_clearances = findViewById(R.id.container_clearances);

		tv_dead_stock_empty = findViewById(R.id.tv_dead_stock_empty);
		container_dead_stock = findViewById(R.id.container_dead_stock);

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

					final ArrayList<HashMap<String, Object>> deadStock =
						db.getDeadStockAging(DeadStockAgingReportActivity.this);
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

		if (list.isEmpty()) {

			tv_dead_stock_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_dead_stock_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> row : list) {

			// An item already in clearance is shown above, not duplicated here.
			if (row.get("clearance") != null) {
				continue;
			}

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
