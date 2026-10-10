package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity {

	Button btn_parties;
	Button btn_items;
	Button btn_purchases;
	Button btn_sales;
	Button btn_expenses;

	Button btn_transactions_purchase;
	Button btn_transactions_sale;

	Button btn_payments;

	Button btn_reports;

	Button btn_import;
	Button btn_generate_entries;
	Button btn_cash;
	Button btn_wanted_items;
	Button btn_recurring_expenses;
	Button btn_drafts;
	Button btn_cost_items;
	Button btn_display_shoes;
	Button btn_sample_shoes;
	Button btn_recently_deleted;
	Button btn_reorder_list;
	Button btn_bulk_item_update;
	Button btn_loyalty_points;
	Button btn_stock_take;
	Button btn_quick_sale;
	Button btn_today_actions;
	Button btn_customize_dashboard;
	Button btn_swipe_gesture_settings;

	Button btn_quick_add;

	TextView tv_cash_balance;
	TextView tv_expense_today;
	TextView tv_expense_week;
	TextView tv_expense_month;

	Spinner spinner_sales_trend_horizon;
	SimpleBarChartView chart_sales_trend;
	Spinner spinner_trend_compare_mode;
	TextView tv_today_vs_last_week;
	TextView tv_today_vs_last_week_change;
	TextView tv_sales_streak;
	TextView tv_sales_streak_best;

	private static final int[] TREND_HORIZON_DAYS = {7, 30};
	private static final String[] TREND_HORIZON_LABELS = {"7 Days", "30 Days"};

	private int selectedTrendHorizonDays = TREND_HORIZON_DAYS[0];

	private static final int[] TREND_COMPARE_MODE_VALUES = {
		DatabaseHelper.TREND_COMPARE_TODAY_VS_LAST_WEEK,
		DatabaseHelper.TREND_COMPARE_WEEK_VS_WEEK,
		DatabaseHelper.TREND_COMPARE_MONTH_VS_MONTH
	};

	private static final String[] TREND_COMPARE_MODE_LABELS = {
		"Today vs Last Week", "This Week vs Last Week", "This Month vs Last Month"
	};

	private int selectedTrendCompareMode = DatabaseHelper.TREND_COMPARE_TODAY_VS_LAST_WEEK;

	// A background result is only applied if it's still the most
	// recent request by the time it comes back.
	private long trendLoadGeneration = 0;
	private long todayVsLastWeekLoadGeneration = 0;
	private long salesStreakLoadGeneration = 0;

	EditText et_dashboard_search;

	View block_favorites;
	View row_favorites_label;
	LinearLayout card_favorites;
	View card_cash_summary;
	View card_sales_trend;

	// Every long-press-pinnable Tool Row on this screen (both the
	// Modules and Tools cards), keyed by its own button id's resource
	// name - the same key DashboardFavorites stores and
	// openShortcut(key) switches on.
	private static final String[] SHORTCUT_KEYS = {
		"btn_parties", "btn_items", "btn_transactions_purchase", "btn_transactions_sale",
		"btn_payments", "btn_expenses", "btn_reports",
		"btn_import", "btn_generate_entries", "btn_cash", "btn_wanted_items",
		"btn_recurring_expenses", "btn_drafts", "btn_cost_items",
		"btn_display_shoes", "btn_sample_shoes", "btn_recently_deleted",
		"btn_reorder_list", "btn_bulk_item_update", "btn_loyalty_points",
		"btn_stock_take", "btn_quick_sale", "btn_today_actions", "btn_customize_dashboard",
		"btn_swipe_gesture_settings"
	};

	private static final String[] SHORTCUT_LABELS = {
		"Parties", "Items", "Purchases", "Sales",
		"Payments", "Expenses", "Reports",
		"Import", "Generate Entries", "Cash", "Wanted Items",
		"Recurring Expenses", "Drafts", "Cost Items",
		"Display Shoes", "Sample Shoes", "Recently Deleted",
		"Reorder List", "Bulk Item Update", "Loyalty Points",
		"Stock Take", "Quick Sale", "What To Do Today", "Customize Dashboard",
		"Swipe Gesture Settings"
	};

	DatabaseHelper db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		

		btn_parties = findViewById(R.id.btn_parties);
		btn_items = findViewById(R.id.btn_items);

		btn_transactions_purchase = findViewById(R.id.btn_transactions_purchase);
		btn_transactions_sale = findViewById(R.id.btn_transactions_sale);

		btn_payments = findViewById(R.id.btn_payments);
		btn_expenses = findViewById(R.id.btn_expenses);

		btn_reports = findViewById(R.id.btn_reports);

		btn_import = findViewById(R.id.btn_import);
		btn_generate_entries = findViewById(R.id.btn_generate_entries);
		btn_cash = findViewById(R.id.btn_cash);
		btn_wanted_items = findViewById(R.id.btn_wanted_items);
		btn_recurring_expenses = findViewById(R.id.btn_recurring_expenses);
		btn_drafts = findViewById(R.id.btn_drafts);
		btn_cost_items = findViewById(R.id.btn_cost_items);
		btn_display_shoes = findViewById(R.id.btn_display_shoes);
		btn_sample_shoes = findViewById(R.id.btn_sample_shoes);
		btn_recently_deleted = findViewById(R.id.btn_recently_deleted);
		btn_reorder_list = findViewById(R.id.btn_reorder_list);
		btn_bulk_item_update = findViewById(R.id.btn_bulk_item_update);
		btn_loyalty_points = findViewById(R.id.btn_loyalty_points);
		btn_stock_take = findViewById(R.id.btn_stock_take);
		btn_quick_sale = findViewById(R.id.btn_quick_sale);
		btn_today_actions = findViewById(R.id.btn_today_actions);
		btn_customize_dashboard = findViewById(R.id.btn_customize_dashboard);
		btn_swipe_gesture_settings = findViewById(R.id.btn_swipe_gesture_settings);

		btn_quick_add = findViewById(R.id.btn_quick_add);

		tv_cash_balance = findViewById(R.id.tv_cash_balance);
		tv_expense_today = findViewById(R.id.tv_expense_today);
		tv_expense_week = findViewById(R.id.tv_expense_week);
		tv_expense_month = findViewById(R.id.tv_expense_month);

		InfoBubbleView info_bubble_cash = findViewById(R.id.info_bubble_cash);
		info_bubble_cash.setInfo(
			"Cash in Hand",
			"Running cash balance: every Sale's paid amount and Payment In, minus every Purchase's paid amount, Payment Out, and Expense's paid amount, plus manual Cash Adjustments. Today's/This Week's/This Month's Expense figures below are a separate, simpler total - just paid-or-not expenses in that window, excluding one already counted as a Purchase's landed cost."
		);

		spinner_sales_trend_horizon = findViewById(R.id.spinner_sales_trend_horizon);
		chart_sales_trend = findViewById(R.id.chart_sales_trend);
		spinner_trend_compare_mode = findViewById(R.id.spinner_trend_compare_mode);
		tv_today_vs_last_week = findViewById(R.id.tv_today_vs_last_week);
		tv_today_vs_last_week_change = findViewById(R.id.tv_today_vs_last_week_change);
		tv_sales_streak = findViewById(R.id.tv_sales_streak);
		tv_sales_streak_best = findViewById(R.id.tv_sales_streak_best);

		InfoBubbleView info_bubble_sales_trend = findViewById(R.id.info_bubble_sales_trend);
		info_bubble_sales_trend.setInfo(
			"Sales Trend",
			"Daily sales total for the last 7/30 days, including a day with zero sales - a quick \"is the shop busy lately\" glance, not a profit report. Below it, pick Today/This Week/This Month to compare its sales-so-far against the same elapsed window of the period before it (a week-ago weekday instead of yesterday, since a Monday is naturally busier or quieter than a Sunday) - never a partial period against a full one, so the percentage isn't skewed by one side simply having fewer days counted. At the bottom, a streak counter shows how many days in a row have had at least one sale, plus the best streak ever - a small motivational nudge, not a report."
		);

		et_dashboard_search = findViewById(R.id.et_dashboard_search);

		block_favorites = findViewById(R.id.block_favorites);
		row_favorites_label = findViewById(R.id.row_favorites_label);
		card_favorites = findViewById(R.id.card_favorites);
		card_cash_summary = findViewById(R.id.card_cash_summary);
		card_sales_trend = findViewById(R.id.card_sales_trend);

		InfoBubbleView info_bubble_favorites = findViewById(R.id.info_bubble_favorites);
		info_bubble_favorites.setInfo(
			"Favorites",
			"Long-press any row in the Modules or Tools card below, or any report in the Reports screen, to pin it here for quick access - long-press a pinned row here to unpin it. Pin order is remembered across app opens."
		);

		db = new DatabaseHelper(this);

		et_dashboard_search.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					startActivity(new Intent(MainActivity.this, GlobalSearchActivity.class));
				}
			});

		attachFavoriteLongPress(btn_parties, "btn_parties");
		attachFavoriteLongPress(btn_items, "btn_items");
		attachFavoriteLongPress(btn_transactions_purchase, "btn_transactions_purchase");
		attachFavoriteLongPress(btn_transactions_sale, "btn_transactions_sale");
		attachFavoriteLongPress(btn_payments, "btn_payments");
		attachFavoriteLongPress(btn_expenses, "btn_expenses");
		attachFavoriteLongPress(btn_reports, "btn_reports");
		attachFavoriteLongPress(btn_import, "btn_import");
		attachFavoriteLongPress(btn_generate_entries, "btn_generate_entries");
		attachFavoriteLongPress(btn_cash, "btn_cash");
		attachFavoriteLongPress(btn_wanted_items, "btn_wanted_items");
		attachFavoriteLongPress(btn_recurring_expenses, "btn_recurring_expenses");
		attachFavoriteLongPress(btn_drafts, "btn_drafts");
		attachFavoriteLongPress(btn_cost_items, "btn_cost_items");
		attachFavoriteLongPress(btn_display_shoes, "btn_display_shoes");
		attachFavoriteLongPress(btn_sample_shoes, "btn_sample_shoes");
		attachFavoriteLongPress(btn_recently_deleted, "btn_recently_deleted");
		attachFavoriteLongPress(btn_reorder_list, "btn_reorder_list");
		attachFavoriteLongPress(btn_bulk_item_update, "btn_bulk_item_update");
		attachFavoriteLongPress(btn_loyalty_points, "btn_loyalty_points");
		attachFavoriteLongPress(btn_stock_take, "btn_stock_take");
		attachFavoriteLongPress(btn_quick_sale, "btn_quick_sale");
		attachFavoriteLongPress(btn_today_actions, "btn_today_actions");
		attachFavoriteLongPress(btn_customize_dashboard, "btn_customize_dashboard");
		attachFavoriteLongPress(btn_swipe_gesture_settings, "btn_swipe_gesture_settings");

		ArrayAdapter<String> trendHorizonAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, TREND_HORIZON_LABELS
		);

		trendHorizonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_sales_trend_horizon.setAdapter(trendHorizonAdapter);

		spinner_sales_trend_horizon.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedTrendHorizonDays = TREND_HORIZON_DAYS[position];
					loadSalesTrend();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		ArrayAdapter<String> trendCompareAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, TREND_COMPARE_MODE_LABELS
		);

		trendCompareAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_trend_compare_mode.setAdapter(trendCompareAdapter);

		int rememberedCompareModePosition =
			FilterMemory.getInt(this, "Dashboard", "trend_compare_mode", 0);

		spinner_trend_compare_mode.setSelection(rememberedCompareModePosition);
		selectedTrendCompareMode = TREND_COMPARE_MODE_VALUES[rememberedCompareModePosition];

		spinner_trend_compare_mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedTrendCompareMode = TREND_COMPARE_MODE_VALUES[position];

					FilterMemory.setInt(MainActivity.this, "Dashboard", "trend_compare_mode", position);

					loadTodayVsLastWeek();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});


		btn_expenses.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						Expensesactivity.class
					);

					startActivity(intent);
				}
			}
		);
		
		
		btn_parties.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						Partiesactivity.class
					);

					startActivity(intent);
				}
			});

		btn_items.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						Itemsactivity.class
					);

					startActivity(intent);
				}
			});

		btn_transactions_purchase.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						Transactionactivity.class
					);

					intent.putExtra("transaction_type", 0);

					startActivity(intent);
				}
			});

		btn_transactions_sale.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						Transactionactivity.class
					);

					intent.putExtra("transaction_type", 1);

					startActivity(intent);
				}
			});

		btn_payments.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						Paymentactivity.class
					);

					startActivity(intent);
				}
			});

		btn_reports.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						Reportsactivity.class
					);

					startActivity(intent);
				}
			});

		btn_import.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						Importexcelactivity.class
					);

					startActivity(intent);
				}
			});

		btn_generate_entries.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						GenerateEntriesActivity.class
					);

					startActivity(intent);
				}
			});

		btn_cash.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						CashActivity.class
					);

					startActivity(intent);
				}
			});

		btn_wanted_items.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						WantedItemsActivity.class
					);

					startActivity(intent);
				}
			});

		btn_recurring_expenses.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						RecurringExpensesActivity.class
					);

					startActivity(intent);
				}
			});

		btn_drafts.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						DraftsActivity.class
					);

					startActivity(intent);
				}
			});

		btn_cost_items.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						CostItemsActivity.class
					);

					startActivity(intent);
				}
			});

		btn_display_shoes.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						DisplayShoesActivity.class
					);

					startActivity(intent);
				}
			});

		btn_sample_shoes.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						SampleShoesActivity.class
					);

					startActivity(intent);
				}
			});

		btn_recently_deleted.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						RecentlyDeletedActivity.class
					);

					startActivity(intent);
				}
			});

		btn_loyalty_points.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						LoyaltyPointsActivity.class
					);

					startActivity(intent);
				}
			});

		btn_reorder_list.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						ReorderListActivity.class
					);

					startActivity(intent);
				}
			});

		btn_bulk_item_update.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						BulkItemUpdateActivity.class
					);

					startActivity(intent);
				}
			});

		btn_stock_take.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						StockTakeActivity.class
					);

					startActivity(intent);
				}
			});

		btn_quick_sale.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						QuickSaleActivity.class
					);

					startActivity(intent);
				}
			});

		btn_today_actions.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						TodayActionsActivity.class
					);

					startActivity(intent);
				}
			});

		btn_customize_dashboard.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						DashboardCustomizeActivity.class
					);

					startActivity(intent);
				}
			});

		btn_swipe_gesture_settings.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						MainActivity.this,
						SwipeGestureSettingsActivity.class
					);

					startActivity(intent);
				}
			});

		btn_quick_add.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					PopupMenu popup = new PopupMenu(MainActivity.this, v);
					popup.getMenu().add(0, 1, 0, "New Sale");
					popup.getMenu().add(0, 2, 1, "New Purchase");
					popup.getMenu().add(0, 3, 2, "Payment In");
					popup.getMenu().add(0, 4, 3, "Payment Out");
					popup.getMenu().add(0, 5, 4, "Add Expense");
					popup.getMenu().add(0, 6, 5, "Add Party");

					popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
							@Override
							public boolean onMenuItemClick(MenuItem item) {

								Intent intent;

								switch (item.getItemId()) {
									case 1:
										intent = new Intent(MainActivity.this, Transactioneditactivity.class);
										intent.putExtra("transaction_type", 1);
										startActivity(intent);
										return true;
									case 2:
										intent = new Intent(MainActivity.this, Transactioneditactivity.class);
										intent.putExtra("transaction_type", 0);
										startActivity(intent);
										return true;
									case 3:
										intent = new Intent(MainActivity.this, Paymenteditactivity.class);
										intent.putExtra("payment_id", 0);
										intent.putExtra("payment_type", DatabaseHelper.PAYMENT_IN);
										startActivity(intent);
										return true;
									case 4:
										intent = new Intent(MainActivity.this, Paymenteditactivity.class);
										intent.putExtra("payment_id", 0);
										intent.putExtra("payment_type", DatabaseHelper.PAYMENT_OUT);
										startActivity(intent);
										return true;
									case 5:
										intent = new Intent(MainActivity.this, Expenseeditactivity.class);
										startActivity(intent);
										return true;
									case 6:
										intent = new Intent(MainActivity.this, Addpartyactivity.class);
										startActivity(intent);
										return true;
									default:
										return false;
								}
							}
						});

					popup.show();
				}
			});

	}

	@Override
	protected void onResume() {
		super.onResume();
		applyDashboardCardOrder();
		loadCashSummary();
		loadSalesTrend();
		loadTodayVsLastWeek();
		loadSalesStreak();
		refreshFavoritesCard();
	}

	// Customizable dashboard (approved feature "pick which cards show
	// first") - physically reorders the three top info-card blocks
	// (Favorites/Cash Summary/Sales Trend) per DashboardCardOrder's
	// saved order, and hides whichever ones the user turned off. Run
	// first on every resume (including right after returning from
	// DashboardCustomizeActivity) so a saved change takes effect
	// immediately; cheap and idempotent to repeat.
	private void applyDashboardCardOrder() {

		LinearLayout parent = (LinearLayout) card_cash_summary.getParent();

		View[] blocks = new View[]{block_favorites, card_cash_summary, card_sales_trend};
		String[] keys = new String[]{
			DashboardCardOrder.CARD_FAVORITES,
			DashboardCardOrder.CARD_CASH_SUMMARY,
			DashboardCardOrder.CARD_SALES_TREND
		};

		int insertIndex = parent.indexOfChild(block_favorites);

		for (View block : blocks) {
			insertIndex = Math.min(insertIndex, parent.indexOfChild(block));
		}

		for (View block : blocks) {
			parent.removeView(block);
		}

		ArrayList<String> order = DashboardCardOrder.getOrder(this);

		for (String key : order) {

			for (int i = 0; i < keys.length; i++) {

				if (keys[i].equals(key)) {
					parent.addView(blocks[i], insertIndex);
					insertIndex++;
				}
			}
		}

		for (int i = 0; i < keys.length; i++) {
			blocks[i].setVisibility(
				DashboardCardOrder.isVisible(this, keys[i]) ? View.VISIBLE : View.GONE
			);
		}
	}

	// Every Tool Row (Modules/Tools card buttons) gets the same
	// long-press behavior: toggle its pin and refresh the Favorites
	// card immediately, with a Toast so a long-press that didn't look
	// like it did anything still confirms it worked.
	private void attachFavoriteLongPress(Button button, final String key) {

		button.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {

					boolean nowFavorite = DashboardFavorites.toggleFavorite(MainActivity.this, key);

					Toast.makeText(
						MainActivity.this,
						nowFavorite ? "Added to Favorites" : "Removed from Favorites",
						Toast.LENGTH_SHORT
					).show();

					refreshFavoritesCard();

					return true;
				}
			});
	}

	private String labelForKey(String key) {

		for (int i = 0; i < SHORTCUT_KEYS.length; i++) {

			if (SHORTCUT_KEYS[i].equals(key)) {
				return SHORTCUT_LABELS[i];
			}
		}

		String reportLabel = Reportsactivity.getLabelForFavoriteKey(key);

		return reportLabel != null ? reportLabel : key;
	}

	// Combines both pinning stores - DashboardFavorites (long-press on a
	// Tool Row here) and ReportFavorites (long-press on a report in
	// Reportsactivity) - into one Favorites card, so any report or tool
	// can become a Dashboard shortcut, not just the fixed Modules/Tools
	// buttons on this screen. Tool and report favoriteKeys live in
	// disjoint namespaces (every Tool Row key starts with "btn_"; no
	// report favoriteKey does), so a key's own shape says which store it
	// came from - see unfavorite(key) below.
	private void refreshFavoritesCard() {

		java.util.ArrayList<String> favorites = new java.util.ArrayList<String>();
		favorites.addAll(DashboardFavorites.getFavorites(this));
		favorites.addAll(ReportFavorites.getFavorites(this));

		card_favorites.removeAllViews();

		if (favorites.isEmpty()) {

			row_favorites_label.setVisibility(View.GONE);
			card_favorites.setVisibility(View.GONE);

			return;
		}

		row_favorites_label.setVisibility(View.VISIBLE);
		card_favorites.setVisibility(View.VISIBLE);

		LayoutInflater inflater = LayoutInflater.from(this);

		for (int i = 0; i < favorites.size(); i++) {

			final String key = favorites.get(i);

			View row = inflater.inflate(R.layout.dashboard_favorite_row, card_favorites, false);

			Button btn = row.findViewById(R.id.btn_favorite_shortcut);
			btn.setText(labelForKey(key));

			btn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						openShortcut(key);
					}
				});

			btn.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {

						unfavorite(key);

						Toast.makeText(
							MainActivity.this, "Removed from Favorites", Toast.LENGTH_SHORT
						).show();

						refreshFavoritesCard();

						return true;
					}
				});

			card_favorites.addView(row);

			if (i < favorites.size() - 1) {

				View divider = new View(this);

				divider.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density)
				));

				divider.setBackgroundColor(getResources().getColor(R.color.stroke));

				card_favorites.addView(divider);
			}
		}
	}

	// A Tool Row key always starts with "btn_" (see SHORTCUT_KEYS); a
	// report favoriteKey never does, so this is enough to route the
	// unpin to whichever store actually holds the key.
	private boolean isToolRowKey(String key) {
		return key.startsWith("btn_");
	}

	private void unfavorite(String key) {

		if (isToolRowKey(key)) {
			DashboardFavorites.toggleFavorite(this, key);
		} else {
			ReportFavorites.toggleFavorite(this, key);
		}
	}

	// Reopens whatever Activity/extras the matching Tool Row's own
	// onClick above launches - kept as one switch here instead of
	// threading a Runnable/Intent through DashboardFavorites, since
	// only this Activity knows every button's target.
	private void openShortcut(String key) {

		Intent intent;

		switch (key) {

			case "btn_parties":
				intent = new Intent(this, Partiesactivity.class);
				break;

			case "btn_items":
				intent = new Intent(this, Itemsactivity.class);
				break;

			case "btn_transactions_purchase":
				intent = new Intent(this, Transactionactivity.class);
				intent.putExtra("transaction_type", 0);
				break;

			case "btn_transactions_sale":
				intent = new Intent(this, Transactionactivity.class);
				intent.putExtra("transaction_type", 1);
				break;

			case "btn_payments":
				intent = new Intent(this, Paymentactivity.class);
				break;

			case "btn_expenses":
				intent = new Intent(this, Expensesactivity.class);
				break;

			case "btn_reports":
				intent = new Intent(this, Reportsactivity.class);
				break;

			case "btn_import":
				intent = new Intent(this, Importexcelactivity.class);
				break;

			case "btn_generate_entries":
				intent = new Intent(this, GenerateEntriesActivity.class);
				break;

			case "btn_cash":
				intent = new Intent(this, CashActivity.class);
				break;

			case "btn_wanted_items":
				intent = new Intent(this, WantedItemsActivity.class);
				break;

			case "btn_recurring_expenses":
				intent = new Intent(this, RecurringExpensesActivity.class);
				break;

			case "btn_drafts":
				intent = new Intent(this, DraftsActivity.class);
				break;

			case "btn_cost_items":
				intent = new Intent(this, CostItemsActivity.class);
				break;

			case "btn_display_shoes":
				intent = new Intent(this, DisplayShoesActivity.class);
				break;

			case "btn_sample_shoes":
				intent = new Intent(this, SampleShoesActivity.class);
				break;

			case "btn_recently_deleted":
				intent = new Intent(this, RecentlyDeletedActivity.class);
				break;

			case "btn_reorder_list":
				intent = new Intent(this, ReorderListActivity.class);
				break;

			case "btn_bulk_item_update":
				intent = new Intent(this, BulkItemUpdateActivity.class);
				break;

			case "btn_loyalty_points":
				intent = new Intent(this, LoyaltyPointsActivity.class);
				break;

			case "btn_stock_take":
				intent = new Intent(this, StockTakeActivity.class);
				break;

			case "btn_quick_sale":
				intent = new Intent(this, QuickSaleActivity.class);
				break;

			case "btn_today_actions":
				intent = new Intent(this, TodayActionsActivity.class);
				break;

			case "btn_customize_dashboard":
				intent = new Intent(this, DashboardCustomizeActivity.class);
				break;

			case "btn_swipe_gesture_settings":
				intent = new Intent(this, SwipeGestureSettingsActivity.class);
				break;

			default:

				// Not a Tool Row key - check whether it's a report
				// pinned via ReportFavorites instead (see
				// refreshFavoritesCard()'s merged list).
				Class<? extends Activity> reportTarget =
					Reportsactivity.getTargetForFavoriteKey(key);

				if (reportTarget == null) {
					return;
				}

				intent = new Intent(this, reportTarget);
				break;
		}

		startActivity(intent);
	}

	// Computed off the main thread - a handful of SUM queries, but still
	// no reason to risk a hitch on the dashboard's own launch/resume path.
	// Also catches up any due recurring expenses first, so a newly
	// generated one is already reflected in these same totals.
	private void loadCashSummary() {

		new Thread(new Runnable() {
				@Override
				public void run() {

					final int generatedCount = db.generateDueRecurringExpenses();

					if (generatedCount > 0) {

						runOnUiThread(new Runnable() {
								@Override
								public void run() {

									android.widget.Toast.makeText(
										MainActivity.this,
										generatedCount + " recurring expense" +
										(generatedCount == 1 ? "" : "s") + " added",
										android.widget.Toast.LENGTH_SHORT
									).show();
								}
							});
					}

					final double cashBalance = db.getCashBalance();

					SimpleDateFormat sdf =
						new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

					String today = sdf.format(new java.util.Date());

					Calendar weekStart = Calendar.getInstance();
					weekStart.set(Calendar.DAY_OF_WEEK, weekStart.getFirstDayOfWeek());
					String weekFrom = sdf.format(weekStart.getTime());

					Calendar monthStart = Calendar.getInstance();
					monthStart.set(Calendar.DAY_OF_MONTH, 1);
					String monthFrom = sdf.format(monthStart.getTime());

					final double expenseToday = db.getExpenseTotalForRange(today, today);
					final double expenseWeek = db.getExpenseTotalForRange(weekFrom, today);
					final double expenseMonth = db.getExpenseTotalForRange(monthFrom, today);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								tv_cash_balance.setText(
									AmountFormat.format(cashBalance)
								);

								tv_cash_balance.setTextColor(
									getResources().getColor(
										cashBalance < 0 ? R.color.danger : R.color.text_primary
									)
								);

								tv_expense_today.setText(
									AmountFormat.format(expenseToday)
								);

								tv_expense_week.setText(
									AmountFormat.format(expenseWeek)
								);

								tv_expense_month.setText(
									AmountFormat.format(expenseMonth)
								);
							}
						});
				}
			}).start();
	}

	// Last N days' daily sales total, including a day with zero sales
	// - a quick "is the shop busy lately" glance, not another profit
	// report (see DatabaseHelper.getDailySalesTrend()).
	private void loadSalesTrend() {

		final long myGeneration = ++trendLoadGeneration;
		final int days = selectedTrendHorizonDays;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> trend = db.getDailySalesTrend(days);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != trendLoadGeneration || isFinishing()) {
									return;
								}

								applySalesTrend(trend);
							}
						});
				}
			}).start();
	}

	private void applySalesTrend(ArrayList<HashMap<String, Object>> trend) {

		SimpleDateFormat dayFormat = new SimpleDateFormat("d", Locale.getDefault());
		SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

		int salesColor = getResources().getColor(R.color.mod_sales);

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();

		for (HashMap<String, Object> row : trend) {

			String date = (String) row.get("date");
			double total = (Double) row.get("total");

			String label = date;

			try {
				label = dayFormat.format(isoFormat.parse(date));
			} catch (Exception e) {
			}

			chartEntries.add(new SimpleBarChartView.Entry(label, total, salesColor));
		}

		chart_sales_trend.setEntries(chartEntries);
	}

	// This period's sales so far vs the same elapsed window of the prior
	// period, shown inside the Sales Trend card - spinner_trend_compare_mode
	// picks Today vs Last Week (the original default; a week-ago weekday,
	// not yesterday, is the fairer baseline), This Week vs Last Week, or
	// This Month vs Last Month (see DatabaseHelper.getSalesTrendComparison()).
	private void loadTodayVsLastWeek() {

		final long myGeneration = ++todayVsLastWeekLoadGeneration;
		final int compareMode = selectedTrendCompareMode;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> result = db.getSalesTrendComparison(compareMode);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != todayVsLastWeekLoadGeneration || isFinishing()) {
									return;
								}

								applyTodayVsLastWeek(result);
							}
						});
				}
			}).start();
	}

	private void applyTodayVsLastWeek(HashMap<String, Object> result) {

		double currentTotal = (Double) result.get("current_total");
		double comparisonTotal = (Double) result.get("comparison_total");
		String currentLabel = (String) result.get("current_label");
		String comparisonLabel = (String) result.get("comparison_label");
		Double percentChange = (Double) result.get("percent_change");

		tv_today_vs_last_week.setText(
			currentLabel + ": " + AmountFormat.format(currentTotal) + "  ·  " +
			comparisonLabel + ": " + AmountFormat.format(comparisonTotal)
		);

		if (percentChange == null) {

			tv_today_vs_last_week_change.setText("No sales in " + comparisonLabel.toLowerCase(Locale.getDefault()) + " to compare");
			tv_today_vs_last_week_change.setTextColor(getResources().getColor(R.color.text_secondary));

		} else {

			String arrow = percentChange >= 0 ? "▲" : "▼";
			String sign = percentChange >= 0 ? "+" : "";

			tv_today_vs_last_week_change.setText(arrow + " " + sign + AmountFormat.formatPlain(percentChange) + "%");
			tv_today_vs_last_week_change.setTextColor(
				getResources().getColor(percentChange >= 0 ? R.color.success : R.color.danger)
			);
		}
	}

	// Current/best consecutive-day sales streak badge at the bottom of
	// the Sales Trend card (see DatabaseHelper.getSalesStreak()).
	private void loadSalesStreak() {

		final long myGeneration = ++salesStreakLoadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> result = db.getSalesStreak();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != salesStreakLoadGeneration || isFinishing()) {
									return;
								}

								applySalesStreak(result);
							}
						});
				}
			}).start();
	}

	private void applySalesStreak(HashMap<String, Object> result) {

		int currentStreak = (Integer) result.get("current_streak");
		int bestStreak = (Integer) result.get("best_streak");
		String streakStartDate = (String) result.get("current_streak_start_date");

		if (currentStreak <= 0) {

			tv_sales_streak.setText("No sales streak yet");
			tv_sales_streak_best.setText("");

		} else {

			// A long streak with no date range looks like a bug report
			// waiting to happen (row #35's "wrong/confusing 335 days") -
			// showing since-when turns a surprising raw count into
			// something the user can actually verify against their own
			// sales history (e.g. a bulk historical backfill via
			// GenerateEntriesActivity legitimately starts a long streak).
			String sinceText = "";

			if (streakStartDate != null) {

				try {

					java.util.Date parsed =
						new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(streakStartDate);

					sinceText = " (since " +
						new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(parsed) + ")";

				} catch (Exception e) {
				}
			}

			tv_sales_streak.setText(
				currentStreak + (currentStreak == 1 ? " day" : " days") + " sales streak" + sinceText
			);

			tv_sales_streak_best.setText(
				bestStreak > currentStreak ? ("Best: " + bestStreak + " days") : "Personal best!"
			);
		}
	}
}
