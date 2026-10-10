package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

// =====================
// One small, self-contained snapshot query per report that's safe to
// show as a live Dashboard widget - always "today" (or that report's
// own shortest meaningful default period), reusing the SAME
// DatabaseHelper methods each report's own full page already calls so
// the widget and the full report can never disagree about what a
// number means. Deliberately kept OUT of each report Activity itself:
// an Activity's own chart-building code stays focused on whatever
// period the user has selected there (today/this week/custom/etc),
// while this class owns the one fixed, no-Activity-instance-needed
// snapshot used for the Dashboard - see DashboardChartWidgetsActivity
// (the picker) and MainActivity#refreshChartWidgets() (where this gets
// rendered).
//
// Only reports with a cheap, parameterless-or-"today" default query
// and an always-unambiguous chart type are covered here - not every
// report that has its own chart on its own page is automatically a
// good fit for a glanceable, no-selector Dashboard card (a report that
// needs the user to pick an item/category first has nothing sensible
// to show with zero input). More keys can be added to SUPPORTED_KEYS/
// SUPPORTED_LABELS/load() whenever a report's own default view is
// simple enough to snapshot this way - same "ongoing, pick it up
// again whenever touching this" spirit as the charts-on-reports
// rollout itself (see CLAUDE.md).
// =====================
public class DashboardChartWidgetLoader {

	public static final int TYPE_BAR = 0;
	public static final int TYPE_PIE = 1;
	public static final int TYPE_LINE = 2;

	// Same favoriteKey spelling Reportsactivity/ReportFavorites already
	// use for these reports, so one key namespace covers both "pin as
	// a text shortcut" and "show as a live chart widget" - not a new,
	// separate id scheme.
	public static final String[] SUPPORTED_KEYS = {
		"net_profit", "month_over_month", "stock_worth",
		"profit_split", "shoes_vs_non_shoes", "expense_ratio_trend"
	};

	public static final String[] SUPPORTED_LABELS = {
		"Net Profit by Period", "Month-over-Month", "Stock Worth",
		"Profit: Cash Sale vs Party", "Shoes vs Non-Shoes", "Expense Ratio Trend"
	};

	public static class WidgetChart {

		public final String title;
		public final int chartType;
		public final ArrayList<SimpleBarChartView.Entry> entries;
		public final Class<? extends Activity> targetActivity;

		public WidgetChart(
			String title, int chartType,
			ArrayList<SimpleBarChartView.Entry> entries, Class<? extends Activity> targetActivity) {

			this.title = title;
			this.chartType = chartType;
			this.entries = entries;
			this.targetActivity = targetActivity;
		}
	}

	private static final SimpleDateFormat DATE_FORMAT =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	public static String labelFor(String key) {

		for (int i = 0; i < SUPPORTED_KEYS.length; i++) {

			if (SUPPORTED_KEYS[i].equals(key)) {
				return SUPPORTED_LABELS[i];
			}
		}

		return key;
	}

	public static WidgetChart load(Context context, String key) {

		DatabaseHelper db = new DatabaseHelper(context);
		String today = DATE_FORMAT.format(new Date());

		switch (key) {

			case "net_profit":
				return loadNetProfit(context, db, today);

			case "month_over_month":
				return loadMonthOverMonth(context, db);

			case "stock_worth":
				return loadStockWorth(context, db);

			case "profit_split":
				return loadProfitSplit(context, db, today);

			case "shoes_vs_non_shoes":
				return loadShoesVsNonShoes(context, db, today);

			case "expense_ratio_trend":
				return loadExpenseRatioTrend(context, db);

			default:
				return null;
		}
	}

