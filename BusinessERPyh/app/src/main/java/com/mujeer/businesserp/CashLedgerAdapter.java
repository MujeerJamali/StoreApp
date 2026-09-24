package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class CashLedgerAdapter extends BaseAdapter {

    private final Activity activity;
    private final ArrayList<HashMap<String, Object>> list;

    public CashLedgerAdapter(Activity activity, ArrayList<HashMap<String, Object>> list) {
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
            convertView = LayoutInflater.from(activity)
                .inflate(R.layout.cash_ledger_row, parent, false);
        }

        HashMap<String, Object> row = list.get(position);

        TextView tvLabel = convertView.findViewById(R.id.tv_ledger_label);
        TextView tvDate = convertView.findViewById(R.id.tv_ledger_date);
        TextView tvAmount = convertView.findViewById(R.id.tv_ledger_amount);

        double amount = (Double) row.get("amount");

        tvLabel.setText(String.valueOf(row.get("label")));

        tvDate.setText(
            row.get("date") + " " + row.get("time") + " · " + row.get("source")
        );

        tvAmount.setText(
            String.format(Locale.getDefault(), "%s%.2f", amount >= 0 ? "+" : "", amount)
        );

        tvAmount.setTextColor(
            activity.getResources().getColor(
                amount >= 0 ? R.color.success : R.color.danger
            )
        );

        return convertView;
    }
}
