package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
// Shows every cash-affecting transaction (the paid portion of a sale/
// purchase/expense, a payment, or a manual adjustment) as a plain
// running list of dates and signed amounts - not full transaction
// detail, just how much cash moved and when - plus the current cash-
// in-hand total and a way to record a manual adjustment (e.g.
// reconciling against a physical till count). See
// DatabaseHelper.getCashBalance()/getCashLedger() for what counts as
// "cash" here.
//
// Both the balance and the ledger are computed with a handful of SQL
// SUM/UNION queries - cheap at this app's data volume, but still run on
// a background thread (not the main thread) so a large history never
// risks a hang/ANR on this screen.
// =====================
public class CashActivity extends Activity {

    private TextView tvCashBalance;
    private TextView tvEmpty;
    private ListView lvLedger;
    private Button btnAddAdjustment;

    private DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cash_activity);

        tvCashBalance = findViewById(R.id.tv_cash_balance);
        tvEmpty = findViewById(R.id.tv_cash_empty);
        lvLedger = findViewById(R.id.lv_cash_ledger);
        btnAddAdjustment = findViewById(R.id.btn_add_adjustment);

        db = new DatabaseHelper(this);

        btnAddAdjustment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptAddAdjustment();
            }
        });

        loadCash();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCash();
    }

    private void loadCash() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                final double balance = db.getCashBalance();
                final ArrayList<HashMap<String, Object>> ledger = db.getCashLedger();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        tvCashBalance.setText(
                            AmountFormat.format(balance)
                        );

                        tvCashBalance.setTextColor(
                            getResources().getColor(
                                balance < 0 ? R.color.danger : R.color.text_primary
                            )
                        );

                        lvLedger.setAdapter(new CashLedgerAdapter(CashActivity.this, ledger));
                        lvLedger.setEmptyView(tvEmpty);
                    }
                });
            }
        }).start();
    }

    private void promptAddAdjustment() {

        View view = getLayoutInflater().inflate(R.layout.dialog_cash_adjustment, null);

        final EditText etAmount = view.findViewById(R.id.et_adjustment_amount);
        final EditText etNotes = view.findViewById(R.id.et_adjustment_notes);

        new AlertDialog.Builder(this)
            .setTitle("Add Cash Adjustment")
            .setMessage(
                "Positive to add cash (e.g. found extra in the till), " +
                "negative to remove it."
            )
            .setView(view)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    saveAdjustment(etAmount, etNotes);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void saveAdjustment(EditText etAmount, EditText etNotes) {

        double amount;

        try {

            amount = Double.parseDouble(etAmount.getText().toString().trim());

        } catch (Exception e) {

            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount == 0) {

            Toast.makeText(this, "Amount cannot be 0", Toast.LENGTH_SHORT).show();
            return;
        }

        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        db.insertCashAdjustment(date, time, amount, etNotes.getText().toString().trim());

        Toast.makeText(this, "Adjustment saved", Toast.LENGTH_SHORT).show();

        loadCash();
    }
}
