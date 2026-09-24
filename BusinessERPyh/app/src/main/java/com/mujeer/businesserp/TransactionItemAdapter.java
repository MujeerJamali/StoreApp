package com.mujeer.businesserp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Shows the line items added to the purchase/sale currently being
// created/edited. 'list' (passed in by Transactioneditactivity) is the
// real, authoritative line-item list that gets saved - this adapter
// never mutates it. filter() instead builds 'displayed', a search-
// narrowed view over the same HashMap objects (not copies), so a
// displayed position can still be resolved back to its real position
// in 'list' via getRealIndex() - see Transactioneditactivity's
// edit/remove handlers, which need the real index to edit/remove the
// right line even while a search filter is narrowing what's shown.
// =====================
public class TransactionItemAdapter extends BaseAdapter {

    Activity activity;
    ArrayList<HashMap<String, Object>> list;
    ArrayList<HashMap<String, Object>> displayed;
    String currentQuery = "";

    public TransactionItemAdapter(Activity activity,
                               ArrayList<HashMap<String, Object>> list) {

        this.activity = activity;
        this.list = list;
        this.displayed = new ArrayList<HashMap<String, Object>>(list);
    }

    // Re-applies the current search query against 'list' - call this
    // (or notifyDataSetChanged(), which does it for you) any time
    // 'list' itself has changed (an item added/edited/removed).
    public void filter(String query) {

        currentQuery = query == null ? "" : query;

        displayed = new ArrayList<HashMap<String, Object>>();

        for (HashMap<String, Object> item : list) {

            String name = String.valueOf(item.get("name"));
            String code = String.valueOf(item.get("code"));

            if (SearchUtils.matchesTokensAcrossFields(currentQuery, name, code)) {
                displayed.add(item);
            }
        }

        notifyDataSetChangedInternal();
    }

    // The index this displayed row actually has in the real list - use
    // this instead of the raw adapter position whenever editing/removing
    // a line, since a search filter can make them different.
    public int getRealIndex(int displayedPosition) {

        if (displayedPosition < 0 || displayedPosition >= displayed.size()) {
            return -1;
        }

        HashMap<String, Object> target = displayed.get(displayedPosition);

        for (int i = 0; i < list.size(); i++) {

            if (list.get(i) == target) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void notifyDataSetChanged() {

        // Re-filter first so 'displayed' reflects whatever just changed
        // in 'list' (an add/edit/remove elsewhere calls this same
        // method), then let BaseAdapter do its normal notification.
        filter(currentQuery);
    }

    private void notifyDataSetChangedInternal() {
        super.notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return displayed.size();
    }

    @Override
    public Object getItem(int position) {
        return displayed.get(position);
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

        HashMap<String, Object> item = displayed.get(position);

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
