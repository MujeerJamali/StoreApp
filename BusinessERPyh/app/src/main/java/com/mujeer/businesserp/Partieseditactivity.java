package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Partieseditactivity extends Activity {

    EditText et_party_name;
    EditText et_party_appearance;
    TextView tv_party_balance;
    Button btn_update_party;

    DatabaseHelper db;

    int partyId = -1;
    String originalName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.partieseditactivityv);

        et_party_name = findViewById(R.id.et_party_name);
        et_party_appearance = findViewById(R.id.et_party_appearance);
        tv_party_balance = findViewById(R.id.tv_party_balance);
        btn_update_party = findViewById(R.id.btn_update_party);

        db = new DatabaseHelper(this);

        partyId = getIntent().getIntExtra("party_id", -1);

        if (partyId != -1) {

            originalName = db.getPartyById(partyId);
            et_party_name.setText(originalName);
            et_party_appearance.setText(db.getPartyAppearanceNotes(partyId));

            double balance = db.getPartyBalance(partyId);

            if (balance > 0) {

                tv_party_balance.setText(
                    AmountFormat.format(balance) + " (Receivable)"
                );
                tv_party_balance.setTextColor(getResources().getColor(R.color.success));

            } else if (balance < 0) {

                tv_party_balance.setText(
                    AmountFormat.format(Math.abs(balance)) + " (Payable)"
                );
                tv_party_balance.setTextColor(getResources().getColor(R.color.danger));

            } else {

                tv_party_balance.setText("0 (Settled)");
                tv_party_balance.setTextColor(getResources().getColor(R.color.text_secondary));
            }
        }

        btn_update_party.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String name = et_party_name.getText().toString().trim();

					if (name.isEmpty()) {
						Toast.makeText(
                            Partieseditactivity.this,
                            "Enter party name",
                            Toast.LENGTH_SHORT
						).show();
						return;
					}

					if (!name.equals(originalName) && db.partyExists(name)) {
						Toast.makeText(
                            Partieseditactivity.this,
                            "Party already exists",
                            Toast.LENGTH_SHORT
						).show();
						return;
					}

					String appearanceNotes = et_party_appearance.getText().toString().trim();

					if (db.updateParty(partyId, name, appearanceNotes)) {

						Toast.makeText(
                            Partieseditactivity.this,
                            "Party updated",
                            Toast.LENGTH_SHORT
						).show();

						finish();

					} else {

						Toast.makeText(
                            Partieseditactivity.this,
                            "Update failed",
                            Toast.LENGTH_SHORT
						).show();
					}
				}
			});
    }
}
