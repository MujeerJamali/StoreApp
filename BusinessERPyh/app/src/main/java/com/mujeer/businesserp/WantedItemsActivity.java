package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Lets staff log something a customer asked for that the shop doesn't
// currently have - either a catalog item that's out of stock, or
// something not registered as an item at all. Not tied to a
// transaction; just a running request log staff can check off once
// fulfilled (e.g. restocked and the customer was told).
// =====================
public class WantedItemsActivity extends Activity {

    private Button btnAdd;
    private CheckBox cbShowFulfilled;
    private TextView tvEmpty;
    private ListView lvList;

    private DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wanted_items_activity);

        btnAdd = findViewById(R.id.btn_add_wanted_item);
        cbShowFulfilled = findViewById(R.id.cb_show_fulfilled);
        tvEmpty = findViewById(R.id.tv_wanted_items_empty);
        lvList = findViewById(R.id.lv_wanted_items);

        db = new DatabaseHelper(this);

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptWantedItemDialog(null);
            }
        });

        cbShowFulfilled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                loadList();
            }
        });

        lvList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                HashMap<String, Object> existing =
                    (HashMap<String, Object>) lvList.getAdapter().getItem(position);

                promptWantedItemDialog(existing);
            }
        });

        loadList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadList();
    }

    private void loadList() {

        final boolean includeFulfilled = cbShowFulfilled.isChecked();

        new Thread(new Runnable() {
            @Override
            public void run() {

                final ArrayList<HashMap<String, Object>> items =
                    db.getWantedItems(includeFulfilled);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        lvList.setAdapter(
                            new WantedItemsAdapter(
                                WantedItemsActivity.this,
                                items,
                                new WantedItemsAdapter.OnFulfilledToggleListener() {
                                    @Override
                                    public void onToggle(int wantedItemId, boolean fulfilled) {
                                        toggleFulfilled(wantedItemId, fulfilled);
                                    }
                                }
                            )
                        );

                        lvList.setEmptyView(tvEmpty);
                    }
                });
            }
        }).start();
    }

    private void toggleFulfilled(final int wantedItemId, final boolean fulfilled) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                db.setWantedItemFulfilled(wantedItemId, fulfilled);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadList();
                    }
                });
            }
        }).start();
    }

    private void promptWantedItemDialog(final HashMap<String, Object> existing) {

        View view = getLayoutInflater().inflate(R.layout.dialog_wanted_item, null);

        final AutoCompleteTextView actvItemName = view.findViewById(R.id.actv_wanted_item_name);
        final AutoCompleteTextView actvParty = view.findViewById(R.id.actv_wanted_item_party);
        final EditText etNotes = view.findViewById(R.id.et_wanted_item_notes);

        final ArrayList<HashMap<String, Object>> items = db.getItemsForSpinner();
        final ArrayList<String> itemNames = new ArrayList<>();

        for (HashMap<String, Object> item : items) {
            itemNames.add((String) item.get("name"));
        }

        actvItemName.setAdapter(
            new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, itemNames)
        );
        actvItemName.setThreshold(1);

        final ArrayList<HashMap<String, Object>> parties = db.getParties();
        final ArrayList<String> partyNames = new ArrayList<>();

        for (HashMap<String, Object> party : parties) {
            partyNames.add((String) party.get("name"));
        }

        actvParty.setAdapter(
            new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, partyNames)
        );
        actvParty.setThreshold(1);

        if (existing != null) {
            actvItemName.setText(String.valueOf(existing.get("item_name")));

            if (existing.get("party_name") != null) {
                actvParty.setText(String.valueOf(existing.get("party_name")));
            }

            Object notes = existing.get("notes");

            if (notes != null) {
                etNotes.setText(notes.toString());
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Add Wanted Item" : "Edit Wanted Item")
            .setView(view)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    saveWantedItem(existing, items, parties, actvItemName, actvParty, etNotes);
                }
            })
            .setNegativeButton("Cancel", null);

        if (existing != null) {
            builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    deleteWantedItem((Integer) existing.get("id"));
                }
            });
        }

        builder.show();
    }

    private void saveWantedItem(
        HashMap<String, Object> existing,
        ArrayList<HashMap<String, Object>> items,
        ArrayList<HashMap<String, Object>> parties,
        AutoCompleteTextView actvItemName,
        AutoCompleteTextView actvParty,
        EditText etNotes) {

        String itemName = actvItemName.getText().toString().trim();

        if (itemName.length() == 0) {
            Toast.makeText(this, "Enter an item name", Toast.LENGTH_SHORT).show();
            return;
        }

        Integer itemId = null;

        for (HashMap<String, Object> item : items) {
            if (itemName.equalsIgnoreCase((String) item.get("name"))) {
                itemId = (Integer) item.get("id");
                break;
            }
        }

        String partyName = actvParty.getText().toString().trim();
        Integer partyId = null;

        if (partyName.length() > 0) {

            for (HashMap<String, Object> party : parties) {
                if (partyName.equalsIgnoreCase((String) party.get("name"))) {
                    partyId = (Integer) party.get("id");
                    break;
                }
            }
        }

        String notes = etNotes.getText().toString().trim();

        if (existing == null) {

            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

            db.insertWantedItem(itemId, itemName, date, time, partyId, notes);

            Toast.makeText(this, "Wanted item saved", Toast.LENGTH_SHORT).show();
        } else {

            db.updateWantedItem((Integer) existing.get("id"), itemId, itemName, partyId, notes);

            Toast.makeText(this, "Wanted item updated", Toast.LENGTH_SHORT).show();
        }

        loadList();
    }

    private void deleteWantedItem(final int wantedItemId) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                db.deleteWantedItem(wantedItemId);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WantedItemsActivity.this, "Wanted item deleted", Toast.LENGTH_SHORT).show();
                        loadList();
                    }
                });
            }
        }).start();
    }
}
