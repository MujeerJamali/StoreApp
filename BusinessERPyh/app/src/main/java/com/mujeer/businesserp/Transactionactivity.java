package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class Transactionactivity extends Activity {

	private static final int TYPE_PURCHASE = 0;
	private static final int TYPE_SALE = 1;

	private int transactionType = TYPE_PURCHASE;

	private EditText et_search;
	private TextView tv_empty;
	private TextView tv_page_title;

	private Button btn_add;
	private Button btn_filter;

	private ListView lv_transactions;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> transactionList;

	private TransactionAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.transactionactivity);

		transactionType = getIntent().getIntExtra(
			"transaction_type",
			TYPE_PURCHASE
		);

		et_search = findViewById(R.id.et_search);
		tv_empty = findViewById(R.id.tv_empty);
		tv_page_title = findViewById(R.id.tv_page_title);

		btn_add = findViewById(R.id.btn_add);
		btn_filter = findViewById(R.id.btn_filter);

		lv_transactions = findViewById(R.id.lv_transactions);

		if (transactionType == TYPE_PURCHASE) {

			setTitle("Purchases");
			tv_page_title.setText("Purchases");

			btn_add.setText("+");

		} else {

			setTitle("Sales");
			tv_page_title.setText("Sales");

			btn_add.setText("+");
		}

		db = new DatabaseHelper(this);

		transactionList = new ArrayList<HashMap<String, Object>>();

		
		btn_filter.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					showFilterDialog();
				}
			}
		);
		
		btn_add.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Transactionactivity.this,
						Transactioneditactivity.class
					);

					intent.putExtra(
						"transaction_type",
						transactionType
					);

					intent.putExtra(
						"is_edit",
						false
					);

					startActivity(intent);
				}
			}
		);

		lv_transactions.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					HashMap<String, Object> transaction =
						(HashMap<String, Object>) adapter.getItem(position);

					Intent intent = new Intent(
						Transactionactivity.this,
						Transactionviewactivity.class
					);

					intent.putExtra(
						"transaction_type",
						transactionType
					);

					intent.putExtra(
						"transaction_id",
						Integer.parseInt(
							transaction.get("id").toString()
						)
					);

					startActivity(intent);
				}
			}
		);

		lv_transactions.setOnItemLongClickListener(
			new AdapterView.OnItemLongClickListener() {

				@Override
				public boolean onItemLongClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					final HashMap<String, Object> transaction =
						(HashMap<String, Object>) adapter.getItem(position);

					if (transactionType == TYPE_PURCHASE) {

						if (db.deletePurchase(
								Integer.parseInt(
									transaction.get("id").toString()
								)
							)) {

							loadTransactions();
						}

					} else {

						String saleId =
							transaction.get("id").toString();

						db.deleteSaleItems(saleId);
						db.deleteSale(saleId);

						loadTransactions();
					}

					return true;
				}
			}
		);

		et_search.addTextChangedListener(
			new TextWatcher() {

				@Override
				public void beforeTextChanged(
					CharSequence s,
					int start,
					int count,
					int after) {
				}

				@Override
				public void onTextChanged(
					CharSequence s,
					int start,
					int before,
					int count) {
				}

				@Override
				public void afterTextChanged(
					Editable s) {

					adapter.filter(
						s.toString()
					);
				}
			}
		);
	}

	@Override
	protected void onResume() {
		super.onResume();

		loadTransactions();
	}

	private void loadTransactions() {

		transactionList.clear();

		if (transactionType == TYPE_PURCHASE) {

			transactionList.addAll(
				db.getPurchases()
			);

		} else {

			transactionList.addAll(
				db.getSales()
			);
		}

		adapter = new TransactionAdapter(
			this,
			transactionList
		);

		lv_transactions.setAdapter(adapter);
		lv_transactions.setEmptyView(tv_empty);
	}

	private void refreshList() {
		loadTransactions();
	}

	private boolean isPurchase() {
		return transactionType == TYPE_PURCHASE;
	}

	private boolean isSale() {
		return transactionType == TYPE_SALE;
	}


	private void showFilterDialog() {

		final String[] filters = {

			"All",
			"Today",
			"This Week",
			"This Month",
			"This Year"

		};

		new android.app.AlertDialog.Builder(this)

			.setTitle(
			isPurchase() ?
			"Filter Purchases" :
			"Filter Sales"
		)

			.setItems(
			filters,
			new android.content.DialogInterface.OnClickListener() {

				@Override
				public void onClick(
					android.content.DialogInterface dialog,
					int which) {

					switch (which) {

						case 0:

							btn_filter.setText("All");

							loadTransactions(
								null,
								null
							);

							break;

						case 1:

							btn_filter.setText("Today");

							java.text.SimpleDateFormat sdf =
								new java.text.SimpleDateFormat(
								"yyyy-MM-dd",
								java.util.Locale.getDefault()
							);

							String today =
								sdf.format(new java.util.Date());

							loadTransactions(
								today,
								today
							);

							break;

						case 2:

							btn_filter.setText("This Week");

							java.util.Calendar calendar =
								java.util.Calendar.getInstance();

							calendar.set(
								java.util.Calendar.DAY_OF_WEEK,
								calendar.getFirstDayOfWeek()
							);

							java.text.SimpleDateFormat sdfWeek =
								new java.text.SimpleDateFormat(
								"yyyy-MM-dd",
								java.util.Locale.getDefault()
							);

							String fromWeek =
								sdfWeek.format(calendar.getTime());

							calendar.add(
								java.util.Calendar.DAY_OF_YEAR,
								6
							);

							String toWeek =
								sdfWeek.format(calendar.getTime());

							loadTransactions(
								fromWeek,
								toWeek
							);

							break;

						case 3:

							btn_filter.setText("This Month");

							java.util.Calendar calendarMonth =
								java.util.Calendar.getInstance();

							calendarMonth.set(
								java.util.Calendar.DAY_OF_MONTH,
								1
							);

							java.text.SimpleDateFormat sdfMonth =
								new java.text.SimpleDateFormat(
								"yyyy-MM-dd",
								java.util.Locale.getDefault()
							);

							String fromMonth =
								sdfMonth.format(calendarMonth.getTime());

							calendarMonth.set(
								java.util.Calendar.DAY_OF_MONTH,
								calendarMonth.getActualMaximum(
									java.util.Calendar.DAY_OF_MONTH
								)
							);

							String toMonth =
								sdfMonth.format(calendarMonth.getTime());

							loadTransactions(
								fromMonth,
								toMonth
							);

							break;

						case 4:

							btn_filter.setText("This Year");

							java.util.Calendar calendarYear =
								java.util.Calendar.getInstance();

							calendarYear.set(
								java.util.Calendar.MONTH,
								java.util.Calendar.JANUARY
							);

							calendarYear.set(
								java.util.Calendar.DAY_OF_MONTH,
								1
							);

							java.text.SimpleDateFormat sdfYear =
								new java.text.SimpleDateFormat(
								"yyyy-MM-dd",
								java.util.Locale.getDefault()
							);

							String fromYear =
								sdfYear.format(calendarYear.getTime());

							calendarYear.set(
								java.util.Calendar.MONTH,
								java.util.Calendar.DECEMBER
							);

							calendarYear.set(
								java.util.Calendar.DAY_OF_MONTH,
								31
							);

							String toYear =
								sdfYear.format(calendarYear.getTime());

							loadTransactions(
								fromYear,
								toYear
							);

							break;
					}
				}
			}
		)

			.show();
	}
	
	private void loadTransactions(
		String fromDate,
		String toDate) {

		transactionList.clear();

		if (isPurchase()) {

			transactionList.addAll(
				db.getPurchases(
					fromDate,
					toDate
				)
			);

		} else {

			transactionList.addAll(
				db.getSales(
					fromDate,
					toDate
				)
			);
		}

		adapter = new TransactionAdapter(
			this,
			transactionList
		);

		lv_transactions.setAdapter(adapter);
		lv_transactions.setEmptyView(tv_empty);

		if (et_search != null) {

			adapter.filter(
				et_search.getText().toString()
			);
		}
	}
	
	

}
