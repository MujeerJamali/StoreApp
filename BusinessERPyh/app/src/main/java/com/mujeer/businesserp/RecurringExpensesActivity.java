package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class RecurringExpensesActivity extends Activity {

	private TextView tv_empty;
	private Button btn_add;
	private ListView lv_recurring_expenses;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> ruleList;
	private RecurringExpenseAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.recurring_expenses_activity);

		setTitle("Recurring Expenses");

		tv_empty = findViewById(R.id.tv_empty);
		btn_add = findViewById(R.id.btn_add);
		lv_recurring_expenses = findViewById(R.id.lv_recurring_expenses);

		db = new DatabaseHelper(this);

		ruleList = new ArrayList<HashMap<String, Object>>();

		btn_add.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						RecurringExpensesActivity.this, RecurringExpenseEditActivity.class
					);

					intent.putExtra("recurring_expense_id", 0);

					startActivity(intent);
				}
			});

		lv_recurring_expenses.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(
					AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row =
						(HashMap<String, Object>) adapter.getItem(position);

					Intent intent = new Intent(
						RecurringExpensesActivity.this, RecurringExpenseEditActivity.class
					);

					intent.putExtra("recurring_expense_id", (Integer) row.get("id"));

					startActivity(intent);
				}
			});
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadRules();
	}

	private void loadRules() {

		ruleList.clear();
		ruleList.addAll(db.getRecurringExpenses());

		adapter = new RecurringExpenseAdapter(this, ruleList);

		lv_recurring_expenses.setAdapter(adapter);
		lv_recurring_expenses.setEmptyView(tv_empty);
	}
}
