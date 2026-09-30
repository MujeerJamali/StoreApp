package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class Expensesactivity extends Activity {

	private EditText et_search;
	private TextView tv_empty;

	private Button btn_add;
	private Button btn_filter;

	private ListView lv_expenses;

	private LinearLayout row_top_expenses_1;
	private LinearLayout row_top_expenses_2;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> expenseList;

	private ExpenseAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.expensesactivity);

		setTitle("Expenses");

		et_search = findViewById(R.id.et_search);
		tv_empty = findViewById(R.id.tv_empty);

		btn_add = findViewById(R.id.btn_add);
		btn_filter = findViewById(R.id.btn_filter);

		lv_expenses = findViewById(R.id.lv_expenses);

		row_top_expenses_1 = findViewById(R.id.row_top_expenses_1);
		row_top_expenses_2 = findViewById(R.id.row_top_expenses_2);

		db = new DatabaseHelper(this);

		expenseList =
			new ArrayList<HashMap<String, Object>>();

		btn_add.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Expensesactivity.this,
						Expenseeditactivity.class
					);

					intent.putExtra(
						"expense_id",
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

					final EditText et_from =
						new EditText(
						Expensesactivity.this
					);

					et_from.setHint(
						"From Date (yyyy-MM-dd)"
					);

					final EditText et_to =
						new EditText(
						Expensesactivity.this
					);

					et_to.setHint(
						"To Date (yyyy-MM-dd)"
					);

					android.widget.LinearLayout layout =
						new android.widget.LinearLayout(
						Expensesactivity.this
					);

					layout.setOrientation(
						android.widget.LinearLayout.VERTICAL
					);

					layout.setPadding(
						40,
						20,
						40,
						20
					);

					layout.addView(et_from);
					layout.addView(et_to);

					new android.app.AlertDialog.Builder(
						Expensesactivity.this
					)

						.setTitle(
						"Filter Expenses"
					)

						.setView(layout)

						.setPositiveButton(
						"Apply",
						new android.content.DialogInterface.OnClickListener() {

							@Override
							public void onClick(
								android.content.DialogInterface dialog,
								int which) {

								adapter.filter(

									et_search
									.getText()
									.toString(),

									et_from
									.getText()
									.toString()
									.trim(),

									et_to
									.getText()
									.toString()
									.trim()
								);
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

		lv_expenses.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					Intent intent = new Intent(
						Expensesactivity.this,
						Expenseviewactivity.class
					);

					intent.putExtra(
						"expense_id",
						Integer.parseInt(
							((HashMap<String, Object>)
							adapter.getItem(position))
							.get("id")
							.toString()
						)
					);

					startActivity(intent);
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

		loadExpenses();
		loadTopExpenseBoxes();
	}

	private void loadExpenses() {

		expenseList.clear();

		expenseList.addAll(
			db.getExpenses()
		);

		adapter = new ExpenseAdapter(
			this,
			expenseList
		);

		lv_expenses.setAdapter(adapter);

		lv_expenses.setEmptyView(tv_empty);

		adapter.filter(
			et_search.getText().toString()
		);
	}

	// =====================
	// Six quick-add boxes for the most frequently logged expense items -
	// tapping one confirms via a dialog, then adds a new Expense dated
	// today with that item's own last-used amount, fully paid, no
	// navigation to the full editor. Boxes are rebuilt every onResume()
	// so a newly-added expense can reshuffle the ranking.
	// =====================
	private void loadTopExpenseBoxes() {

		row_top_expenses_1.removeAllViews();
		row_top_expenses_2.removeAllViews();

		ArrayList<HashMap<String, Object>> topItems = db.getTopExpenseItems(6);

		LayoutInflater inflater = LayoutInflater.from(this);

		for (int i = 0; i < topItems.size(); i++) {

			final HashMap<String, Object> entry = topItems.get(i);
			final String item = String.valueOf(entry.get("item"));
			final double amount = (Double) entry.get("amount");

			LinearLayout targetRow = i < 3 ? row_top_expenses_1 : row_top_expenses_2;

			View box = inflater.inflate(R.layout.top_expense_box, targetRow, false);

			TextView label = box.findViewById(R.id.tv_top_expense_label);
			label.setText(item + " - " + AmountFormat.format(amount));

			box.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						confirmAddTopExpense(item, amount);
					}
				});

			targetRow.addView(box);
		}
	}

	private void confirmAddTopExpense(final String item, final double amount) {

		new AlertDialog.Builder(this)
			.setTitle("Add Expense")
			.setMessage(item + " - " + AmountFormat.format(amount) + "?")
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						if (amount > db.getCashBalance()) {

							Toast.makeText(
								Expensesactivity.this,
								"This would take cash balance below 0 - reduce the amount or add cash first",
								Toast.LENGTH_LONG
							).show();

							return;
						}

						String today = new SimpleDateFormat(
							"yyyy-MM-dd", Locale.getDefault()
						).format(new Date());

						String now = new SimpleDateFormat(
							"HH:mm", Locale.getDefault()
						).format(new Date());

						int cashExpensePartyId = (int) db.getOrCreatePartyId("Cash Expenses");

						db.insertExpense(item, today, now, amount, amount, null, cashExpensePartyId);

						Toast.makeText(
							Expensesactivity.this, "Expense added", Toast.LENGTH_SHORT
						).show();

						loadExpenses();
						loadTopExpenseBoxes();
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}
}
