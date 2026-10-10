package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class Partieseditactivity extends Activity {

    EditText et_party_name;
    EditText et_party_appearance;
    Spinner spinner_customer_type;
    TextView tv_party_balance;
    Button btn_update_party;

    DatabaseHelper db;

    int partyId = -1;
    String originalName = "";

    private static final String[] CUSTOMER_TYPE_VALUES = {
        DatabaseHelper.CUSTOMER_TYPE_REGULAR,
        DatabaseHelper.CUSTOMER_TYPE_ONE_TIME,
        DatabaseHelper.CUSTOMER_TYPE_WHOLESALE
    };

    private static final String[] CUSTOMER_TYPE_LABELS = {
        "Regular", "One-Time", "Wholesale"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.partieseditactivityv);

        et_party_name = findViewById(R.id.et_party_name);
        et_party_appearance = findViewById(R.id.et_party_appearance);
        spinner_customer_type = findViewById(R.id.spinner_customer_type);
        tv_party_balance = findViewById(R.id.tv_party_balance);
        btn_update_party = findViewById(R.id.btn_update_party);

        db = new DatabaseHelper(this);

        ArrayAdapter<String> customerTypeAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, CUSTOMER_TYPE_LABELS
        );

        customerTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_customer_type.setAdapter(customerTypeAdapter);

        partyId = getIntent().getIntExtra("party_id", -1);

        if (partyId != -1) {

            originalName = db.getPartyById(partyId);
            et_party_name.setText(originalName);
            et_party_appearance.setText(db.getPartyAppearanceNotes(partyId));

            String customerType = db.getPartyCustomerType(partyId);

            for (int i = 0; i < CUSTOMER_TYPE_VALUES.length; i++) {

                if (CUSTOMER_TYPE_VALUES[i].equals(customerType)) {
                    spinner_customer_type.setSelection(i);
                    break;
                }
            }

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

						db.updatePartyCustomerType(
							partyId, CUSTOMER_TYPE_VALUES[spinner_customer_type.getSelectedItemPosition()]
						);

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
