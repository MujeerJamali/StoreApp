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

	public class Partiesactivity extends Activity {

    EditText et_search_party;
    TextView tv_no_parties;

    Button btn_add_party;

    Spinner spinner_party_sort;

    ListView lv_parties;

    // Each entry pairs a DatabaseHelper.PARTY_SORT_* with the ascending
    // flag that gives it the label at the same index in
    // partySortLabels() - order must match the Spinner's options.
    private static final int[] PARTY_SORT_VALUES = {
        DatabaseHelper.PARTY_SORT_LATEST_TXN, DatabaseHelper.PARTY_SORT_LATEST_TXN,
        DatabaseHelper.PARTY_SORT_BALANCE, DatabaseHelper.PARTY_SORT_BALANCE,
        DatabaseHelper.PARTY_SORT_NAME, DatabaseHelper.PARTY_SORT_NAME
    };

    private static final boolean[] PARTY_SORT_ASCENDING = {
        false, true, false, true, true, false
    };

    private static final String[] PARTY_SORT_LABELS = {
        "Recent Activity", "Oldest Activity",
        "Balance: High to Low", "Balance: Low to High",
        "Name: A-Z", "Name: Z-A"
    };

    DatabaseHelper db;

    ArrayList<HashMap<String, Object>> partyList;

		PartyAdapter adapter;

		private int selectedPartySort = DatabaseHelper.PARTY_SORT_LATEST_TXN;
		private boolean selectedPartySortAscending = false;

		@Override
		protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.partiesactivityv);

        et_search_party = findViewById(R.id.et_search_party);
        tv_no_parties = findViewById(R.id.tv_no_parties);

        btn_add_party = findViewById(R.id.btn_add_party);

        spinner_party_sort = findViewById(R.id.spinner_party_sort);

        lv_parties = findViewById(R.id.lv_parties);

				lv_parties.setEmptyView(tv_no_parties);

				db = new DatabaseHelper(this);

				ArrayAdapter<String> partySortAdapter = new ArrayAdapter<String>(
					this, android.R.layout.simple_spinner_item, PARTY_SORT_LABELS
				);

				partySortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				spinner_party_sort.setAdapter(partySortAdapter);

				spinner_party_sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
						@Override
						public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
							selectPartySort(PARTY_SORT_VALUES[position], PARTY_SORT_ASCENDING[position]);
						}

						@Override
						public void onNothingSelected(AdapterView<?> parent) {
						}
					});

				loadParties();

				et_search_party.addTextChangedListener(new TextWatcher() {

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

					btn_add_party.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

			startActivity(new Intent(
		Partiesactivity.this,
		Addpartyactivity.class
                ));
				}
					});

			lv_parties.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

		HashMap<String, Object> party =
		(HashMap<String, Object>) adapter.getItem(position);

			int partyId = (Integer) party.get("id");

	Intent intent = new Intent(
Partiesactivity.this,
                        Partyviewactivity.class
                );

                intent.putExtra("party_id", partyId);

                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadParties();
    }

    private void selectPartySort(int sort, boolean ascending) {

        selectedPartySort = sort;
        selectedPartySortAscending = ascending;

        loadParties();
    }

    private void loadParties() {

        partyList = db.getPartiesWithActivity(
            selectedPartySort, selectedPartySortAscending, DatabaseHelper.PARTY_BALANCE_FILTER_ALL
        );

        adapter = new PartyAdapter(
                this,
                partyList
        );

        lv_parties.setAdapter(adapter);

        if (et_search_party != null) {
            adapter.getFilter().filter(et_search_party.getText().toString());
        }
    }
}
