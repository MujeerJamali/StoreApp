package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class Additemactivity extends Activity {

    EditText et_item_name;
    EditText et_purchase_price;
    EditText et_sale_price;

    Button btn_save_item;

    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.additemactivityv);

        et_item_name = findViewById(R.id.et_item_name);
        et_purchase_price = findViewById(R.id.et_purchase_price);
        et_sale_price = findViewById(R.id.et_sale_price);

        btn_save_item = findViewById(R.id.btn_save_item);

        db = new DatabaseHelper(this);

        // Lets a caller (e.g. the item picker in Transactioneditactivity)
        // pre-fill the name when the user typed something that didn't
        // match any existing item, so they don't have to retype it here.
        String prefillName = getIntent().getStringExtra("item_name");

        if (prefillName != null && !prefillName.trim().isEmpty()) {

            et_item_name.setText(prefillName.trim());
            et_item_name.setSelection(et_item_name.getText().length());
        }

        btn_save_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String name = et_item_name.getText().toString().trim();
					String purchase = et_purchase_price.getText().toString().trim();
					String sale = et_sale_price.getText().toString().trim();

					if (name.isEmpty() || purchase.isEmpty() || sale.isEmpty()) {

						Toast.makeText(
                            Additemactivity.this,
                            "Fill all fields",
                            Toast.LENGTH_SHORT
						).show();

						return;
					}

					double purchasePrice = Double.parseDouble(purchase);
					double salePrice = Double.parseDouble(sale);

					long result = db.insertItem(
                        name,
                        purchasePrice,
                        salePrice
					);

					if (result != -1) {

						Toast.makeText(
                            Additemactivity.this,
                            "Item saved",
                            Toast.LENGTH_SHORT
						).show();

						Intent data = new Intent();
						data.putExtra("item_id", (int) result);
						data.putExtra("item_name", name);
						setResult(RESULT_OK, data);

						finish();

					} else {

						Toast.makeText(
                            Additemactivity.this,
                            "Item already exists",
                            Toast.LENGTH_SHORT
						).show();
					}
				}
			});
    }
}
