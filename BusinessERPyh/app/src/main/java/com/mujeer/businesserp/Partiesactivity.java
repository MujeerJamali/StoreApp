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
    Button btn_delete_all_parties;

    ListView lv_parties;

    DatabaseHelper db;

    ArrayList<HashMap<String, Object>> partyList;

		PartyAdapter adapter;

		@Override
		protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.partiesactivityv);

        et_search_party = findViewById(R.id.et_search_party);
        tv_no_parties = findViewById(R.id.tv_no_parties);

        btn_add_party = findViewById(R.id.btn_add_party);
        btn_delete_all_parties = findViewById(R.id.btn_delete_all_parties);

        lv_parties = findViewById(R.id.lv_parties);

				lv_parties.setEmptyView(tv_no_parties);

				db = new DatabaseHelper(this);

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

						btn_delete_all_parties.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

					new AlertDialog.Builder(Partiesactivity.this)
                        .setTitle("Delete All Parties")
                        .setMessage("Are you sure you want to delete all parties?")
					.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

					db.deleteAllParties();

			loadParties();

	Toast.makeText(
	Partiesactivity.this,
	"All parties deleted",
		Toast.LENGTH_SHORT
		).show();
	}
	})
	.setNegativeButton("Cancel", null)
		.show();
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

    private void loadParties() {

        partyList = db.getParties();

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
