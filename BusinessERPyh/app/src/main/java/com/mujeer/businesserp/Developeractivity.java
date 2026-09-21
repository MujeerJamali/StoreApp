package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

public class Developeractivity extends Activity {

    EditText et_party_name;
    Button btn_save_party;
    ListView lv_parties;

    DatabaseHelper db;
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.developeractivity);

        et_party_name = findViewById(R.id.et_party_name);
        btn_save_party = findViewById(R.id.btn_save_party);
        lv_parties = findViewById(R.id.lv_parties);

        db = new DatabaseHelper(this);

        loadParties();

        btn_save_party.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String name = et_party_name.getText().toString().trim();

					if (name.isEmpty()) {
						Toast.makeText(
                            Developeractivity.this,
                            "Enter party name",
                            Toast.LENGTH_SHORT
						).show();
						return;
					}

					long result = db.insertParty(name);

					if (result != -1) {

						Toast.makeText(
                            Developeractivity.this,
                            "Party saved",
                            Toast.LENGTH_SHORT
						).show();

						et_party_name.setText("");

						loadParties();

					} else {

						Toast.makeText(
                            Developeractivity.this,
                            "Party already exists",
                            Toast.LENGTH_SHORT
						).show();
					}
				}
			});
    }

    private void loadParties() {

        adapter = new ArrayAdapter<>(
			this,
			R.layout.party_view,
			R.id.tv_party_name,
			db.getParties()
        );

        lv_parties.setAdapter(adapter);
    }
}
