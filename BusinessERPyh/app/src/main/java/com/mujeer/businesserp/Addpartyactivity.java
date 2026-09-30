package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class Addpartyactivity extends Activity {

    EditText et_party_name;
    Button btn_save_party;

    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addpartyactivityv);

        et_party_name = findViewById(R.id.et_party_name);
        btn_save_party = findViewById(R.id.btn_save_party);

        db = new DatabaseHelper(this);

        // Lets a caller (e.g. the party picker in Transactioneditactivity/
        // Paymenteditactivity) pre-fill the name when the user typed
        // something that didn't match any existing party, so they don't
        // have to retype it here - same pattern as Additemactivity.
        String prefillName = getIntent().getStringExtra("party_name");

        if (prefillName != null && !prefillName.trim().isEmpty()) {

            et_party_name.setText(prefillName.trim());
            et_party_name.setSelection(et_party_name.getText().length());
        }

        btn_save_party.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String name = et_party_name.getText().toString().trim();

					if (name.isEmpty()) {
						Toast.makeText(
                            Addpartyactivity.this,
                            "Enter party name",
                            Toast.LENGTH_SHORT
						).show();
						return;
					}

					long result = db.insertParty(name);

					if (result != -1) {

						Toast.makeText(
                            Addpartyactivity.this,
                            "Party saved",
                            Toast.LENGTH_SHORT
						).show();

						Intent data = new Intent();
						data.putExtra("party_id", (int) result);
						data.putExtra("party_name", name);
						setResult(RESULT_OK, data);

						finish();

					} else {

						Toast.makeText(
                            Addpartyactivity.this,
                            "Party already exists",
                            Toast.LENGTH_SHORT
						).show();
					}
				}
			});
    }
}
