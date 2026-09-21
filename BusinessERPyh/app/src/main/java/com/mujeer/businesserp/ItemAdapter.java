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

public class ItemAdapter extends BaseAdapter implements Filterable {

    Activity activity;

    ArrayList<HashMap<String, Object>> originalList;
    ArrayList<HashMap<String, Object>> filteredList;

    public ItemAdapter(Activity activity,
                       ArrayList<HashMap<String, Object>> list) {

        this.activity = activity;
        this.originalList = list;
        this.filteredList = new ArrayList<>(list);
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
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(activity)
				.inflate(R.layout.item_view, parent, false);
        }

        TextView tvCode = convertView.findViewById(R.id.tv_item_code);
        TextView tvName = convertView.findViewById(R.id.tv_item_name);
        TextView tvStock = convertView.findViewById(R.id.tv_item_stock);

        HashMap<String, Object> item = filteredList.get(position);

        tvCode.setText((String) item.get("code"));
        tvName.setText((String) item.get("name"));

        double stock = 0;

        if (item.get("balance") != null) {
            stock = (Double) item.get("balance");
        }

        tvStock.setText("Stock: " + formatQty(stock));

        if (stock <= 0) {
            tvStock.setTextColor(activity.getResources().getColor(R.color.danger));
        } else {
            tvStock.setTextColor(activity.getResources().getColor(R.color.mod_items));
        }

        return convertView;
    }

    // Drops a trailing ".0" for whole-number quantities so stock reads
    // like "12" instead of "12.0", but keeps decimals when they matter.
    private String formatQty(double qty) {

        if (qty == Math.rint(qty)) {
            return String.valueOf((long) qty);
        }

        return String.valueOf(qty);
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

                        String code = (String) map.get("code");
                        String name = (String) map.get("name");

                        if (SearchUtils.matchesTokensAcrossFields(
                            constraint.toString(), code, name)) {

                            list.add(map);
                        }
                    }
                }

                FilterResults results = new FilterResults();
                results.values = list;
                results.count = list.size();

                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint,
                                          FilterResults results) {

                filteredList.clear();
                filteredList.addAll((ArrayList<HashMap<String, Object>>) results.values);

                notifyDataSetChanged();
            }
        };
    }
}
