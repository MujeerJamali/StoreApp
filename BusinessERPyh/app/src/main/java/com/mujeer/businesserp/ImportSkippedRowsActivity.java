package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

// =====================
// Shows the rows that were left out of the previous "Import Parties,
// Purchases, Sales" run - duplicates, unsupported transaction types, and
// rows with a missing/unreadable date - each with the reason, so it's
// easy to see exactly what to fix or ignore. Reads
// Importexcelactivity.lastSkippedRows, which is only kept in memory for
// the run that just finished.
// =====================
public class ImportSkippedRowsActivity extends Activity {

    private TextView tv_summary;
    private TextView tv_empty;
    private ListView lv_rows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_skipped_rows);

        tv_summary = (TextView) findViewById(R.id.tv_skipped_summary);
        tv_empty = (TextView) findViewById(R.id.tv_skipped_empty);
        lv_rows = (ListView) findViewById(R.id.lv_skipped_rows);

        ArrayList<Importexcelactivity.SkippedRow> rows =
            Importexcelactivity.lastSkippedRows;

        if (rows == null || rows.isEmpty()) {

            tv_summary.setText("Rows not imported (0)");
            tv_empty.setVisibility(android.view.View.VISIBLE);
            lv_rows.setVisibility(android.view.View.GONE);

            return;
        }

        tv_summary.setText("Rows not imported (" + rows.size() + ")");

        ArrayList<String> lines = new ArrayList<String>();

        for (Importexcelactivity.SkippedRow row : rows) {
            lines.add(row.toDisplayString());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this,
            R.layout.skipped_row_view,
            R.id.tv_skipped_row,
            lines
        );

        lv_rows.setAdapter(adapter);
    }
}
