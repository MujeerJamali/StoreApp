package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class Itemviewactivity extends Activity {

    TextView tv_item_code;
    TextView tv_item_name;
    TextView tv_purchase_price;
    TextView tv_sale_price;
    TextView tv_item_stock;
    TextView tv_transactions_empty;

    Button btn_edit_item;
    Button btn_delete_item;

    ListView lv_transactions;

    ArrayList<HashMap<String, Object>> transactionList;

    TransactionAdapter transactionAdapter;

    DatabaseHelper db;

    int itemId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.itemviewactivity);

        tv_item_code = findViewById(R.id.tv_item_code);
        tv_item_name = findViewById(R.id.tv_item_name);
        tv_purchase_price = findViewById(R.id.tv_purchase_price);
        tv_sale_price = findViewById(R.id.tv_sale_price);
        tv_item_stock = findViewById(R.id.tv_item_stock);
        tv_transactions_empty = findViewById(R.id.tv_transactions_empty);

        btn_edit_item = findViewById(R.id.btn_edit_item);
        btn_delete_item = findViewById(R.id.btn_delete_item);

        lv_transactions = findViewById(R.id.lv_transactions);

        db = new DatabaseHelper(this);

        transactionList = new ArrayList<HashMap<String, Object>>();

        itemId = getIntent().getIntExtra("item_id", -1);

        loadItem();
        loadTransactions();

        lv_transactions.setOnItemClickListener(
            new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {

                    HashMap<String, Object> transaction =
                        (HashMap<String, Object>) transactionAdapter.getItem(position);

                    Intent intent = new Intent(
                        Itemviewactivity.this,
                        Transactionviewactivity.class
                    );

                    intent.putExtra(
                        "transaction_type",
                        Integer.parseInt(
                            transaction.get("transaction_type").toString()
                        )
                    );

                    intent.putExtra(
                        "transaction_id",
                        Integer.parseInt(
                            transaction.get("id").toString()
                        )
                    );

                    startActivity(intent);
                }
            }
        );

        btn_edit_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
                        Itemviewactivity.this,
                        Itemseditactivity.class
					);

					intent.putExtra("item_id", itemId);

					startActivity(intent);
				}
			});

        btn_delete_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					new AlertDialog.Builder(Itemviewactivity.this)
                        .setTitle("Delete Item")
                        .setMessage("Are you sure you want to delete this item?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                if (db.deleteItem(itemId)) {

                                    Toast.makeText(
										Itemviewactivity.this,
										"Item deleted",
										Toast.LENGTH_SHORT
                                    ).show();

                                    finish();

                                } else {

                                    Toast.makeText(
										Itemviewactivity.this,
										"Delete failed",
										Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
				}
			});
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItem();
        loadTransactions();
    }

    private void loadTransactions() {

        if (itemId == -1) {
            return;
        }

        transactionList.clear();

        transactionList.addAll(
            db.getTransactionsByItem(itemId)
        );

        transactionAdapter = new TransactionAdapter(
            this,
            transactionList
        );

        lv_transactions.setAdapter(transactionAdapter);
        lv_transactions.setEmptyView(tv_transactions_empty);

        setListViewHeightBasedOnChildren(lv_transactions);
    }

    // Mirrors the sizing fix used in Transactionviewactivity: a ListView
    // placed inside a ScrollView doesn't get real scrolling of its own,
    // so it needs its height calculated from its actual children (at the
    // list's real width, so wrapped rows measure correctly) instead of
    // being left to clip at whatever height it happens to get.
    private void setListViewHeightBasedOnChildren(ListView listView) {

        android.widget.ListAdapter listAdapter = listView.getAdapter();

        if (listAdapter == null || listAdapter.getCount() == 0) {
            return;
        }

        int listViewWidth = listView.getWidth();

        if (listViewWidth <= 0) {

            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int paddingPx = (int) (32 * metrics.density);
            listViewWidth = metrics.widthPixels - paddingPx;
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(
            listViewWidth, View.MeasureSpec.EXACTLY);

        int heightSpec = View.MeasureSpec.makeMeasureSpec(
            0, View.MeasureSpec.UNSPECIFIED);

        int totalHeight = 0;

        for (int i = 0; i < listAdapter.getCount(); i++) {

            View listItem = listAdapter.getView(i, null, listView);

            listItem.measure(widthSpec, heightSpec);

            totalHeight += listItem.getMeasuredHeight();
        }

        android.view.ViewGroup.LayoutParams params =
            listView.getLayoutParams();

        params.height =
            totalHeight +
            (listView.getDividerHeight() *
            (listAdapter.getCount() - 1));

        listView.setLayoutParams(params);

        listView.requestLayout();
    }

    private void loadItem() {

        if (itemId != -1) {

            HashMap<String, Object> item = db.getItemById(itemId);

            tv_item_code.setText((String) item.get("code"));
            tv_item_name.setText((String) item.get("name"));
            tv_purchase_price.setText(String.valueOf(item.get("purchase_price")));
            tv_sale_price.setText(String.valueOf(item.get("sale_price")));

            double stock = 0;

            if (item.get("balance") != null) {
                stock = (Double) item.get("balance");
            }

            tv_item_stock.setText(String.valueOf(stock));

            if (stock <= 0) {
                tv_item_stock.setTextColor(getResources().getColor(R.color.danger));
            } else {
                tv_item_stock.setTextColor(getResources().getColor(R.color.mod_items));
            }
        }
    }
}
