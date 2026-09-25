package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
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

	Button btn_quick_add;

	TextView tv_cash_balance;
	TextView tv_expense_today;
	TextView tv_expense_week;
	TextView tv_expense_month;

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

		btn_quick_add = findViewById(R.id.btn_quick_add);

		tv_cash_balance = findViewById(R.id.tv_cash_balance);
		tv_expense_today = findViewById(R.id.tv_expense_today);
		tv_expense_week = findViewById(R.id.tv_expense_week);
		tv_expense_month = findViewById(R.id.tv_expense_month);

		db = new DatabaseHelper(this);


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
	}

	// Computed off the main thread - a handful of SUM queries, but still
	// no reason to risk a hitch on the dashboard's own launch/resume path.
	private void loadCashSummary() {

		new Thread(new Runnable() {
				@Override
				public void run() {

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
}
