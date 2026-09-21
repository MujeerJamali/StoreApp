package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class Paymentviewactivity extends Activity {

	private TextView tv_code;
	private TextView tv_type;
	private TextView tv_party;
	private TextView tv_date;
	private TextView tv_time;
	private TextView tv_amount;
	private TextView tv_notes;

	private Button btn_edit;
	private Button btn_delete;

	private DatabaseHelper db;

	private int paymentId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.payment_view_activity);

		setTitle("Payment");

		tv_code = findViewById(R.id.tv_code);
		tv_type = findViewById(R.id.tv_type);
		tv_party = findViewById(R.id.tv_party);
		tv_date = findViewById(R.id.tv_date);
		tv_time = findViewById(R.id.tv_time);
		tv_amount = findViewById(R.id.tv_amount);
		tv_notes = findViewById(R.id.tv_notes);

		btn_edit = findViewById(R.id.btn_edit);
		btn_delete = findViewById(R.id.btn_delete);

		db = new DatabaseHelper(this);

		paymentId = getIntent().getIntExtra(
			"payment_id",
			0
		);

		HashMap<String, Object> payment =
			db.getPaymentById(paymentId);

		tv_code.setText(
			payment.get("code").toString()
		);

		int type = Integer.parseInt(
			payment.get("type").toString()
		);

		if (type == DatabaseHelper.PAYMENT_IN) {

			tv_type.setText(
				"Type: Payment In"
			);

		} else {

			tv_type.setText(
				"Type: Payment Out"
			);
		}

		tv_party.setText(
			"Party: " +
			payment.get("party_name").toString()
		);

		tv_date.setText(
			"Date: " +
			payment.get("date").toString()
		);

		tv_time.setText(
			"Time: " +
			payment.get("time").toString()
		);

		tv_amount.setText(
			"Amount: Rs. " +
			payment.get("amount").toString()
		);

		String notes = "";

		if (payment.get("notes") != null) {

			notes = payment.get("notes").toString();
		}

		tv_notes.setText(
			"Notes: " + notes
		);

		btn_edit.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Paymentviewactivity.this,
						Paymenteditactivity.class
					);

					intent.putExtra(
						"payment_id",
						paymentId
					);

					startActivity(intent);
				}
			}
		);

		btn_delete.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					new android.app.AlertDialog.Builder(
						Paymentviewactivity.this
						)

							.setTitle(
							"Delete Payment"
						)

						.setMessage(
						"Are you sure you want to delete this payment?"
					)

						.setPositiveButton(
							"Delete",
							new android.content.DialogInterface.OnClickListener() {

						@Override
					public void onClick(
				android.content.DialogInterface dialog,
			int which) {

	if (db.deletePayment(paymentId)) {

							Toast.makeText(
								Paymentviewactivity.this,
								"Payment deleted successfully.",
								Toast.LENGTH_SHORT
							).show();

							finish();

						} else {

							Toast.makeText(
								Paymentviewactivity.this,
								"Failed to delete payment.",
								Toast.LENGTH_SHORT
							).show();
						}
					}
				}
			)

			.setNegativeButton(
				"Cancel",
				null
			)

			.show();
		}
	}
);
	}
}
