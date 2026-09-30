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

	public class Partiesactivity extends Activity {

    EditText et_search_party;
    TextView tv_no_parties;

    Button btn_add_party;

    Button btn_party_sort_recent;
    Button btn_party_sort_oldest;
    Button btn_party_sort_balance_high;
    Button btn_party_sort_balance_low;
    Button btn_party_sort_name_asc;
    Button btn_party_sort_name_desc;

    ListView lv_parties;

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

        btn_party_sort_recent = findViewById(R.id.btn_party_sort_recent);
        btn_party_sort_oldest = findViewById(R.id.btn_party_sort_oldest);
        btn_party_sort_balance_high = findViewById(R.id.btn_party_sort_balance_high);
        btn_party_sort_balance_low = findViewById(R.id.btn_party_sort_balance_low);
        btn_party_sort_name_asc = findViewById(R.id.btn_party_sort_name_asc);
        btn_party_sort_name_desc = findViewById(R.id.btn_party_sort_name_desc);

        lv_parties = findViewById(R.id.lv_parties);

				lv_parties.setEmptyView(tv_no_parties);

				db = new DatabaseHelper(this);

				btn_party_sort_recent.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						selectPartySort(DatabaseHelper.PARTY_SORT_LATEST_TXN, false);
					}
				});

				btn_party_sort_oldest.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						selectPartySort(DatabaseHelper.PARTY_SORT_LATEST_TXN, true);
					}
				});

				btn_party_sort_balance_high.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						selectPartySort(DatabaseHelper.PARTY_SORT_BALANCE, false);
					}
				});

				btn_party_sort_balance_low.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						selectPartySort(DatabaseHelper.PARTY_SORT_BALANCE, true);
					}
				});

				btn_party_sort_name_asc.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						selectPartySort(DatabaseHelper.PARTY_SORT_NAME, true);
					}
				});

				btn_party_sort_name_desc.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						selectPartySort(DatabaseHelper.PARTY_SORT_NAME, false);
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

        Button[] buttons = {
            btn_party_sort_recent, btn_party_sort_oldest,
            btn_party_sort_balance_high, btn_party_sort_balance_low,
            btn_party_sort_name_asc, btn_party_sort_name_desc
        };

        int[] sorts = {
            DatabaseHelper.PARTY_SORT_LATEST_TXN, DatabaseHelper.PARTY_SORT_LATEST_TXN,
            DatabaseHelper.PARTY_SORT_BALANCE, DatabaseHelper.PARTY_SORT_BALANCE,
            DatabaseHelper.PARTY_SORT_NAME, DatabaseHelper.PARTY_SORT_NAME
        };

        boolean[] ascendings = {false, true, false, true, true, false};

        for (int i = 0; i < buttons.length; i++) {

            if (sorts[i] == sort && ascendings[i] == ascending) {

                buttons[i].setBackgroundResource(R.drawable.bg_button_primary);
                buttons[i].setTextColor(getResources().getColor(R.color.text_on_primary));

            } else {

                buttons[i].setBackgroundResource(R.drawable.bg_button_outline);
                buttons[i].setTextColor(getResources().getColor(R.color.primary));
            }
        }

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
