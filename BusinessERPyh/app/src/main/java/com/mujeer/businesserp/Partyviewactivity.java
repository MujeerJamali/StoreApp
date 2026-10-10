package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class Partyviewactivity extends Activity {

    TextView tv_party_name;
    TextView tv_party_customer_type;
    TextView tv_party_balance;
    TextView tv_party_loyalty_points;
    TextView tv_adjust_loyalty_points;
    View container_party_appearance;
    TextView tv_party_appearance;
    TextView tv_transactions_empty;
    Button btn_edit_party, btn_delete_party;

    ListView lv_transactions;

    ArrayList<HashMap<String, Object>> transactionList;

    TransactionAdapter transactionAdapter;

    DatabaseHelper db;

    int partyId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.partyviewactivity);

        tv_party_name = findViewById(R.id.tv_party_name);
        tv_party_customer_type = findViewById(R.id.tv_party_customer_type);
        tv_party_balance = findViewById(R.id.tv_party_balance);
        tv_party_loyalty_points = findViewById(R.id.tv_party_loyalty_points);
        tv_adjust_loyalty_points = findViewById(R.id.tv_adjust_loyalty_points);
        container_party_appearance = findViewById(R.id.container_party_appearance);
        tv_party_appearance = findViewById(R.id.tv_party_appearance);
        tv_transactions_empty = findViewById(R.id.tv_transactions_empty);
        btn_edit_party = findViewById(R.id.btn_edit_party);
        btn_delete_party = findViewById(R.id.btn_delete_party);

        lv_transactions = findViewById(R.id.lv_transactions);

        db = new DatabaseHelper(this);

        transactionList = new ArrayList<HashMap<String, Object>>();

        partyId = getIntent().getIntExtra("party_id", -1);

        loadParty();
        loadTransactions();

        lv_transactions.setOnItemClickListener(
            new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {

                    HashMap<String, Object> transaction =
                        (HashMap<String, Object>) transactionAdapter.getItem(position);

                    int transactionType = Integer.parseInt(
                        transaction.get("transaction_type").toString()
                    );

                    int transactionId = Integer.parseInt(
                        transaction.get("id").toString()
                    );

                    Intent intent;

                    if (transactionType == DatabaseHelper.TRANSACTION_TYPE_PAYMENT_IN
                        || transactionType == DatabaseHelper.TRANSACTION_TYPE_PAYMENT_OUT) {

                        intent = new Intent(
                            Partyviewactivity.this,
                            Paymentviewactivity.class
                        );

                        intent.putExtra("payment_id", transactionId);

                    } else if (transactionType == DatabaseHelper.TRANSACTION_TYPE_EXPENSE) {

                        intent = new Intent(
                            Partyviewactivity.this,
                            Expenseviewactivity.class
                        );

                        intent.putExtra("expense_id", transactionId);

                    } else {

                        intent = new Intent(
                            Partyviewactivity.this,
                            Transactionviewactivity.class
                        );

                        intent.putExtra("transaction_type", transactionType);
                        intent.putExtra("transaction_id", transactionId);
                    }

                    startActivity(intent);
                }
            }
        );

        tv_adjust_loyalty_points.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					promptAdjustLoyaltyPoints();
				}
			});

        btn_edit_party.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
                        Partyviewactivity.this,
                        Partieseditactivity.class
					);

					intent.putExtra("party_id", partyId);

					startActivity(intent);
				}
			});

        btn_delete_party.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					new AlertDialog.Builder(Partyviewactivity.this)
                        .setTitle("Delete Party")
                        .setMessage("Are you sure you want to delete this party?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                if (db.deleteParty(partyId)) {

                                    Toast.makeText(
										Partyviewactivity.this,
										"Party deleted",
										Toast.LENGTH_SHORT
                                    ).show();

                                    finish();

                                } else {

                                    Toast.makeText(
										Partyviewactivity.this,
										"Delete failed",
										Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
				}
			});
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadParty();
        loadTransactions();
    }

    private void loadTransactions() {

        if (partyId == -1) {
            return;
        }

        transactionList.clear();

        transactionList.addAll(
            db.getTransactionsByParty(partyId)
        );

        transactionAdapter = new TransactionAdapter(
            this,
            transactionList
        );

        lv_transactions.setAdapter(transactionAdapter);
        lv_transactions.setEmptyView(tv_transactions_empty);
    }

    private void promptAdjustLoyaltyPoints() {

        final EditText input = new EditText(this);
        input.setHint("e.g. 50 or -50");
        input.setInputType(
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
        );

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle("Adjust Loyalty Points")
            .setMessage(
                "A positive number adds points (e.g. a goodwill credit), " +
                "a negative number removes them (e.g. a redemption)."
            )
            .setView(input)
            .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        int delta;

                        try {
                            delta = Integer.parseInt(input.getText().toString().trim());
                        } catch (Exception e) {
                            Toast.makeText(
                                Partyviewactivity.this,
                                "Enter a valid whole number",
                                Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        if (delta == 0) {
                            return;
                        }

                        db.adjustLoyaltyPoints(
                            partyId, delta,
                            (delta > 0 ? "Manual credit" : "Manual redemption") + " (+/-" +
                            Math.abs(delta) + ")"
                        );

                        loadParty();
                    }
                })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void loadParty() {

        if (partyId != -1) {

            tv_party_name.setText(db.getPartyById(partyId));

            String customerType = db.getPartyCustomerType(partyId);

            if (DatabaseHelper.CUSTOMER_TYPE_WHOLESALE.equals(customerType)) {

                tv_party_customer_type.setText("Wholesale");
                tv_party_customer_type.setVisibility(View.VISIBLE);

            } else if (DatabaseHelper.CUSTOMER_TYPE_ONE_TIME.equals(customerType)) {

                tv_party_customer_type.setText("One-Time");
                tv_party_customer_type.setVisibility(View.VISIBLE);

            } else {

                tv_party_customer_type.setVisibility(View.GONE);
            }

            tv_party_loyalty_points.setText(
                String.valueOf(db.getLoyaltyPointsBalance(partyId)) + " points"
            );

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

            String appearanceNotes = db.getPartyAppearanceNotes(partyId);

            if (appearanceNotes != null && !appearanceNotes.isEmpty()) {
                tv_party_appearance.setText(appearanceNotes);
                container_party_appearance.setVisibility(View.VISIBLE);
            } else {
                container_party_appearance.setVisibility(View.GONE);
            }
        }
    }
}
