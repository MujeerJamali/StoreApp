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

        lvLedger.setOnItemClickListener(
            new android.widget.AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(
                    android.widget.AdapterView<?> parent, View view, int position, long id) {

                    HashMap<String, Object> row =
                        (HashMap<String, Object>) parent.getItemAtPosition(position);

                    int adjustmentId = (Integer) row.get("adjustment_id");

                    if (adjustmentId != 0) {
                        promptEditAdjustment(adjustmentId, row);
                    }
                }
            }
        );

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

    private void promptEditAdjustment(
        final int adjustmentId, HashMap<String, Object> row) {

        View view = getLayoutInflater().inflate(R.layout.dialog_cash_adjustment, null);

        final EditText etAmount = view.findViewById(R.id.et_adjustment_amount);
        final EditText etNotes = view.findViewById(R.id.et_adjustment_notes);

        etAmount.setText(
            AmountFormat.formatPlain((Double) row.get("amount"))
        );
        etNotes.setText(String.valueOf(row.get("notes")));

        final String date = String.valueOf(row.get("date"));
        final String time = String.valueOf(row.get("time"));

        new AlertDialog.Builder(this)
            .setTitle("Edit Cash Adjustment")
            .setView(view)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    updateAdjustment(adjustmentId, date, time, etAmount, etNotes);
                }
            })
            .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    confirmDeleteAdjustment(adjustmentId);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateAdjustment(
        int adjustmentId, String date, String time, EditText etAmount, EditText etNotes) {

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

        db.updateCashAdjustment(
            adjustmentId, date, time, amount, etNotes.getText().toString().trim()
        );

        Toast.makeText(this, "Adjustment updated", Toast.LENGTH_SHORT).show();

        loadCash();
    }

    private void confirmDeleteAdjustment(final int adjustmentId) {

        new AlertDialog.Builder(this)
            .setTitle("Delete Adjustment")
            .setMessage("Delete this cash adjustment? This cannot be undone.")
            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    db.deleteCashAdjustment(adjustmentId);

                    Toast.makeText(CashActivity.this, "Adjustment deleted", Toast.LENGTH_SHORT).show();

                    loadCash();
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
