package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// Backs LinkExpenseActivity's "Pick an Expense" list - each row is one
// not-yet-linked expense (see DatabaseHelper#getUnlinkedExpenses()), plus
// an always-last "+ Add New Expense" row (present regardless of what's
// typed in the search box) that opens Expenseeditactivity and returns
// with the newly created expense selected - same pattern
// TwoLineAutoCompleteAdapter uses for its own "+ Add New ..." row.
public class ExpensePickerAdapter extends BaseAdapter implements Filterable {

	public static final String ADD_NEW_LABEL = "+ Add New Expense";

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> originalList;
	private final ArrayList<HashMap<String, Object>> filteredList;

	public ExpensePickerAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
		this.activity = activity;
		this.originalList = list;
		this.filteredList = new ArrayList<>(list);
	}

	public boolean isAddNewPosition(int position) {
		return position == filteredList.size();
	}

	@Override
	public int getCount() {
		return filteredList.size() + 1;
	}

	@Override
	public Object getItem(int position) {
		return isAddNewPosition(position) ? null : filteredList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return isAddNewPosition(position) ? -1 : (Integer) filteredList.get(position).get("id");
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		convertView = LayoutInflater.from(activity)
			.inflate(R.layout.autocomplete_two_line_item, parent, false);

		TextView tvLine1 = convertView.findViewById(R.id.tv_line1);
		TextView tvLine2 = convertView.findViewById(R.id.tv_line2);

		if (isAddNewPosition(position)) {

			tvLine1.setText(ADD_NEW_LABEL);
			tvLine1.setTextColor(activity.getResources().getColor(R.color.primary));
			tvLine1.setTypeface(null, android.graphics.Typeface.BOLD);

			tvLine2.setVisibility(View.GONE);

			return convertView;
		}

		HashMap<String, Object> expense = filteredList.get(position);

		tvLine1.setText(
			expense.get("item") + " - " + AmountFormat.format((Double) expense.get("amount"))
		);
		tvLine1.setTextColor(activity.getResources().getColor(R.color.text_primary));
		tvLine1.setTypeface(null, android.graphics.Typeface.NORMAL);

		tvLine2.setVisibility(View.VISIBLE);
		tvLine2.setText(String.valueOf(expense.get("date")));

		return convertView;
	}

	@Override
	public Filter getFilter() {

		return new Filter() {

			@Override
			protected FilterResults performFiltering(CharSequence constraint) {

				ArrayList<HashMap<String, Object>> list = new ArrayList<>();

				if (constraint == null || constraint.length() == 0) {

					list.addAll(originalList);

				} else {

					for (HashMap<String, Object> map : originalList) {

						String item = (String) map.get("item");

						if (SearchUtils.matchesTokensAcrossFields(constraint.toString(), item)) {
							list.add(map);
						}
					}
				}

				FilterResults results = new FilterResults();
				results.values = list;
				results.count = list.size() + 1;

				return results;
			}

			@Override
			@SuppressWarnings("unchecked")
			protected void publishResults(CharSequence constraint, FilterResults results) {

				filteredList.clear();
				filteredList.addAll((ArrayList<HashMap<String, Object>>) results.values);

				notifyDataSetChanged();
			}
		};
	}
}
