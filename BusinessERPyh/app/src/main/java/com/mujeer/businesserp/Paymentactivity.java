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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class Paymentactivity extends Activity {

	private EditText et_search;
	private TextView tv_empty;

	private Button btn_add;
	private Button btn_filter;

	private ListView lv_payments;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> paymentList;

	private PaymentAdapter adapter;

	// Shared across every PaymentAdapter instance this activity creates
	// (initial load, type filter, date filter) so swipe actions keep
	// working no matter which one is currently attached.
	private final PaymentAdapter.RowActionListener rowActionListener =
		new PaymentAdapter.RowActionListener() {

			@Override
			public void onRowEdit(HashMap<String, Object> payment) {

				Intent intent = new Intent(
					Paymentactivity.this,
					Paymenteditactivity.class
				);

				intent.putExtra(
					"payment_id",
					(Integer) payment.get("id")
				);

				startActivity(intent);
			}

			@Override
			public void onRowDelete(final HashMap<String, Object> payment) {

				final int paymentId = (Integer) payment.get("id");

				new android.app.AlertDialog.Builder(Paymentactivity.this)
					.setTitle("Delete Payment")
					.setMessage("Are you sure you want to delete this payment?")
					.setPositiveButton(
						"Delete",
						new android.content.DialogInterface.OnClickListener() {

							@Override
							public void onClick(android.content.DialogInterface dialog, int which) {

								if (db.deletePayment(paymentId)) {

									Toast.makeText(
										Paymentactivity.this,
										"Payment deleted successfully.",
										Toast.LENGTH_SHORT
									).show();

									loadPayments();

								} else {

									Toast.makeText(
										Paymentactivity.this,
										"Failed to delete payment.",
										Toast.LENGTH_SHORT
									).show();
								}
							}
						}
					)
					.setNegativeButton("Cancel", null)
					.show();
			}

			// Plain tap on the row (open the payment) - see
			// SwipeRevealLayout.onTouchEvent()'s own comment for why this
			// can no longer be left to lv_payments.setOnItemClickListener
			// alone now that swipe is enabled on every row here.
			@Override
			public void onRowTap(HashMap<String, Object> payment) {

				Intent intent = new Intent(
					Paymentactivity.this,
					Paymentviewactivity.class
				);

				intent.putExtra("payment_id", (Integer) payment.get("id"));

				startActivity(intent);
			}
		};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.paymentactivity);

		setTitle("Payments");

		et_search = findViewById(R.id.et_search);
		tv_empty = findViewById(R.id.tv_empty);

		btn_add = findViewById(R.id.btn_add);
		btn_filter = findViewById(R.id.btn_filter);

		lv_payments = findViewById(R.id.lv_payments);

		db = new DatabaseHelper(this);

		paymentList = new ArrayList<HashMap<String, Object>>();

		btn_add.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Paymentactivity.this,
						Paymenteditactivity.class
					);

					intent.putExtra(
						"payment_id",
						0
					);

					startActivity(intent);
				}
			}
		);

		btn_filter.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					final String[] options = {

						"All Payments",

						"Payment In",

						"Payment Out"

					};

					new android.app.AlertDialog.Builder(
						Paymentactivity.this
					)

						.setTitle(
						"Filter Payments"
					)

						.setItems(
						options,
						new android.content.DialogInterface.OnClickListener() {

							@Override
							public void onClick(
								android.content.DialogInterface dialog,
								int which) {

								if (which == 0) {

									loadPayments();

								} else if (which == 1) {

									paymentList.clear();

									paymentList.addAll(
										db.getPaymentsByType(
											DatabaseHelper.PAYMENT_IN
										)
									);

									adapter = new PaymentAdapter(
										Paymentactivity.this,
										paymentList
									);

									lv_payments.setAdapter(adapter);

								} else {

									paymentList.clear();

									paymentList.addAll(
										db.getPaymentsByType(
											DatabaseHelper.PAYMENT_OUT
										)
									);

									adapter = new PaymentAdapter(
										Paymentactivity.this,
										paymentList
									);

									lv_payments.setAdapter(adapter);
								}

								if (adapter != null) {

									adapter.setSwipeEnabled(
										SwipeGestureSettings.isEnabled(Paymentactivity.this)
									);

									adapter.setRowActionListener(rowActionListener);

									adapter.filter(
										et_search.getText().toString()
									);
								}
							}
						}
					)

						.show();
				}
			}
		);

		lv_payments.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					Intent intent = new Intent(
						Paymentactivity.this,
						Paymentviewactivity.class
					);

					intent.putExtra(
						"payment_id",
						Integer.parseInt(
							paymentList
							.get(position)
							.get("id")
							.toString()
						)
					);

					startActivity(intent);
				}
			}
		);

		// Edit/Delete are now a swipe-left gesture on the row itself
		// (see SwipeRevealLayout/PaymentAdapter) rather than long-press.

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

					if (adapter != null) {

						adapter.filter(
							s.toString()
						);
					}
				}
			}
		);
	}

	@Override
	protected void onResume() {
		super.onResume();

		loadPayments();
	}

	private void loadPayments() {

		paymentList.clear();

		// Set only when launched from DayCloseReportActivity's Payments
		// In/Out cards - pins the list to that one type and day.
		int filterType = getIntent().getIntExtra("payment_type", -1);
		String filterDate = getIntent().getStringExtra("date");

		if (filterType != -1 && filterDate != null) {

			paymentList.addAll(
				db.getPaymentsByType(filterType, filterDate, filterDate)
			);

		} else if (filterDate != null) {

			paymentList.addAll(
				db.getPayments(filterDate, filterDate)
			);

		} else if (filterType != -1) {

			paymentList.addAll(
				db.getPaymentsByType(filterType)
			);

		} else {

			paymentList.addAll(
				db.getPayments()
			);
		}

		adapter = new PaymentAdapter(
			this,
			paymentList
		);

		lv_payments.setAdapter(adapter);
		lv_payments.setEmptyView(tv_empty);

		adapter.setSwipeEnabled(SwipeGestureSettings.isEnabled(this));
		adapter.setRowActionListener(rowActionListener);

		adapter.filter(
			et_search.getText().toString()
		);
	}
}
