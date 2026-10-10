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
        TextView tvTrend = convertView.findViewById(R.id.tv_item_trend);

        HashMap<String, Object> item = filteredList.get(position);

        tvCode.setText((String) item.get("code"));

        boolean active = !Boolean.FALSE.equals(item.get("active"));

        tvName.setText(active ? (String) item.get("name") : item.get("name") + " (Inactive)");
        tvName.setAlpha(active ? 1f : 0.5f);

        double stock = 0;

        if (item.get("balance") != null) {
            stock = (Double) item.get("balance");
        }

        double reorderThreshold = 0;

        if (item.get("reorder_threshold") != null) {
            reorderThreshold = (Double) item.get("reorder_threshold");
        }

        tvStock.setText("Stock: " + formatQty(stock));

        if (stock <= 0) {
            tvStock.setTextColor(activity.getResources().getColor(R.color.danger));
        } else if (reorderThreshold > 0 && stock <= reorderThreshold) {
            tvStock.setTextColor(activity.getResources().getColor(R.color.warning));
        } else {
            tvStock.setTextColor(activity.getResources().getColor(R.color.mod_items));
        }

        String trend = (String) item.get("trend");

        if ("up".equals(trend)) {

            tvTrend.setVisibility(View.VISIBLE);
            tvTrend.setText("▲");
            tvTrend.setTextColor(activity.getResources().getColor(R.color.danger));

        } else if ("down".equals(trend)) {

            tvTrend.setVisibility(View.VISIBLE);
            tvTrend.setText("▼");
            tvTrend.setTextColor(activity.getResources().getColor(R.color.text_secondary));

        } else {

            tvTrend.setVisibility(View.GONE);
        }

        return convertView;
    }

    // Drops a trailing ".0" for whole-number quantities so stock reads
    // like "12" instead of "12.0", but keeps decimals when they matter
    // (capped to 2 places - see AmountFormat.formatPlain()).
    private String formatQty(double qty) {

        return AmountFormat.formatPlain(qty);
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
