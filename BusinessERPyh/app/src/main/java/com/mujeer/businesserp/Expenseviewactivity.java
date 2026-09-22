package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class Expenseviewactivity extends Activity {

	private TextView tv_code;
	private TextView tv_item;
	private TextView tv_party;
	private TextView tv_date;
	private TextView tv_time;
	private TextView tv_amount;
	private TextView tv_notes;

	private Button btn_edit;
	private Button btn_delete;

	private DatabaseHelper db;

	private int expenseId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.expense_view_activity);

		setTitle("Expense");

		tv_code = findViewById(R.id.tv_code);
		tv_item = findViewById(R.id.tv_item);
		tv_party = findViewById(R.id.tv_party);
		tv_date = findViewById(R.id.tv_date);
		tv_time = findViewById(R.id.tv_time);
		tv_amount = findViewById(R.id.tv_amount);
		tv_notes = findViewById(R.id.tv_notes);

		btn_edit = findViewById(R.id.btn_edit);
		btn_delete = findViewById(R.id.btn_delete);

		db = new DatabaseHelper(this);

		expenseId = getIntent().getIntExtra(
			"expense_id",
			0
		);

		loadExpense();

		btn_edit.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Expenseviewactivity.this,
						Expenseeditactivity.class
					);

					intent.putExtra(
						"expense_id",
						expenseId
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
						Expenseviewactivity.this
					)

						.setTitle(
						"Delete Expense"
					)

						.setMessage(
						"Are you sure you want to delete this expense?"
					)

						.setPositiveButton(
						"Delete",
						new android.content.DialogInterface.OnClickListener() {

							@Override
							public void onClick(
								android.content.DialogInterface dialog,
								int which) {

								if (db.deleteExpense(expenseId)) {

									Toast.makeText(
										Expenseviewactivity.this,
										"Expense deleted successfully.",
										Toast.LENGTH_SHORT
									).show();

									finish();

								} else {

									Toast.makeText(
										Expenseviewactivity.this,
										"Failed to delete expense.",
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

	@Override
	protected void onResume() {
		super.onResume();

		loadExpense();
	}

	private void loadExpense() {

		HashMap<String, Object> expense =
			db.getExpenseById(expenseId);

		tv_code.setText(
			expense.get("code").toString()
		);

		tv_item.setText(
			"Item: " +
			expense.get("item").toString()
		);

		if (expense.get("party_id") != null) {

			int partyId = (Integer) expense.get("party_id");
			String partyName = db.getPartyById(partyId);

			tv_party.setText("Party: " + partyName);
			tv_party.setVisibility(View.VISIBLE);

		} else {

			tv_party.setVisibility(View.GONE);
		}

		tv_date.setText(
			"Date: " +
			expense.get("date").toString()
		);

		tv_time.setText(
			"Time: " +
			expense.get("time").toString()
		);

		tv_amount.setText(
			"Amount: Rs. " +
			expense.get("amount").toString()
		);

		String notes = "";

		if (expense.get("notes") != null) {

			notes = expense.get("notes").toString();
		}

		tv_notes.setText(
			"Notes: " + notes
		);
	}
}
