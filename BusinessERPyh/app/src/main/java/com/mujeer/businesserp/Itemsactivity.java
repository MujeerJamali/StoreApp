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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class Itemsactivity extends Activity {

    EditText et_search_item;
    TextView tv_no_items;

    Button btn_add_item;

    Spinner spinner_item_filter;

    ListView lv_items;

    private static final int[] ITEM_FILTER_VALUES = {
        DatabaseHelper.ITEM_ACTIVE_FILTER_ALL,
        DatabaseHelper.ITEM_ACTIVE_FILTER_ACTIVE_ONLY,
        DatabaseHelper.ITEM_ACTIVE_FILTER_INACTIVE_ONLY
    };

    private static final String[] ITEM_FILTER_LABELS = {
        "All Items", "Active Only", "Inactive Only"
    };

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

        spinner_item_filter = findViewById(R.id.spinner_item_filter);

        lv_items = findViewById(R.id.lv_items);

        lv_items.setEmptyView(tv_no_items);

        db = new DatabaseHelper(this);

        ArrayAdapter<String> itemFilterAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, ITEM_FILTER_LABELS
        );

        itemFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_item_filter.setAdapter(itemFilterAdapter);

        spinner_item_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectedItemFilter = ITEM_FILTER_VALUES[position];
					loadItems();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
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

    private void loadItems() {

        itemList = db.getItems(selectedItemFilter);

        HashMap<Integer, String> trends = db.getItemTrends();

        for (HashMap<String, Object> item : itemList) {

            Integer itemId = (Integer) item.get("id");
            String trend = itemId == null ? null : trends.get(itemId);

            if (trend != null) {
                item.put("trend", trend);
            }
        }

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