	private static WidgetChart loadNetProfit(Context context, DatabaseHelper db, String today) {

		HashMap<String, Object> summary = db.getNetProfitSummary(today, today);

		double salesTotal = (Double) summary.get("sales_total");
		double itemCost = (Double) summary.get("item_cost");
		double expensesTotal = (Double) summary.get("expenses_total");
		double netProfit = (Double) summary.get("net_profit");

		ArrayList<SimpleBarChartView.Entry> entries = new ArrayList<SimpleBarChartView.Entry>();
		entries.add(new SimpleBarChartView.Entry("Sales", salesTotal, context.getResources().getColor(R.color.mod_sales)));
		entries.add(new SimpleBarChartView.Entry("Item Cost", itemCost, context.getResources().getColor(R.color.mod_purchase)));
		entries.add(new SimpleBarChartView.Entry("Expenses", expensesTotal, context.getResources().getColor(R.color.mod_expenses)));
		entries.add(new SimpleBarChartView.Entry("Net Profit", netProfit, context.getResources().getColor(R.color.primary)));

		return new WidgetChart("Net Profit (Today)", TYPE_BAR, entries, NetProfitReportActivity.class);
	}

	private static WidgetChart loadMonthOverMonth(Context context, DatabaseHelper db) {

		Calendar thisMonthStart = Calendar.getInstance();
		thisMonthStart.set(Calendar.DAY_OF_MONTH, 1);
		thisMonthStart.set(Calendar.HOUR_OF_DAY, 0);
		thisMonthStart.set(Calendar.MINUTE, 0);
		thisMonthStart.set(Calendar.SECOND, 0);
		thisMonthStart.set(Calendar.MILLISECOND, 0);

		String thisFrom = DATE_FORMAT.format(thisMonthStart.getTime());
		String thisTo = DATE_FORMAT.format(new Date());

		Calendar lastMonthEnd = (Calendar) thisMonthStart.clone();
		lastMonthEnd.add(Calendar.DAY_OF_MONTH, -1);

		Calendar lastMonthStart = (Calendar) lastMonthEnd.clone();
		lastMonthStart.set(Calendar.DAY_OF_MONTH, 1);

		String lastFrom = DATE_FORMAT.format(lastMonthStart.getTime());
		String lastTo = DATE_FORMAT.format(lastMonthEnd.getTime());

		HashMap<String, Object> thisSummary = db.getNetProfitSummary(thisFrom, thisTo);
		HashMap<String, Object> lastSummary = db.getNetProfitSummary(lastFrom, lastTo);

		double salesThis = (Double) thisSummary.get("sales_total");
		double salesLast = (Double) lastSummary.get("sales_total");
		double profitThis = (Double) thisSummary.get("net_profit");
		double profitLast = (Double) lastSummary.get("net_profit");
		double expensesThis = (Double) thisSummary.get("expenses_total");
		double expensesLast = (Double) lastSummary.get("expenses_total");

		ArrayList<SimpleBarChartView.Entry> entries = new ArrayList<SimpleBarChartView.Entry>();

		entries.add(new SimpleBarChartView.Entry("Sales (Last)", salesLast, context.getResources().getColor(R.color.mod_sales)));
		entries.add(new SimpleBarChartView.Entry("Sales (This)", salesThis, context.getResources().getColor(R.color.mod_sales)));
		entries.add(new SimpleBarChartView.Entry("Profit (Last)", profitLast, context.getResources().getColor(R.color.primary)));
		entries.add(new SimpleBarChartView.Entry("Profit (This)", profitThis, context.getResources().getColor(R.color.primary)));
		entries.add(new SimpleBarChartView.Entry("Expenses (Last)", expensesLast, context.getResources().getColor(R.color.mod_expenses)));
		entries.add(new SimpleBarChartView.Entry("Expenses (This)", expensesThis, context.getResources().getColor(R.color.mod_expenses)));

		return new WidgetChart("Month-over-Month", TYPE_BAR, entries, MonthOverMonthReportActivity.class);
	}

