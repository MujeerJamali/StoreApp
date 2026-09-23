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
    Button btn_delete_all_items;

    ListView lv_items;

    DatabaseHelper db;

    ArrayList<HashMap<String, Object>> itemList;

    ItemAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.itemsactivityv);

        et_search_item = findViewById(R.id.et_search_item);
        tv_no_items = findViewById(R.id.tv_no_items);

        btn_add_item = findViewById(R.id.btn_add_item);
        btn_delete_all_items = findViewById(R.id.btn_delete_all_items);

        lv_items = findViewById(R.id.lv_items);

        lv_items.setEmptyView(tv_no_items);

        db = new DatabaseHelper(this);

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

        btn_delete_all_items.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					new AlertDialog.Builder(Itemsactivity.this)
                        .setTitle("Delete All Items")
                        .setMessage("Are you sure you want to delete all items?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                db.deleteAllItems();

                                loadItems();

                                Toast.makeText(
									Itemsactivity.this,
									"All items deleted",
									Toast.LENGTH_SHORT
                                ).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
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

        itemList = db.getItems();

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
