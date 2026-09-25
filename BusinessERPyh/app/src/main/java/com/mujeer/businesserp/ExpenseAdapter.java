package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class ExpenseAdapter extends BaseAdapter {

	private Activity activity;

	private ArrayList<HashMap<String, Object>> originalList;
	private ArrayList<HashMap<String, Object>> filteredList;

	private TextView tv_code;
	private TextView tv_item;
	private TextView tv_date;
	private TextView tv_amount;

	public ExpenseAdapter(
		Activity activity,
		ArrayList<HashMap<String, Object>> list) {

		this.activity = activity;

		this.originalList = list;

		this.filteredList =
			new ArrayList<HashMap<String, Object>>(list);
	}

	@Override
	public int getCount() {

		return filteredList.size();
	}

	@Override
	public Object getItem(int position) {

		return filteredList.get(position);
	}

	@Override
	public long getItemId(int position) {

		return Integer.parseInt(
			filteredList.get(position)
			.get("id")
			.toString()
		);
	}

	@Override
	public View getView(
		int position,
		View convertView,
		ViewGroup parent) {

		if (convertView == null) {

			convertView = LayoutInflater.from(activity).inflate(
				R.layout.expense_adapter,
				parent,
				false
			);
		}

		tv_code = convertView.findViewById(R.id.tv_code);
		tv_item = convertView.findViewById(R.id.tv_item);
		tv_date = convertView.findViewById(R.id.tv_date);
		tv_amount = convertView.findViewById(R.id.tv_amount);

		HashMap<String, Object> expense =
			filteredList.get(position);

		tv_code.setText(
			expense.get("code").toString()
		);

		tv_item.setText(
			expense.get("item").toString()
		);

		tv_date.setText(
			RelativeDate.format(expense.get("date").toString())
		);

		tv_amount.setText(
			"Rs. " +
			expense.get("amount").toString()
		);

		return convertView;
	}

	public void filter(String query) {

		filteredList.clear();

		if (query == null || query.trim().isEmpty()) {

			filteredList.addAll(originalList);

		} else {

			for (HashMap<String, Object> expense : originalList) {

				String code =
					expense.get("code")
					.toString();

				String item =
					expense.get("item")
					.toString();

				if (SearchUtils.matchesTokensAcrossFields(
					query, code, item)) {

					filteredList.add(expense);
				}
			}
		}

		notifyDataSetChanged();
	}
	
	public void filter(
		String query,
		String fromDate,
		String toDate
	) {

		filteredList.clear();

		for (HashMap<String, Object> expense : originalList) {

			String code =
				expense.get("code")
				.toString();

			String item =
				expense.get("item")
				.toString();

			String date =
				expense.get("date")
				.toString();

			boolean matchesQuery =
				SearchUtils.matchesTokensAcrossFields(query, code, item);

			boolean matchesDate = true;

			if (!fromDate.isEmpty()) {

				matchesDate &=
					date.compareTo(fromDate) >= 0;
			}

			if (!toDate.isEmpty()) {

				matchesDate &=
					date.compareTo(toDate) <= 0;
			}

			if (
				matchesQuery
				&&
				matchesDate
				) {

				filteredList.add(expense);
			}
		}

		notifyDataSetChanged();
	}
	
}
