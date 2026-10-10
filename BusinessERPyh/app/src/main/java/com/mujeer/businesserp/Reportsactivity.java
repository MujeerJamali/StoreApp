package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

// =====================
// Every report in the app (approved feature list row #65's own follow-
// up request), grouped into sections instead of one long flat list, a
// search box that filters every section at once, and long-press to pin
// a report into a Favorites section at the top - same gesture/storage
// pattern as the Dashboard's own Favorites (see ReportFavorites,
// mirroring DashboardFavorites). One ReportEntry table below drives the
// click/long-click wiring, the search filter, and the dynamic Favorites
// section, instead of 30 near-identical manually written blocks.
// =====================
public class Reportsactivity extends Activity {

	private static final class ReportEntry {

		final int buttonId;
		final int rowId;
		final int sectionId;
		final String favoriteKey;
		final String label;
		final Class<? extends Activity> target;

		// Extra, never-shown search text for a feature that's real but
		// buried inside this report rather than a screen of its own
		// (approved feature list row #65's own follow-up - a user who
		// typed the feature's own name here otherwise gets zero
		// results and assumes it doesn't exist). E.g. "Top Customers by
		// profit" is really the Profit sort on Sales by Party, and
		// "best category this month vs last" is a section inside
		// Month-over-Month, not a report of their own.
		final String keywords;

		ReportEntry(
			int buttonId, int rowId, int sectionId, String favoriteKey,
			String label, Class<? extends Activity> target) {

			this(buttonId, rowId, sectionId, favoriteKey, label, target, "");
		}

		ReportEntry(
			int buttonId, int rowId, int sectionId, String favoriteKey,
			String label, Class<? extends Activity> target, String keywords) {

			this.buttonId = buttonId;
			this.rowId = rowId;
			this.sectionId = sectionId;
			this.favoriteKey = favoriteKey;
			this.label = label;
			this.target = target;
			this.keywords = keywords;
		}
	}

