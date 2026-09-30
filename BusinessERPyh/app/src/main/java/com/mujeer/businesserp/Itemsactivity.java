package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class Itemsactivity extends Activity {

    EditText et_search_item;
    TextView tv_no_items;

    Button btn_add_item;

    Button btn_item_filter_all;
    Button btn_item_filter_active;
    Button btn_item_filter_inactive;

    ListView lv_items;

    DatabaseHelper db;

    ArrayList<HashMap<String, Object>> itemList;

    ItemAdapter adapter;

    private int selectedItemFilter = DatabaseHelper.ITEM_ACTIVE_FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.itemsactivityv);

        et_search_item = findViewById(R.id.et_search_item);
        tv_no_items = findViewById(R.id.tv_no_items);

        btn_add_item = findViewById(R.id.btn_add_item);

        btn_item_filter_all = findViewById(R.id.btn_item_filter_all);
        btn_item_filter_active = findViewById(R.id.btn_item_filter_active);
        btn_item_filter_inactive = findViewById(R.id.btn_item_filter_inactive);

        lv_items = findViewById(R.id.lv_items);

        lv_items.setEmptyView(tv_no_items);

        db = new DatabaseHelper(this);

        btn_item_filter_all.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectItemFilter(DatabaseHelper.ITEM_ACTIVE_FILTER_ALL);
				}
			});

        btn_item_filter_active.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectItemFilter(DatabaseHelper.ITEM_ACTIVE_FILTER_ACTIVE_ONLY);
				}
			});

        btn_item_filter_inactive.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectItemFilter(DatabaseHelper.ITEM_ACTIVE_FILTER_INACTIVE_ONLY);
				}
			});

        loadItems();

        et_search_item.addTextChangedListener(new TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

					if (adapter != null) {
						adapter.getFilter().filter(s);
					}
				}

				@Override
				public void afterTextChanged(Editable s) {
				}
			});

        btn_add_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					startActivity(new Intent(
									  Itemsactivity.this,
									  Additemactivity.class
								  ));
				}
			});

        lv_items.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> item =
                        (HashMap<String, Object>) adapter.getItem(position);

					Intent intent = new Intent(
                        Itemsactivity.this,
                        Itemviewactivity.class
					);

					intent.putExtra("item_id", (Integer) item.get("id"));

					startActivity(intent);
				}
			});
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
    }

    private void selectItemFilter(int filter) {

        selectedItemFilter = filter;

        Button[] buttons = {btn_item_filter_all, btn_item_filter_active, btn_item_filter_inactive};

        int[] filters = {
            DatabaseHelper.ITEM_ACTIVE_FILTER_ALL,
            DatabaseHelper.ITEM_ACTIVE_FILTER_ACTIVE_ONLY,
            DatabaseHelper.ITEM_ACTIVE_FILTER_INACTIVE_ONLY
        };

        for (int i = 0; i < buttons.length; i++) {

            if (filters[i] == filter) {

                buttons[i].setBackgroundResource(R.drawable.bg_button_primary);
                buttons[i].setTextColor(getResources().getColor(R.color.text_on_primary));

            } else {

                buttons[i].setBackgroundResource(R.drawable.bg_button_outline);
                buttons[i].setTextColor(getResources().getColor(R.color.primary));
            }
        }

        loadItems();
    }

    private void loadItems() {

        itemList = db.getItems(selectedItemFilter);

        adapter = new ItemAdapter(
			this,
			itemList
        );

        lv_items.setAdapter(adapter);

        if (et_search_item != null) {
            adapter.getFilter().filter(et_search_item.getText().toString());
        }
    }
}
