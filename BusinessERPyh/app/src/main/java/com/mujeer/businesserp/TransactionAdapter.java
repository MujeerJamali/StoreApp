package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class TransactionAdapter extends BaseAdapter {

	private Activity activity;

	private ArrayList<HashMap<String, Object>> originalList;
	private ArrayList<HashMap<String, Object>> filteredList;

	private TextView tv_type;
	private TextView tv_code;
	private TextView tv_party;
	private TextView tv_date;
	private TextView tv_total;

	public TransactionAdapter(
		Activity activity,
		ArrayList<HashMap<String, Object>> list) {

		this.activity = activity;

		this.originalList = list;
		this.filteredList = new ArrayList<HashMap<String, Object>>(list);
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
				R.layout.transaction_adapter,
				parent,
				false
			);
		}

		tv_type = convertView.findViewById(R.id.tv_type);
		tv_code = convertView.findViewById(R.id.tv_code);
		tv_party = convertView.findViewById(R.id.tv_party);
		tv_date = convertView.findViewById(R.id.tv_date);
		tv_total = convertView.findViewById(R.id.tv_total);

		HashMap<String, Object> transaction =
			filteredList.get(position);

		if (tv_type != null) {

			if (transaction.containsKey("transaction_type")) {

				int type = Integer.parseInt(
					transaction.get("transaction_type").toString()
				);

				if (type == DatabaseHelper.TRANSACTION_TYPE_SALE) {

					tv_type.setText("Sale");
					tv_type.setTextColor(activity.getResources().getColor(R.color.mod_sales));

				} else if (type == DatabaseHelper.TRANSACTION_TYPE_PURCHASE) {

					tv_type.setText("Purchase");
					tv_type.setTextColor(activity.getResources().getColor(R.color.mod_purchase));

				} else if (type == DatabaseHelper.TRANSACTION_TYPE_PAYMENT_IN) {

					tv_type.setText("Payment In");
					tv_type.setTextColor(activity.getResources().getColor(R.color.success));

				} else if (type == DatabaseHelper.TRANSACTION_TYPE_PAYMENT_OUT) {

					tv_type.setText("Payment Out");
					tv_type.setTextColor(activity.getResources().getColor(R.color.danger));
				}

				tv_type.setVisibility(View.VISIBLE);

			} else {

				tv_type.setVisibility(View.GONE);
			}
		}

		if (transaction.containsKey("code")) {

			tv_code.setText(
				transaction.get("code").toString()
			);

		} else {

			tv_code.setText(
				transaction.get("invoice_no").toString()
			);
		}

		Object party =
			transaction.get("party_name");

		tv_party.setText(
			party == null
			? ""
			: party.toString()
		);

		tv_date.setText(
			RelativeDate.format(transaction.get("date").toString())
		);

		tv_total.setText(
			transaction.get("grand_total").toString()
		);

		return convertView;
	}
	
	public void filter(String text) {

		filteredList.clear();

		if (text == null || text.trim().length() == 0) {

			filteredList.addAll(originalList);

		} else {

			for (HashMap<String, Object> transaction : originalList) {

				Object codeValue =
					transaction.containsKey("code") ?
					transaction.get("code") :
					transaction.get("invoice_no");

				String code =
					codeValue == null ?
					"" :
					codeValue.toString();

				String party =
					transaction.get("party_name") == null ?
					"" :
					transaction.get("party_name").toString();

				if (SearchUtils.matchesTokensAcrossFields(text, code, party)) {

					filteredList.add(transaction);
				}
			}
		}

		notifyDataSetChanged();
	}

}
