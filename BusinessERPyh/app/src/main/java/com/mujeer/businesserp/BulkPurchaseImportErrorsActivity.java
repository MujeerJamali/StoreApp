package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

// =====================
// Shows every validation error found in the last Bulk Purchase Import
// attempt - each with its row number, column, and description - so the
// user can see exactly what to fix in the Excel file before trying
// again. Reads BulkPurchaseImportActivity.lastErrors, which is only
// kept in memory for the run that just finished (same convention as
// ImportSkippedRowsActivity / Importexcelactivity.lastSkippedRows).
// =====================
public class BulkPurchaseImportErrorsActivity extends Activity {

	private TextView tv_summary;
	private ListView lv_errors;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bulk_purchase_import_errors);

		tv_summary = (TextView) findViewById(R.id.tv_bulk_import_errors_summary);
		lv_errors = (ListView) findViewById(R.id.lv_bulk_import_errors);

		ArrayList<BulkPurchaseImportActivity.ImportError> errors =
			BulkPurchaseImportActivity.lastErrors;

		if (errors == null) {
			errors = new ArrayList<BulkPurchaseImportActivity.ImportError>();
		}

		tv_summary.setText("Validation errors (" + errors.size() + ")");

		ArrayList<String> lines = new ArrayList<String>();

		for (BulkPurchaseImportActivity.ImportError error : errors) {
			lines.add(error.toDisplayString());
		}

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(
			this,
			R.layout.bulk_purchase_import_error_row,
			R.id.tv_bulk_import_error_row,
			lines
		);

		lv_errors.setAdapter(adapter);
	}
}
