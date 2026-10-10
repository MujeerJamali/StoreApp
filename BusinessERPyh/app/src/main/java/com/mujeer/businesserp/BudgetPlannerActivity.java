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

// =====================
// Budget planner for next month's purchases (approved feature list row
// #77) - "forecast" here is simply every current Reorder List
// suggestion's own estimated cost (DatabaseHelper.getReorderSuggestions()
// already folds in velocity, seasonal, trend, and learning adjustments -
// see that method), summed and grouped by supplier; "cash position" is
// the exact same effective cash limit ReorderListActivity's own convert-
// to-draft warning already uses (ReorderSettings.getEffectiveCashLimit()),
// so this screen never disagrees with that one about what's affordable.
// Purely a planning view - nothing here commits anything; use the
// Reorder List itself (or individual Purchases) to actually act.
// =====================
public class BudgetPlannerActivity extends Activity {

	private TextView tv_available_cash;
	private TextView tv_total_suggested;
	private TextView tv_remaining;
	private TextView tv_no_suggestions;
	private LinearLayout container_budget_by_supplier;

	private DatabaseHelper db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.budget_planner_activity);

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Budget Planner",
			"What next month's purchases would cost if every current Reorder List suggestion were bought, grouped by supplier, against what's actually available to spend. A planning view only - nothing here commits anything."
		);

		tv_available_cash = findViewById(R.id.tv_available_cash);
		tv_total_suggested = findViewById(R.id.tv_total_suggested);
		tv_remaining = findViewById(R.id.tv_remaining);
		tv_no_suggestions = findViewById(R.id.tv_no_suggestions);
		container_budget_by_supplier = findViewById(R.id.container_budget_by_supplier);

		db = new DatabaseHelper(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadBudget();
	}

	private void loadBudget() {

		ArrayList<HashMap<String, Object>> suggestions = db.getReorderSuggestions(this);

		double totalSuggested = 0;

		// LinkedHashMap keeps suppliers in first-seen order, same
		// convention ReorderListActivity's own per-supplier grouping uses.
		LinkedHashMap<String, Double> bySupplier = new LinkedHashMap<String, Double>();
		LinkedHashMap<String, Integer> itemCountBySupplier = new LinkedHashMap<String, Integer>();

		for (HashMap<String, Object> row : suggestions) {

			double cost = row.get("estimated_cost") == null ? 0 : (Double) row.get("estimated_cost");
			totalSuggested += cost;

			String supplierName = row.get("supplier_party_name") == null ?
				"No Supplier" : row.get("supplier_party_name").toString();

			Double existing = bySupplier.get(supplierName);
			bySupplier.put(supplierName, (existing == null ? 0 : existing) + cost);

			Integer existingCount = itemCountBySupplier.get(supplierName);
			itemCountBySupplier.put(supplierName, (existingCount == null ? 0 : existingCount) + 1);
		}

		double cashBalance = db.getCashBalance();
		double effectiveCashLimit = ReorderSettings.getEffectiveCashLimit(this, cashBalance);

		boolean cashAware = effectiveCashLimit < Double.MAX_VALUE;

		tv_available_cash.setText(
			cashAware ?
				AmountFormat.format(effectiveCashLimit) :
				AmountFormat.format(cashBalance) + " (cash-awareness off in Reorder Settings)"
		);

		tv_total_suggested.setText(AmountFormat.format(totalSuggested));

		double remaining = (cashAware ? effectiveCashLimit : cashBalance) - totalSuggested;

		tv_remaining.setText(AmountFormat.format(remaining));
		tv_remaining.setTextColor(getResources().getColor(
			remaining < 0 ? R.color.danger : R.color.success));

		container_budget_by_supplier.removeAllViews();

		if (bySupplier.isEmpty()) {

			tv_no_suggestions.setVisibility(View.VISIBLE);
			return;
		}

		tv_no_suggestions.setVisibility(View.GONE);

		for (String supplierName : bySupplier.keySet()) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_budget_by_supplier, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(supplierName);

			int itemCount = itemCountBySupplier.get(supplierName);
			tv_detail.setText(itemCount + (itemCount == 1 ? " item" : " items"));
			tv_badge.setText(AmountFormat.format(bySupplier.get(supplierName)));
			tv_badge.setTextColor(getResources().getColor(R.color.text_primary));

			container_budget_by_supplier.addView(view);
		}
	}
}
