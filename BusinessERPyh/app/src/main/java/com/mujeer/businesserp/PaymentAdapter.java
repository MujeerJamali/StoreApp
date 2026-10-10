package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class PaymentAdapter extends BaseAdapter {

	// Swipe-left-to-reveal Edit/Delete row actions (see SwipeRevealLayout) -
	// the activity owns the actual edit-navigation/delete-confirm logic
	// since it needs an Activity context for the Intent/AlertDialog.
	public interface RowActionListener {
		void onRowEdit(HashMap<String, Object> payment);
		void onRowDelete(HashMap<String, Object> payment);

		// Only fires for a plain tap while swipeEnabled is true - see
		// SwipeRevealLayout.onTouchEvent()'s own comment.
		void onRowTap(HashMap<String, Object> payment);
	}

	private Activity activity;

	private ArrayList<HashMap<String, Object>> originalList;
	private ArrayList<HashMap<String, Object>> filteredList;

	// Defaults true (unlike TransactionAdapter's own flag, which
	// defaults false since that adapter is reused read-only elsewhere) -
	// this adapter is only ever used for Paymentactivity's own live
	// list, so the user's SwipeGestureSettings preference is the only
	// thing that should ever turn it off.
	private boolean swipeEnabled = true;

	private RowActionListener rowActionListener;

	public void setSwipeEnabled(boolean swipeEnabled) {
		this.swipeEnabled = swipeEnabled;
	}

	private TextView tv_code;
	private TextView tv_type;
	private TextView tv_party;
	private TextView tv_date;
	private TextView tv_amount;

	public PaymentAdapter(
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
		return (Integer) filteredList.get(position).get("id");
	}

	@Override
	public View getView(
		int position,
		View convertView,
		ViewGroup parent) {

		if (convertView == null) {

			convertView = LayoutInflater.from(activity).inflate(
				R.layout.payment_adapter,
				parent,
				false
			);
		}

		tv_code = convertView.findViewById(R.id.tv_code);
		tv_type = convertView.findViewById(R.id.tv_type);
		tv_party = convertView.findViewById(R.id.tv_party);
		tv_date = convertView.findViewById(R.id.tv_date);
		tv_amount = convertView.findViewById(R.id.tv_amount);

		final HashMap<String, Object> payment =
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
						rowActionListener.onRowEdit(payment);
					}
				}

				@Override
				public void onDeleteAction() {
					if (rowActionListener != null) {
						rowActionListener.onRowDelete(payment);
					}
				}

				@Override
				public void onRowTap() {
					if (rowActionListener != null) {
						rowActionListener.onRowTap(payment);
					}
				}
			});

		tv_code.setText(
			payment.get("code").toString()
		);

		int type = Integer.parseInt(
			payment.get("type").toString()
		);

		if (type == DatabaseHelper.PAYMENT_IN) {

			tv_type.setText("Payment In");

		} else {

			tv_type.setText("Payment Out");
		}

		tv_party.setText(
			payment.get("party_name").toString()
		);

		tv_date.setText(
			RelativeDate.format(payment.get("date").toString())
		);

		tv_amount.setText(
			"Rs. " +
			AmountFormat.format(Double.parseDouble(payment.get("amount").toString()))
		);

		return convertView;
	}

	public void filter(String keyword) {

		filteredList.clear();

		if (keyword == null) {

			keyword = "";
		}

		keyword = keyword
			.toLowerCase()
			.trim();

		if (keyword.length() == 0) {

			filteredList.addAll(
				originalList
			);

		} else {

			for (HashMap<String, Object> payment : originalList) {

				String code =
					payment.get("code")
					.toString();

				String party =
					payment.get("party_name")
					.toString();

				String amount =
					payment.get("amount")
					.toString();

				String notes = "";

				if (payment.get("notes") != null) {

					notes =
						payment.get("notes")
						.toString();
				}

				if (SearchUtils.matchesTokensAcrossFields(
					keyword, code, party, amount, notes)) {

					filteredList.add(
						payment
					);
				}
			}
		}

		notifyDataSetChanged();
	}
}