	// Static (not an instance field) so MainActivity can resolve a
	// pinned report's target Activity/label without instantiating this
	// screen - see getTargetForFavoriteKey()/getLabelForFavoriteKey()
	// below, which let a report pinned here (ReportFavorites) also show
	// up as one of the Dashboard's own Favorites card shortcuts.
	private static final ReportEntry[] REPORTS = new ReportEntry[]{

		new ReportEntry(R.id.btn_report_sales, R.id.row_sales, R.id.section_sales_profit,
			"sales", "Total Sales by Period", Salesreportactivity.class),
		new ReportEntry(R.id.btn_report_party_sales, R.id.row_party_sales, R.id.section_sales_profit,
			"party_sales", "Sales by Party", Partysalesreportactivity.class,
			"top customers by profit party-wise profit contribution"),
		new ReportEntry(R.id.btn_report_net_profit, R.id.row_net_profit, R.id.section_sales_profit,
			"net_profit", "Net Profit by Period", NetProfitReportActivity.class),
		new ReportEntry(R.id.btn_report_month_over_month, R.id.row_month_over_month, R.id.section_sales_profit,
			"month_over_month", "Month-over-Month", MonthOverMonthReportActivity.class,
			"best category this month vs last month"),
		new ReportEntry(R.id.btn_report_item_monthly_rank, R.id.row_item_monthly_rank, R.id.section_sales_profit,
			"item_monthly_rank", "Item Monthly Rank (Sales/Profit)", ItemMonthlyRankReportActivity.class),
		new ReportEntry(R.id.btn_report_average_cart, R.id.row_average_cart, R.id.section_sales_profit,
			"average_cart", "Average Cart Size/Amount", AverageCartReportActivity.class),
		new ReportEntry(R.id.btn_report_profit_split, R.id.row_profit_split, R.id.section_sales_profit,
			"profit_split", "Profit: Cash Sale vs Party", ProfitSplitReportActivity.class),
		new ReportEntry(R.id.btn_report_shoes_vs_non_shoes, R.id.row_shoes_vs_non_shoes, R.id.section_sales_profit,
			"shoes_vs_non_shoes", "Shoes vs Non-Shoes", ShoesVsNonShoesReportActivity.class),

		new ReportEntry(R.id.btn_report_item_ranking, R.id.row_item_ranking, R.id.section_inventory_stock,
			"item_ranking", "Item Ranking (All Periods Combined)", Itemrankingreportactivity.class),
		new ReportEntry(R.id.btn_report_stock_worth, R.id.row_stock_worth, R.id.section_inventory_stock,
			"stock_worth", "Stock Worth", StockWorthReportActivity.class),
		new ReportEntry(R.id.btn_report_low_stock, R.id.row_low_stock, R.id.section_inventory_stock,
			"low_stock", "Low Stock", LowStockReportActivity.class),
		new ReportEntry(R.id.btn_report_slow_moving_stock, R.id.row_slow_moving_stock, R.id.section_inventory_stock,
			"slow_moving_stock", "Slow-Moving Stock", SlowMovingStockReportActivity.class),
		new ReportEntry(R.id.btn_report_dead_stock_aging, R.id.row_dead_stock_aging, R.id.section_inventory_stock,
			"dead_stock_aging", "Dead Stock Aging", DeadStockAgingReportActivity.class),
		new ReportEntry(R.id.btn_report_stock_value, R.id.row_stock_value, R.id.section_inventory_stock,
			"stock_value", "Stock Value", StockValueReportActivity.class),
		new ReportEntry(R.id.btn_report_size_curve, R.id.row_size_curve, R.id.section_inventory_stock,
			"size_curve", "Size-Curve Analysis", SizeCurveAnalysisReportActivity.class,
			"tool durable goods slower turnover handling"),
		new ReportEntry(R.id.btn_report_combo_stock, R.id.row_combo_stock, R.id.section_inventory_stock,
			"combo_stock", "Combo/Variety Stock", ComboStockReportActivity.class),
		new ReportEntry(R.id.btn_report_discount_stop_restock, R.id.row_discount_stop_restock, R.id.section_inventory_stock,
			"discount_stop_restock", "Discount & Stop-Restocking", DiscountStopRestockReportActivity.class),

		new ReportEntry(R.id.btn_report_budget_planner, R.id.row_budget_planner, R.id.section_reorder_planning,
			"budget_planner", "Budget Planner", BudgetPlannerActivity.class),
		new ReportEntry(R.id.btn_report_scenario_check, R.id.row_scenario_check, R.id.section_reorder_planning,
			"scenario_check", "Scenario Check", ScenarioCheckActivity.class),
		new ReportEntry(R.id.btn_report_min_order_qty, R.id.row_min_order_qty, R.id.section_reorder_planning,
			"min_order_qty", "Min Order Quantities", MinOrderQtyActivity.class),
		new ReportEntry(R.id.btn_report_cash_flow_forecast, R.id.row_cash_flow_forecast, R.id.section_reorder_planning,
			"cash_flow_forecast", "Cash Flow Forecast", CashFlowForecastReportActivity.class),
		new ReportEntry(R.id.btn_report_cash_projection, R.id.row_cash_projection, R.id.section_reorder_planning,
			"cash_projection", "Cash Projection", CashProjectionReportActivity.class),
		new ReportEntry(R.id.btn_report_cross_sell, R.id.row_cross_sell, R.id.section_reorder_planning,
			"cross_sell", "Cross-Sell Insight", CrossSellReportActivity.class),

		new ReportEntry(R.id.btn_report_party_ranking, R.id.row_party_ranking, R.id.section_parties_customers,
			"party_ranking", "Party Ranking (All Periods Combined)", Partyrankingreportactivity.class),
		new ReportEntry(R.id.btn_report_party_balances, R.id.row_party_balances, R.id.section_parties_customers,
			"party_balances", "Party Balances", PartyBalanceReportActivity.class),
		new ReportEntry(R.id.btn_report_credit_due, R.id.row_credit_due, R.id.section_parties_customers,
			"credit_due", "Credit Due", CreditDueReportActivity.class),
		new ReportEntry(R.id.btn_report_win_back, R.id.row_win_back, R.id.section_parties_customers,
			"win_back", "Win-Back List", WinBackListActivity.class),

		new ReportEntry(R.id.btn_report_day_close, R.id.row_day_close, R.id.section_cash_operations,
			"day_close", "Day Close", DayCloseReportActivity.class),
		new ReportEntry(R.id.btn_report_margin_profit_alerts, R.id.row_margin_profit_alerts, R.id.section_cash_operations,
			"margin_profit_alerts", "Margin & Profit Alerts", MarginProfitAlertReportActivity.class),
		new ReportEntry(R.id.btn_report_expense_ratio_trend, R.id.row_expense_ratio_trend, R.id.section_cash_operations,
			"expense_ratio_trend", "Expense Ratio Trend", ExpenseRatioTrendReportActivity.class,
			"unusual expense flag anomaly"),
	};

	private final int[] sectionIds = new int[]{
		R.id.section_sales_profit, R.id.section_inventory_stock, R.id.section_reorder_planning,
		R.id.section_parties_customers, R.id.section_cash_operations
	};

