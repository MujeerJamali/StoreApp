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

	// Swipe-left-to-reveal Edit/Delete row actions (see SwipeRevealLayout) -
	// the activity owns the actual edit-navigation/delete-confirm logic
	// since it needs an Activity context for the Intent/AlertDialog.
	public interface RowActionListener {
		void onRowEdit(HashMap<String, Object> expense);
		void onRowDelete(HashMap<String, Object> expense);

		// Only fires for a plain tap while swipeEnabled is true - see
		// SwipeRevealLayout.onTouchEvent()'s own comment.
		void onRowTap(HashMap<String, Object> expense);
	}

	private Activity activity;

	private ArrayList<HashMap<String, Object>> originalList;
	private ArrayList<HashMap<String, Object>> filteredList;

	// Defaults true (unlike TransactionAdapter's own flag, which
	// defaults false since that adapter is reused read-only elsewhere) -
	// this adapter is only ever used for Expensesactivity's own live
	// list, so the user's SwipeGestureSettings preference is the only
	// thing that should ever turn it off.
	private boolean swipeEnabled = true;

	private RowActionListener rowActionListener;

	public void setSwipeEnabled(boolean swipeEnabled) {
		this.swipeEnabled = swipeEnabled;
	}

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

	public void setRowActionListener(RowActionListener listener) {
		this.rowActionListener = listener;
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

		final HashMap<String, Object> expense =
			filteredList.get(position);

		final SwipeRevealLayout swipeLayout = (SwipeRevealLayout) convertView;
		swipeLayout.close(false);

		View swipeActions = convertView.findViewById(R.id.swipe_actions);
		swipeActions.setVisibility(swipeEnabled ? View.VISIBLE : View.GONE);

		swipeLayout.wireActionButtons(
			convertView.findViewById(R.id.btn_swipe_edit),
			convertView.findViewById(R.id.btn_swipe_delete)
		);

		swipeLayout.setOnSwipeActionListener(new SwipeRevealLayout.OnSwipeActionListener() {
				@Override
				public void onEditAction() {
					if (rowActionListener != null) {
						rowActionListener.onRowEdit(expense);
					}
				}

				@Override
				public void onDeleteAction() {
					if (rowActionListener != null) {
						rowActionListener.onRowDelete(expense);
					}
				}

				@Override
				public void onRowTap() {
					if (rowActionListener != null) {
						rowActionListener.onRowTap(expense);
					}
				}
			});

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
			AmountFormat.format(Double.parseDouble(expense.get("amount").toString()))
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
