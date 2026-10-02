package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

// List adapter for CreditDueReportActivity - each row is one unpaid/
// partial Sale due within the selected period, showing the party,
// invoice, due date (bold + red once it's actually overdue), and the
// amount still owed.
public class CreditDueAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	public CreditDueAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
		this.activity = activity;
		this.list = list;
	}

	@Override
	public int getCount() {
		return list.size();
	}

	@Override
	public Object getItem(int position) {
		return list.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if (convertView == null) {

			convertView = LayoutInflater.from(activity).inflate(
				R.layout.credit_due_row, parent, false
			);
		}

		TextView tv_party_name = convertView.findViewById(R.id.tv_credit_due_party_name);
		TextView tv_invoice = convertView.findViewById(R.id.tv_credit_due_invoice);
		TextView tv_due_date = convertView.findViewById(R.id.tv_credit_due_date);
		TextView tv_balance = convertView.findViewById(R.id.tv_credit_due_balance);

		HashMap<String, Object> row = list.get(position);

		tv_party_name.setText(
			row.get("party_name") == null ? "" : row.get("party_name").toString()
		);

		tv_invoice.setText(
			"Invoice " + (row.get("invoice_no") == null ? "" : row.get("invoice_no").toString())
		);

		String dueDate = row.get("due_date") == null ? "" : row.get("due_date").toString();

		boolean overdue = isBeforeToday(dueDate);

		tv_due_date.setText("Due " + dueDate + (overdue ? " (overdue)" : ""));

		tv_due_date.setTextColor(
			activity.getResources().getColor(overdue ? R.color.danger : R.color.text_secondary)
		);

		double balance = row.get("balance") == null ? 0 : (Double) row.get("balance");

		tv_balance.setText(AmountFormat.format(balance));

		return convertView;
	}

	private boolean isBeforeToday(String date) {

		if (date.isEmpty()) {
			return false;
		}

		String today = dateFormat.format(new java.util.Date());

		return date.compareTo(today) < 0;
	}
}
