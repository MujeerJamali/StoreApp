package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Two independent, read-only lists: what to discount this week (see
// DatabaseHelper.getDiscountCandidates() - slow-moving stock with a
// suggested discount tier) and what to stop restocking (see
// getStopRestockingCandidates() - active items currently selling at or
// below cost). Rows are built programmatically into a plain LinearLayout
// rather than a ListView, since two independently-scrolling lists inside
// one screen would otherwise fight over touch/scroll events - this
// report's lists are always small enough that this costs nothing.
// =====================
public class DiscountStopRestockReportActivity extends Activity {

	private static final int[] SHOES_FILTER_VALUES = {
		DatabaseHelper.SHOES_FILTER_ALL,
		DatabaseHelper.SHOES_FILTER_SHOES_ONLY,
		DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY
	};

	private static final String[] SHOES_FILTER_LABELS = {
		"All Items", "Shoes Only", "Non-Shoes Only"
	};

	private Spinner spinner_shoes_filter;

	private TextView tv_discount_empty;
	private LinearLayout container_discount;

	private TextView tv_stop_restocking_empty;
	private LinearLayout container_stop_restocking;

	private DatabaseHelper db;

	private int selectedShoesFilter = DatabaseHelper.SHOES_FILTER_ALL;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.discount_stop_restock_report_activity);

		setTitle("Discount & Stop-Restocking");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Discount & Stop-Restocking",
			"Discount This Week: slow-moving stock (no sale in 30+ effective days), oldest first, each with a suggested discount tier (10/20/30%) based on how long it's been sitting - a non-shoe item gets 3x as long before counting as slow (configurable in Reorder Settings), since general merchandise naturally turns over slower than shoes. Stop Restocking: active items currently selling at or below their own cost - restocking at today's prices would be a loss."
		);

		spinner_shoes_filter = findViewById(R.id.spinner_shoes_filter);

		tv_discount_empty = findViewById(R.id.tv_discount_empty);
		container_discount = findViewById(R.id.container_discount);

		tv_stop_restocking_empty = findViewById(R.id.tv_stop_restocking_empty);
		container_stop_restocking = findViewById(R.id.container_stop_restocking);

		db = new DatabaseHelper(this);

		ArrayAdapter<String> shoesFilterAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, SHOES_FILTER_LABELS
		);

		shoesFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_shoes_filter.setAdapter(shoesFilterAdapter);

		int rememberedShoesFilterPosition =
			FilterMemory.getInt(this, "DiscountStopRestockReport", "shoes_filter", 0);

		spinner_shoes_filter.setSelection(rememberedShoesFilterPosition);
		selectedShoesFilter = SHOES_FILTER_VALUES[rememberedShoesFilterPosition];

		spinner_shoes_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedShoesFilter = SHOES_FILTER_VALUES[position];
					FilterMemory.setInt(
						DiscountStopRestockReportActivity.this,
						"DiscountStopRestockReport", "shoes_filter", position
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

					final ArrayList<HashMap<String, Object>> discountCandidates =
						db.getDiscountCandidates(
							DiscountStopRestockReportActivity.this, shoesFilter_forQuery
						);

					final ArrayList<HashMap<String, Object>> stopRestockingCandidates =
						db.getStopRestockingCandidates(shoesFilter_forQuery);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyDiscountCandidates(discountCandidates);
								applyStopRestockingCandidates(stopRestockingCandidates);
							}
						}
					);
				}
			}
		).start();
	}

	private void applyDiscountCandidates(ArrayList<HashMap<String, Object>> list) {

		container_discount.removeAllViews();

		if (list.isEmpty()) {

			tv_discount_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_discount_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> row : list) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_discount, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("name")));

			int daysSince = (Integer) row.get("days_since_sale");
			double balance = (Double) row.get("balance");

			tv_detail.setText(
				"Stock " + AmountFormat.formatPlain(balance) + " - " +
				(daysSince < 0 ? "never sold" : "last sold " + daysSince + " days ago")
			);

			int discountPercent = (Integer) row.get("suggested_discount_percent");
			tv_badge.setText(discountPercent + "%");

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							DiscountStopRestockReportActivity.this, Itemviewactivity.class
						);

						intent.putExtra("item_id", (Integer) row.get("item_id"));
						startActivity(intent);
					}
				}
			);

			container_discount.addView(view);
		}
	}

	private void applyStopRestockingCandidates(ArrayList<HashMap<String, Object>> list) {

		container_stop_restocking.removeAllViews();

		if (list.isEmpty()) {

			tv_stop_restocking_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_stop_restocking_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> row : list) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_stop_restocking, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("name")));

			double costBasis = (Double) row.get("cost_basis");
			double salePrice = (Double) row.get("sale_price");

			tv_detail.setText(
				"Cost " + AmountFormat.format(costBasis) + " vs Sale " + AmountFormat.format(salePrice)
			);

			double margin = (Double) row.get("margin");
			tv_badge.setText(AmountFormat.format(margin));

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							DiscountStopRestockReportActivity.this, Itemviewactivity.class
						);

						intent.putExtra("item_id", (Integer) row.get("item_id"));
						startActivity(intent);
					}
				}
			);

			container_stop_restocking.addView(view);
		}
	}
}