	private static WidgetChart loadStockWorth(Context context, DatabaseHelper db) {

		HashMap<String, Object> summary = db.getStockWorthSummary();

		double shoesWorth = (Double) summary.get("shoes_worth");
		double nonShoesWorth = (Double) summary.get("non_shoes_worth");

		ArrayList<SimpleBarChartView.Entry> entries = new ArrayList<SimpleBarChartView.Entry>();
		entries.add(new SimpleBarChartView.Entry("Shoes", shoesWorth, context.getResources().getColor(R.color.mod_sales)));
		entries.add(new SimpleBarChartView.Entry("Non-Shoes", nonShoesWorth, context.getResources().getColor(R.color.primary)));

		return new WidgetChart("Stock Worth", TYPE_BAR, entries, StockWorthReportActivity.class);
	}

	// A pie reads much more naturally as "share of profit" than a bar
	// pair, but can only plot positive slices - so Pie is only used
	// when both sides actually turned a profit today, same guard
	// ProfitSplitReportActivity's own full page applies.
	private static WidgetChart loadProfitSplit(Context context, DatabaseHelper db, String today) {

		HashMap<String, Object> summary = db.getProfitSplitByPartyType(today, today);

		double cashProfit = (Double) summary.get("cash_profit");
		double partyProfit = (Double) summary.get("party_profit");

		ArrayList<SimpleBarChartView.Entry> entries = new ArrayList<SimpleBarChartView.Entry>();
		entries.add(new SimpleBarChartView.Entry("Cash Sale", cashProfit, context.getResources().getColor(R.color.mod_sales)));
		entries.add(new SimpleBarChartView.Entry("Named Party", partyProfit, context.getResources().getColor(R.color.primary)));

		boolean bothPositive = cashProfit > 0 && partyProfit > 0;

		return new WidgetChart(
			"Profit: Cash Sale vs Party (Today)",
			bothPositive ? TYPE_PIE : TYPE_BAR,
			entries,
			ProfitSplitReportActivity.class
		);
	}

	// Sales amounts are never negative, so this (the Sales-mode
	// default) always reads as a true share-of-whole pie - see
	// ShoesVsNonShoesReportActivity's own full page for the Profit-mode
	// case, which isn't used here precisely because it can go negative.
	private static WidgetChart loadShoesVsNonShoes(Context context, DatabaseHelper db, String today) {

		HashMap<String, Object> summary = db.getShoesVsNonShoesSummary(today, today);

		double shoesAmount = (Double) summary.get("shoes_sales");
		double nonShoesAmount = (Double) summary.get("non_shoes_sales");

		ArrayList<SimpleBarChartView.Entry> entries = new ArrayList<SimpleBarChartView.Entry>();
		entries.add(new SimpleBarChartView.Entry("Shoes", shoesAmount, context.getResources().getColor(R.color.mod_sales)));
		entries.add(new SimpleBarChartView.Entry("Non-Shoes", nonShoesAmount, context.getResources().getColor(R.color.primary)));

		boolean bothPositive = shoesAmount > 0 && nonShoesAmount > 0;

		return new WidgetChart(
			"Shoes vs Non-Shoes Sales (Today)",
			bothPositive ? TYPE_PIE : TYPE_BAR,
			entries,
			ShoesVsNonShoesReportActivity.class
		);
	}

	private static WidgetChart loadExpenseRatioTrend(Context context, DatabaseHelper db) {

		ArrayList<HashMap<String, Object>> list = db.getExpenseRatioTrend(6);

		ArrayList<SimpleBarChartView.Entry> entries = new ArrayList<SimpleBarChartView.Entry>();

		for (HashMap<String, Object> row : list) {

			double ratio = (Double) row.get("ratio_percent");

			entries.add(new SimpleBarChartView.Entry(
				String.valueOf(row.get("month_label")), ratio, context.getResources().getColor(R.color.primary)
			));
		}

		return new WidgetChart("Expense Ratio Trend", TYPE_LINE, entries, ExpenseRatioTrendReportActivity.class);
	}
}
