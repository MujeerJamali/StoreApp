package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for NetProfitReportActivity's per-item breakdown - either
// mode of the merged Item Profitability report: "This Period" (each row
// is one item with at least one sale in the selected period, showing
// its quantity sold, sales total, and profit - see
// DatabaseHelper.getNetProfitByItem()) or "Standing Margin" (every
// active item's own current margin per unit and margin %, regardless of
// period or whether it's ever sold - see
// DatabaseHelper.getStandingMarginByItem()).
public class ItemProfitAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	private boolean standingMarginMode = false;

	public ItemProfitAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
		this.activity = activity;
		this.list = list;
	}

	public void setStandingMarginMode(boolean standingMarginMode) {
		this.standingMarginMode = standingMarginMode;
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
				R.layout.item_profit_row, parent, false
			);
		}

		TextView tv_name = convertView.findViewById(R.id.tv_item_profit_name);
		TextView tv_meta = convertView.findViewById(R.id.tv_item_profit_meta);
		TextView tv_amount = convertView.findViewById(R.id.tv_item_profit_amount);

		HashMap<String, Object> row = list.get(position);

		String name = row.get("item_name") == null ? "" : row.get("item_name").toString();
		String code = row.get("item_code") == null ? "" : row.get("item_code").toString();

		tv_name.setText(name);

		double amount;

		if (standingMarginMode) {

			double salePrice = toDouble(row.get("sale_price"));
			double marginPercent = toDouble(row.get("margin_percent"));

			tv_meta.setText(
				code + " · Price " + AmountFormat.format(salePrice) +
				" · " + AmountFormat.formatPlain(marginPercent) + "% margin"
			);

			amount = toDouble(row.get("margin"));

		} else {

			double qty = toDouble(row.get("qty"));
			double salesAmount = toDouble(row.get("sales_amount"));

			tv_meta.setText(
				code + " · Qty " + AmountFormat.format(qty) + " · Sales " + AmountFormat.format(salesAmount)
			);

			amount = toDouble(row.get("profit"));
		}

		tv_amount.setText(AmountFormat.format(amount));

		tv_amount.setTextColor(
			activity.getResources().getColor(amount >= 0 ? R.color.success : R.color.danger)
		);

		return convertView;
	}

	private double toDouble(Object value) {

		if (value == null) {
			return 0;
		}

		return Double.parseDouble(value.toString());
	}
}
