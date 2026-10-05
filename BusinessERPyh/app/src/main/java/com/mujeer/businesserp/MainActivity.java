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

	Button btn_quick_add;

	TextView tv_cash_balance;
	TextView tv_expense_today;
	TextView tv_expense_week;
	TextView tv_expense_month;

	Spinner spinner_sales_trend_horizon;
	SimpleBarChartView chart_sales_trend;

	private static final int[] TREND_HORIZON_DAYS = {7, 30};
	private static final String[] TREND_HORIZON_LABELS = {"7 Days", "30 Days"};

	private int selectedTrendHorizonDays = TREND_HORIZON_DAYS[0];

	// A background result is only applied if it's still the most
	// recent request by the time it comes back.
	private long trendLoadGeneration = 0;

	TextView tv_favorites_label;
	LinearLayout card_favorites;

	// Every long-press-pinnable Tool Row on this screen (both the
	// Modules and Tools cards), keyed by its own button id's resource
	// name - the same key DashboardFavorites stores and
	// openShortcut(key) switches on.
	private static final String[] SHORTCUT_KEYS = {
		"btn_parties", "btn_items", "btn_transactions_purchase", "btn_transactions_sale",
		"btn_payments", "btn_expenses", "btn_reports",
		"btn_import", "btn_generate_entries", "btn_cash", "btn_wanted_items",
		"btn_recurring_expenses", "btn_drafts", "btn_cost_items",
		"btn_display_shoes", "btn_sample_shoes"
	};

	private static final String[] SHORTCUT_LABELS = {
		"Parties", "Items", "Purchases", "Sales",
		"Payments", "Expenses", "Reports",
		"Import", "Generate Entries", "Cash", "Wanted Items",
		"Recurring Expenses", "Drafts", "Cost Items",
		"Display Shoes", "Sample Shoes"
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

		btn_quick_add = findViewById(R.id.btn_quick_add);

		tv_cash_balance = findViewById(R.id.tv_cash_balance);
		tv_expense_today = findViewById(R.id.tv_expense_today);
		tv_expense_week = findViewById(R.id.tv_expense_week);
		tv_expense_month = findViewById(R.id.tv_expense_month);

		spinner_sales_trend_horizon = findViewById(R.id.spinner_sales_trend_horizon);
		chart_sales_trend = findViewById(R.id.chart_sales_trend);

		tv_favorites_label = findViewById(R.id.tv_favorites_label);
		card_favorites = findViewById(R.id.card_favorites);

		db = new DatabaseHelper(this);

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
		loadCashSummary();
		loadSalesTrend();
		refreshFavoritesCard();
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

		return key;
	}

	private void refreshFavoritesCard() {

		java.util.ArrayList<String> favorites = DashboardFavorites.getFavorites(this);

		card_favorites.removeAllViews();

		if (favorites.isEmpty()) {

			tv_favorites_label.setVisibility(View.GONE);
			card_favorites.setVisibility(View.GONE);

			return;
		}

		tv_favorites_label.setVisibility(View.VISIBLE);
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

						DashboardFavorites.toggleFavorite(MainActivity.this, key);

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

			default:
				return;
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
}
