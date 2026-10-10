package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// "What To Do Today" action summary (approved feature list row #91) -
// a single screen combining three existing reports that already answer
// "does something need attention right now" (see DatabaseHelper.
// getTodayActionSummary()): items to reorder, Sales with a balance due
// today or earlier, and slow-moving stock. Each section shows a capped
// preview (top 5) and a "View All" link to that report's own full
// screen for everything beyond the preview - this screen deliberately
// doesn't duplicate any of those reports' own filtering/sorting.
// =====================
public class TodayActionsActivity extends Activity {

	private static final int PREVIEW_LIMIT = 5;
	private static final int TYPE_SALE = 1;

	TextView tv_all_caught_up;

	View section_reorder;
	LinearLayout container_reorder;
	TextView tv_reorder_header;
	TextView tv_reorder_view_all;

	View section_dues;
	LinearLayout container_dues;
	TextView tv_dues_header;
	TextView tv_dues_view_all;

	View section_slow_stock;
	LinearLayout container_slow_stock;
	TextView tv_slow_stock_header;
	TextView tv_slow_stock_view_all;

	DatabaseHelper db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.today_actions_activity);

		setTitle("What To Do Today");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"What To Do Today",
			"Three existing reports in one place - items that need reordering, " +
			"Sales with a balance due today or earlier, and stock that hasn't " +
			"sold in a while. Each section shows up to 5 and links to its own " +
			"full report (Reorder List / Credit Due / Slow-Moving Stock) for " +
			"the rest - nothing here is computed differently than those reports."
		);

		tv_all_caught_up = findViewById(R.id.tv_all_caught_up);

		section_reorder = findViewById(R.id.section_reorder);
		container_reorder = findViewById(R.id.container_reorder);
		tv_reorder_header = findViewById(R.id.tv_reorder_header);
		tv_reorder_view_all = findViewById(R.id.tv_reorder_view_all);

		section_dues = findViewById(R.id.section_dues);
		container_dues = findViewById(R.id.container_dues);
		tv_dues_header = findViewById(R.id.tv_dues_header);
		tv_dues_view_all = findViewById(R.id.tv_dues_view_all);

		section_slow_stock = findViewById(R.id.section_slow_stock);
		container_slow_stock = findViewById(R.id.container_slow_stock);
		tv_slow_stock_header = findViewById(R.id.tv_slow_stock_header);
		tv_slow_stock_view_all = findViewById(R.id.tv_slow_stock_view_all);

		db = new DatabaseHelper(this);

		tv_reorder_view_all.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(TodayActionsActivity.this, ReorderListActivity.class));
				}
			});

		tv_dues_view_all.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(TodayActionsActivity.this, CreditDueReportActivity.class));
				}
			});

		tv_slow_stock_view_all.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(
						new Intent(TodayActionsActivity.this, SlowMovingStockReportActivity.class)
					);
				}
			});

		loadSummary();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadSummary();
	}

	@SuppressWarnings("unchecked")
	private void loadSummary() {

		HashMap<String, Object> summary = db.getTodayActionSummary(this);

		ArrayList<HashMap<String, Object>> reorderItems =
			(ArrayList<HashMap<String, Object>>) summary.get("reorder_items");

		ArrayList<HashMap<String, Object>> duesItems =
			(ArrayList<HashMap<String, Object>>) summary.get("dues_items");

		ArrayList<HashMap<String, Object>> slowStockItems =
			(ArrayList<HashMap<String, Object>>) summary.get("slow_stock_items");

		applyReorderSection(reorderItems);
		applyDuesSection(duesItems);
		applySlowStockSection(slowStockItems);

		boolean anyVisible =
			!reorderItems.isEmpty() || !duesItems.isEmpty() || !slowStockItems.isEmpty();

		tv_all_caught_up.setVisibility(anyVisible ? View.GONE : View.VISIBLE);
	}

	private void applyReorderSection(ArrayList<HashMap<String, Object>> items) {

		if (items.isEmpty()) {
			section_reorder.setVisibility(View.GONE);
			return;
		}

		section_reorder.setVisibility(View.VISIBLE);
		tv_reorder_header.setText("Reorder Needed (" + items.size() + ")");

		container_reorder.removeAllViews();

		int shown = Math.min(items.size(), PREVIEW_LIMIT);

		for (int i = 0; i < shown; i++) {

			final HashMap<String, Object> item = items.get(i);

			View row = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_reorder, false
			);

			TextView tv_name = row.findViewById(R.id.tv_row_name);
			TextView tv_detail = row.findViewById(R.id.tv_row_detail);
			TextView tv_badge = row.findViewById(R.id.tv_row_badge);

			String name = (String) item.get("name");
			String comboLabel = (String) item.get("combo_label");

			tv_name.setText(comboLabel != null ? (name + " - " + comboLabel) : name);

			double suggestedQty = (Double) item.get("suggested_qty");
			double estimatedCost = (Double) item.get("estimated_cost");

			tv_detail.setText(
				"Suggested: " + AmountFormat.formatPlain(suggestedQty) +
				" - Rs. " + AmountFormat.format(estimatedCost)
			);

			String runsOutDate = (String) item.get("runs_out_date");
			tv_badge.setText(runsOutDate != null ? "Out " + runsOutDate : "Low");
			tv_badge.setTextColor(getResources().getColor(R.color.danger));

			row.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(TodayActionsActivity.this, ReorderListActivity.class));
					}
				});

			container_reorder.addView(row);
		}
	}

	private void applyDuesSection(ArrayList<HashMap<String, Object>> items) {

		if (items.isEmpty()) {
			section_dues.setVisibility(View.GONE);
			return;
		}

		section_dues.setVisibility(View.VISIBLE);
		tv_dues_header.setText("Payments Due (" + items.size() + ")");

		container_dues.removeAllViews();

		int shown = Math.min(items.size(), PREVIEW_LIMIT);

		for (int i = 0; i < shown; i++) {

			final HashMap<String, Object> item = items.get(i);

			View row = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_dues, false
			);

			TextView tv_name = row.findViewById(R.id.tv_row_name);
			TextView tv_detail = row.findViewById(R.id.tv_row_detail);
			TextView tv_badge = row.findViewById(R.id.tv_row_badge);

			Object partyName = item.get("party_name");
			tv_name.setText(partyName == null ? "" : partyName.toString());

			tv_detail.setText(
				"Invoice #" + item.get("invoice_no") + " - Due " + item.get("due_date")
			);

			double balance = (Double) item.get("balance");
			tv_badge.setText(AmountFormat.format(balance));
			tv_badge.setTextColor(getResources().getColor(R.color.danger));

			row.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							TodayActionsActivity.this, Transactionviewactivity.class
						);

						intent.putExtra("transaction_type", TYPE_SALE);
						intent.putExtra("transaction_id", (Integer) item.get("sale_id"));

						startActivity(intent);
					}
				});

			container_dues.addView(row);
		}
	}

	private void applySlowStockSection(ArrayList<HashMap<String, Object>> items) {

		if (items.isEmpty()) {
			section_slow_stock.setVisibility(View.GONE);
			return;
		}

		section_slow_stock.setVisibility(View.VISIBLE);
		tv_slow_stock_header.setText("Slow-Moving Stock (" + items.size() + ")");

		container_slow_stock.removeAllViews();

		int shown = Math.min(items.size(), PREVIEW_LIMIT);

		for (int i = 0; i < shown; i++) {

			final HashMap<String, Object> item = items.get(i);

			View row = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_slow_stock, false
			);

			TextView tv_name = row.findViewById(R.id.tv_row_name);
			TextView tv_detail = row.findViewById(R.id.tv_row_detail);
			TextView tv_badge = row.findViewById(R.id.tv_row_badge);

			tv_name.setText((String) item.get("name"));

			double balance = (Double) item.get("balance");
			tv_detail.setText("In stock: " + AmountFormat.formatPlain(balance));

			int daysSince = (Integer) item.get("days_since_sale");
			tv_badge.setText(daysSince < 0 ? "Never sold" : (daysSince + "d"));
			tv_badge.setTextColor(getResources().getColor(R.color.warning));

			row.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							TodayActionsActivity.this, Itemviewactivity.class
						);

						intent.putExtra("item_id", (Integer) item.get("item_id"));

						startActivity(intent);
					}
				});

			container_slow_stock.addView(row);
		}
	}
}
