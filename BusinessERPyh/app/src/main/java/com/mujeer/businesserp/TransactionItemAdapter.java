package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class TransactionItemAdapter extends BaseAdapter {

    Activity activity;
    ArrayList<HashMap<String, Object>> list;

    public TransactionItemAdapter(Activity activity,
                               ArrayList<HashMap<String, Object>> list) {

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
    public View getView(int position,
                        View convertView,
                        ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(activity)
				.inflate(R.layout.transaction_item_view,
						 parent,
						 false);
        }

        HashMap<String, Object> item = list.get(position);

        TextView tvCode = convertView.findViewById(R.id.tv_code);
        TextView tvName = convertView.findViewById(R.id.tv_name);
        TextView tvQuantity = convertView.findViewById(R.id.tv_quantity);
        TextView tvPrice = convertView.findViewById(R.id.tv_price);
        TextView tvTotal = convertView.findViewById(R.id.tv_total);

        tvCode.setText("Code: " + item.get("code"));
        tvName.setText("Item: " + item.get("name"));
        tvQuantity.setText("Qty: " + item.get("quantity"));
        Object price = item.get("purchase_price");

		if (price == null) {
			price = item.get("sale_price");
		}

		tvPrice.setText("Price: " + price);
        tvTotal.setText("Total: " + item.get("total"));

        return convertView;
    }
}
