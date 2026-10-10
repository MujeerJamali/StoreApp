package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

	// Only the top N suppliers by planned spend get a bar - a shop with
	// many suppliers would otherwise overload the chart's own "sparse"
	// threshold (see SimpleBarChartView).
	private static final int CHART_TOP_N = 8;

	private TextView tv_available_cash;
	private TextView tv_total_suggested;
	private TextView tv_remaining;
	private TextView tv_no_suggestions;
	private SimpleBarChartView chart_budget_by_supplier;
	private LinearLayout container_budget_by_supplier;

	private DatabaseHelper db;

	private long budgetLoadGeneration = 0;

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
		chart_budget_by_supplier = findViewById(R.id.chart_budget_by_supplier);
		container_budget_by_supplier = findViewById(R.id.container_budget_by_supplier);

		db = new DatabaseHelper(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadBudget();
	}

	// db.getReorderSuggestions() is the heaviest query in the app (it
	// runs several sub-queries per low-stock item/combo) and was being
	// called directly here on the main thread, freezing/crashing this
	// screen on a shop with any real amount of data - same bug as
	// TodayActionsActivity's loadSummary(), fixed the same way.
	private void loadBudget() {

		final long myGeneration = ++budgetLoadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> suggestions =
						db.getReorderSuggestions(BudgetPlannerActivity.this);

					final double cashBalance = db.getCashBalance();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != budgetLoadGeneration || isFinishing()) {
									return;
								}

								applyBudget(suggestions, cashBalance);
							}
						});
				}
			}).start();
	}

	private void applyBudget(
		ArrayList<HashMap<String, Object>> suggestions, double cashBalance) {

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
			chart_budget_by_supplier.setEntries(null);
			return;
		}

		tv_no_suggestions.setVisibility(View.GONE);

		// Planned spend per supplier is never negative (it's a sum of
		// estimated costs), so a plain ranking bar works fine - built from
		// the same bySupplier map the rows below use, sorted by amount
		// (biggest planned spend first) and capped to CHART_TOP_N, same
		// single color for every bar since suppliers are just a ranking,
		// not a sign/meaning split.
		ArrayList<SimpleBarChartView.Entry> allSupplierEntries = new ArrayList<SimpleBarChartView.Entry>();
		int chartColor = getResources().getColor(R.color.primary);

		for (String supplierName : bySupplier.keySet()) {
			allSupplierEntries.add(new SimpleBarChartView.Entry(
				supplierName, bySupplier.get(supplierName), chartColor
			));
		}

		Collections.sort(allSupplierEntries, new Comparator<SimpleBarChartView.Entry>() {
				@Override
				public int compare(SimpleBarChartView.Entry a, SimpleBarChartView.Entry b) {
					return Double.compare(b.value, a.value);
				}
			}
		);

		chart_budget_by_supplier.setEntries(
			new ArrayList<SimpleBarChartView.Entry>(
				allSupplierEntries.subList(0, Math.min(CHART_TOP_N, allSupplierEntries.size()))
			)
		);

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
