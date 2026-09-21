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
import java.util.Random;

public class Transactionactivity extends Activity {

	private static final int TYPE_PURCHASE = 0;
	private static final int TYPE_SALE = 1;

	private int transactionType = TYPE_PURCHASE;

	private EditText et_search;
	private TextView tv_empty;
	private TextView tv_page_title;

	private Button btn_add;
	private Button btn_generate;
	private Button btn_delete_all;
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
		btn_generate = findViewById(R.id.btn_generate);
		btn_delete_all = findViewById(R.id.btn_delete_all);
		btn_filter = findViewById(R.id.btn_filter);

		lv_transactions = findViewById(R.id.lv_transactions);

		if (transactionType == TYPE_PURCHASE) {

			setTitle("Purchases");
			tv_page_title.setText("Purchases");

			btn_add.setText("+");
			btn_generate.setText("Generate");
			btn_delete_all.setText("Delete All");

		} else {

			setTitle("Sales");
			tv_page_title.setText("Sales");

			btn_add.setText("+");
			btn_generate.setText("Generate");
			btn_delete_all.setText("Delete All");
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

		btn_generate.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					if (transactionType == TYPE_PURCHASE) {

						generate100Purchases();
						refreshList();

					} else {

						generate100Sales();
						refreshList();
					}
				}
			}
		);

		btn_delete_all.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					if (transactionType == TYPE_PURCHASE) {

						new android.app.AlertDialog.Builder(
							Transactionactivity.this
						)
							.setTitle("Delete All Purchases")
							.setMessage(
							"Are you sure you want to delete ALL purchases?\n\nThis action cannot be undone."
						)
							.setPositiveButton(
							"Yes",
							new android.content.DialogInterface.OnClickListener() {

								@Override
								public void onClick(
									android.content.DialogInterface dialog,
									int which) {

									db.deleteAllPurchases();

									refreshList();

									android.widget.Toast.makeText(
										Transactionactivity.this,
										"All purchases deleted.",
										android.widget.Toast.LENGTH_SHORT
									).show();
								}
							}
						)
							.setNegativeButton("No", null)
							.show();

					} else {

						new android.app.AlertDialog.Builder(
							Transactionactivity.this
						)
							.setTitle("Delete All Sales")
							.setMessage("Are you sure you want to delete all sales?")
							.setPositiveButton(
							"Delete All",
							new android.content.DialogInterface.OnClickListener() {

								@Override
								public void onClick(
									android.content.DialogInterface dialog,
									int which) {

									db.deleteAllSales();

									refreshList();

									android.widget.Toast.makeText(
										Transactionactivity.this,
										"All sales deleted",
										android.widget.Toast.LENGTH_SHORT
									).show();
								}
							}
						)
							.setNegativeButton("Cancel", null)
							.show();
					}
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
	
	
	private void generate100Purchases() {

		ArrayList<HashMap<String, Object>> parties =
			db.getParties();

		ArrayList<HashMap<String, Object>> items =
			db.getItemsForSpinner();

		if (parties.isEmpty() || items.isEmpty()) {

			android.widget.Toast.makeText(
				this,
				"Please create at least one party and one item first.",
				android.widget.Toast.LENGTH_LONG
			).show();

			return;
		}

		generateRandomPurchases();

		android.widget.Toast.makeText(
			this,
			"100 purchases generated.",
			android.widget.Toast.LENGTH_LONG
		).show();
	}

	private void generate100Sales() {

		ArrayList<HashMap<String, Object>> parties =
			db.getParties();

		ArrayList<HashMap<String, Object>> items =
			db.getItemsForSpinner();

		if (parties.isEmpty() || items.isEmpty()) {

			android.widget.Toast.makeText(
				this,
				"Please add at least one party and one item first.",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		generateRandomSales();

		android.widget.Toast.makeText(
			this,
			"100 Sales Generated",
			android.widget.Toast.LENGTH_SHORT
		).show();
	}
	
	private void generateRandomPurchases() {

		Random random = new Random();

		for (int i = 0; i < 100; i++) {

			HashMap<String, Object> party =
				db.getRandomParty();

			int partyId =
				(Integer) party.get("id");

			java.util.Calendar calendar =
				java.util.Calendar.getInstance();

			calendar.add(
				java.util.Calendar.DAY_OF_YEAR,
				-random.nextInt(365)
			);

			String date = String.format(
				"%1$tY-%1$tm-%1$td",
				calendar
			);

			String time = String.format(
				"%02d:%02d",
				random.nextInt(24),
				random.nextInt(60)
			);

			long purchaseId = db.insertPurchase(
				partyId,
				date,
				time,
				"INV-" + (1000 + random.nextInt(9000)),
				0,
				0,
				""
			);

			int itemCount = 1 + random.nextInt(5);

			double grandTotal = 0;

			for (int j = 0; j < itemCount; j++) {

				HashMap<String, Object> item =
					db.getRandomItem();

				int itemId =
					(Integer) item.get("id");

				double purchasePrice =
					(Double) item.get("purchase_price");

				double quantity =
					1 + random.nextInt(20);

				double total =
					quantity * purchasePrice;

				grandTotal += total;

				db.insertPurchaseItem(
					purchaseId,
					itemId,
					quantity,
					purchasePrice,
					total
				);
			}

			double amountPaid;

			if (random.nextBoolean()) {

				amountPaid = grandTotal;

			} else {

				amountPaid =
					grandTotal * random.nextDouble();
			}

			db.updatePurchaseTotals(
				purchaseId,
				grandTotal,
				amountPaid
			);
		}
	}
	
	
	private void generateRandomSales() {

		Random random = new Random();

		for (int i = 0; i < 100; i++) {

			HashMap<String, Object> saleData =
				new HashMap<String, Object>();

			saleData.put(
				"invoice_no",
				db.getNextSaleInvoiceNo()
			);

			java.util.Calendar calendar =
				java.util.Calendar.getInstance();

			calendar.add(
				java.util.Calendar.DAY_OF_YEAR,
				-random.nextInt(365)
			);

			saleData.put(
				"date",
				new java.text.SimpleDateFormat(
					"yyyy-MM-dd",
					java.util.Locale.getDefault()
				).format(calendar.getTime())
			);

			saleData.put(
				"time",
				new java.text.SimpleDateFormat(
					"HH:mm",
					java.util.Locale.getDefault()
				).format(new java.util.Date())
			);

			ArrayList<HashMap<String, Object>> parties =
				db.getParties();

			ArrayList<HashMap<String, Object>> items =
				db.getItemsForSpinner();

			saleData.put(
				"party_id",
				parties.get(
					random.nextInt(parties.size())
				).get("id")
			);

			double subtotal = 0;

			int itemCount = random.nextInt(5) + 1;

			ArrayList<HashMap<String, Object>> selectedItems =
				new ArrayList<HashMap<String, Object>>();

			for (int j = 0; j < itemCount; j++) {

				HashMap<String, Object> item =
					items.get(random.nextInt(items.size()));

				double qty = random.nextInt(5) + 1;

				double rate = Double.parseDouble(
					item.get("purchase_price").toString()
				);

				rate += random.nextInt(301);

				double amount = qty * rate;

				subtotal += amount;

				HashMap<String, Object> saleItem =
					new HashMap<String, Object>();

				saleItem.put("item_id", item.get("id"));
				saleItem.put("qty", qty);
				saleItem.put("rate", rate);
				saleItem.put("amount", amount);

				selectedItems.add(saleItem);
			}

			saleData.put("subtotal", subtotal);
			saleData.put("discount", 0);
			saleData.put("other_charges", 0);
			saleData.put("grand_total", subtotal);
			saleData.put("paid_amount", subtotal);
			saleData.put("balance", 0);
			saleData.put("notes", "");

			long saleId = db.insertSale(saleData);

			if (saleId != -1) {

				for (HashMap<String, Object> saleItem : selectedItems) {

					HashMap<String, Object> itemData =
						new HashMap<String, Object>();

					itemData.put("sale_id", saleId);
					itemData.put("item_id", saleItem.get("item_id"));
					itemData.put("qty", saleItem.get("qty"));
					itemData.put("rate", saleItem.get("rate"));
					itemData.put("amount", saleItem.get("amount"));

					db.insertSaleItem(itemData);
				}
			}
		}
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
