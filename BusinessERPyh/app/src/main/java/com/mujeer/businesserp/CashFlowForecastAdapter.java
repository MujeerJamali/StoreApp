package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// List adapter for CashFlowForecastReportActivity - each row is one day
// of the forecast with its expected in/out (from Sale/Purchase due
// dates) and the resulting running cash balance.
public class CashFlowForecastAdapter extends BaseAdapter {

	private final Activity activity;
	private final ArrayList<HashMap<String, Object>> list;

	public CashFlowForecastAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
				R.layout.cash_flow_forecast_row, parent, false
			);
		}

		TextView tv_date = convertView.findViewById(R.id.tv_cff_date);
		TextView tv_flow = convertView.findViewById(R.id.tv_cff_flow);
		TextView tv_balance = convertView.findViewById(R.id.tv_cff_balance);

		HashMap<String, Object> row = list.get(position);

		String date = (String) row.get("date");
		double expectedIn = (Double) row.get("expected_in");
		double expectedOut = (Double) row.get("expected_out");
		double runningBalance = (Double) row.get("running_balance");

		tv_date.setText(RelativeDate.format(date));

		if (expectedIn <= 0.01 && expectedOut <= 0.01) {

			tv_flow.setText("No credit due");

		} else {

			StringBuilder flow = new StringBuilder();

			if (expectedIn > 0.01) {
				flow.append("+").append(AmountFormat.format(expectedIn)).append(" in");
			}

			if (expectedOut > 0.01) {

				if (flow.length() > 0) {
					flow.append("  ");
				}

				flow.append("-").append(AmountFormat.format(expectedOut)).append(" out");
			}

			tv_flow.setText(flow.toString());
		}

		tv_balance.setText(AmountFormat.format(runningBalance));

		tv_balance.setTextColor(
			activity.getResources().getColor(
				runningBalance < 0 ? R.color.danger : R.color.text_primary
			)
		);

		return convertView;
	}
}