	private EditText et_report_search;
	private View section_favorites;
	private LinearLayout container_favorites;
	private TextView tv_no_reports_match;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reportsactivity);

		setTitle("Reports");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Reports",
			"Every report in the app, grouped by what it's about. Search narrows every section at once; long-press any report to pin it here as a Favorite - it also shows up as a shortcut in the Dashboard's own Favorites card, alongside pinned Tools. Most carry their own (i) explaining exactly what they measure - tap it before reading too much into a number you're not sure about."
		);

		et_report_search = findViewById(R.id.et_report_search);
		section_favorites = findViewById(R.id.section_favorites);
		container_favorites = findViewById(R.id.container_favorites);
		tv_no_reports_match = findViewById(R.id.tv_no_reports_match);

		for (final ReportEntry entry : REPORTS) {

			Button btn = findViewById(entry.buttonId);

			btn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						openReport(entry.target);
					}
				}
			);

			btn.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {

						boolean nowFavorite =
							ReportFavorites.toggleFavorite(Reportsactivity.this, entry.favoriteKey);

						Toast.makeText(
							Reportsactivity.this,
							nowFavorite ? "Pinned to Favorites" : "Unpinned from Favorites",
							Toast.LENGTH_SHORT
						).show();

						refreshFavorites();

						return true;
					}
				}
			);
		}

		et_report_search.addTextChangedListener(new TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(Editable s) {
					applyFilter(s.toString());
				}
			}
		);

		refreshFavorites();
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshFavorites();
	}

	private void openReport(Class<? extends Activity> target) {
		startActivity(new Intent(this, target));
	}

	// Lets MainActivity's Favorites card render and open a report pinned
	// here (via ReportFavorites) without duplicating this REPORTS table
	// or instantiating this Activity - null means the key isn't a report
	// favoriteKey at all (e.g. it's one of MainActivity's own Tool Row
	// keys instead), which the caller treats as "not a report."
	public static Class<? extends Activity> getTargetForFavoriteKey(String key) {

		for (ReportEntry entry : REPORTS) {

			if (entry.favoriteKey.equals(key)) {
				return entry.target;
			}
		}

		return null;
	}

	public static String getLabelForFavoriteKey(String key) {

		for (ReportEntry entry : REPORTS) {

			if (entry.favoriteKey.equals(key)) {
				return entry.label;
			}
		}

		return null;
	}

	private ReportEntry findByFavoriteKey(String key) {

		for (ReportEntry entry : REPORTS) {

			if (entry.favoriteKey.equals(key)) {
				return entry;
			}
		}

		return null;
	}

	private void refreshFavorites() {

		ArrayList<String> favoriteKeys = ReportFavorites.getFavorites(this);

		container_favorites.removeAllViews();

		if (favoriteKeys.isEmpty()) {

			section_favorites.setVisibility(View.GONE);

		} else {

			section_favorites.setVisibility(View.VISIBLE);

			LayoutInflater inflater = LayoutInflater.from(this);

			for (int i = 0; i < favoriteKeys.size(); i++) {

				final ReportEntry entry = findByFavoriteKey(favoriteKeys.get(i));

				if (entry == null) {
					continue;
				}

				View row = inflater.inflate(
					R.layout.dashboard_favorite_row, container_favorites, false
				);

				Button btn = row.findViewById(R.id.btn_favorite_shortcut);
				btn.setText(entry.label);

				btn.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							openReport(entry.target);
						}
					}
				);

				btn.setOnLongClickListener(new View.OnLongClickListener() {
						@Override
						public boolean onLongClick(View v) {

							ReportFavorites.toggleFavorite(Reportsactivity.this, entry.favoriteKey);

							Toast.makeText(
								Reportsactivity.this, "Unpinned from Favorites", Toast.LENGTH_SHORT
							).show();

							refreshFavorites();

							return true;
						}
					}
				);

				container_favorites.addView(row);

				if (i < favoriteKeys.size() - 1) {

					View divider = new View(this);

					divider.setLayoutParams(new LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT, 1
					));

					divider.setBackgroundColor(getResources().getColor(R.color.stroke));

					container_favorites.addView(divider);
				}
			}
		}

		applyFilter(et_report_search == null ? "" : et_report_search.getText().toString());
	}

	private void applyFilter(String query) {

		boolean anyMatch = false;

		boolean[] sectionHasMatch = new boolean[sectionIds.length];

		for (ReportEntry entry : REPORTS) {

			boolean matches = SearchUtils.matchesTokensAcrossFields(query, entry.label, entry.keywords);

			View row = findViewById(entry.rowId);
			row.setVisibility(matches ? View.VISIBLE : View.GONE);

			if (matches) {

				anyMatch = true;

				for (int i = 0; i < sectionIds.length; i++) {

					if (sectionIds[i] == entry.sectionId) {
						sectionHasMatch[i] = true;
					}
				}
			}
		}

		for (int i = 0; i < sectionIds.length; i++) {

			View section = findViewById(sectionIds[i]);
			section.setVisibility(sectionHasMatch[i] ? View.VISIBLE : View.GONE);
		}

		tv_no_reports_match.setVisibility(anyMatch ? View.GONE : View.VISIBLE);
	}
}
