package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class Importexcelactivity extends Activity {

    private Button btn_import_vyapar;
    private Button btn_export_vyapar;
    private Button btn_bulk_purchase_import;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_excel);

        btn_import_vyapar = (Button) findViewById(R.id.btn_import_vyapar);
        btn_export_vyapar = (Button) findViewById(R.id.btn_export_vyapar);
        btn_bulk_purchase_import = (Button) findViewById(R.id.btn_bulk_purchase_import);

        // Opens the Vyapar backup importer - a completely separate,
        // fuller-featured import path (parties/items/varieties/purchases/
        // sales/payments/transfers/expenses in one shot from a .vyb file).
        btn_import_vyapar.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						Importexcelactivity.this,
						ImportVyaparActivity.class
					));
				}
			});

        // Exports this app's own data into a .vyb file, in the same
        // format the importer above reads.
        btn_export_vyapar.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						Importexcelactivity.this,
						ExportVyaparActivity.class
					));
				}
			});

        // Opens Bulk Purchase Import with no purchase pre-selected - the
        // user picks one from the list on that screen. (When reached
        // instead from a purchase's own "Bulk Import" button, that
        // purchase comes pre-selected there.)
        btn_bulk_purchase_import.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						Importexcelactivity.this,
						BulkPurchaseImportActivity.class
					));
				}
			});
    }
}
