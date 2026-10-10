package com.mujeer.businesserp;

import android.content.Intent;
import android.app.Activity;
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

public class Expensesactivity extends Activity {

	private EditText et_search;
	private TextView tv_empty;

	private Button btn_add;
	private Button btn_filter;

	private ListView lv_expenses;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> expenseList;

	private ExpenseAdapter adapter;

	private final ExpenseAdapter.RowActionListener rowActionListener =
		new ExpenseAdapter.RowActionListener() {

			@Override
			public void onRowEdit(HashMap<String, Object> expense) {

				Intent intent = new Intent(
					Expensesactivity.this,
					Expenseeditactivity.class
				);

				intent.putExtra(
					"expense_id",
					Integer.parseInt(expense.get("id").toString())
				);

				startActivity(intent);
			}

			@Override
			public void onRowDelete(final HashMap<String, Object> expense) {

				final int expenseId = Integer.parseInt(expense.get("id").toString());

				new android.app.AlertDialog.Builder(Expensesactivity.this)
					.setTitle("Delete Expense")
					.setMessage("Are you sure you want to delete this expense?")
					.setPositiveButton(
						"Delete",
						new android.content.DialogInterface.OnClickListener() {

							@Override
							public void onClick(android.content.DialogInterface dialog, int which) {

								if (db.deleteExpense(expenseId)) {

									Toast.makeText(
										Expensesactivity.this,
										"Expense deleted successfully.",
										Toast.LENGTH_SHORT
									).show();

									loadExpenses();

								} else {

									Toast.makeText(
										Expensesactivity.this,
										"Failed to delete expense.",
										Toast.LENGTH_SHORT
									).show();
								}
							}
						}
					)
					.setNegativeButton("Cancel", null)
					.show();
			}
		};

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

		adapter.setRowActionListener(rowActionListener);

		// Set only when launched from DayCloseReportActivity's Expenses
		// card - pins the list to that one day, same mechanism as the
		// Filter Expenses dialog's own From/To fields.
		String filterDate = getIntent().getStringExtra("date");

		if (filterDate != null) {

			adapter.filter(
				et_search.getText().toString(), filterDate, filterDate
			);

		} else {

			adapter.filter(
				et_search.getText().toString()
			);
		}
	}

}
