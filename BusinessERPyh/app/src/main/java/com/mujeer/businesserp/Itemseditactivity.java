package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class Itemseditactivity extends Activity {

    EditText et_item_name;
    EditText et_purchase_price;
    EditText et_sale_price;
    TextView tv_item_stock;

    Button btn_update_item;

    DatabaseHelper db;

    int itemId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.itemseditactivityv);

        et_item_name = findViewById(R.id.et_item_name);
        et_purchase_price = findViewById(R.id.et_purchase_price);
        et_sale_price = findViewById(R.id.et_sale_price);
        tv_item_stock = findViewById(R.id.tv_item_stock);

        btn_update_item = findViewById(R.id.btn_update_item);

        db = new DatabaseHelper(this);

        itemId = getIntent().getIntExtra("item_id", -1);

        if (itemId != -1) {

            HashMap<String, Object> item = db.getItemById(itemId);

            et_item_name.setText((String) item.get("name"));
            et_purchase_price.setText(String.valueOf(item.get("purchase_price")));
            et_sale_price.setText(String.valueOf(item.get("sale_price")));

            double stock = 0;

            if (item.get("balance") != null) {
                stock = (Double) item.get("balance");
            }

            tv_item_stock.setText(String.valueOf(stock));

            if (stock <= 0) {
                tv_item_stock.setTextColor(getResources().getColor(R.color.danger));
            } else {
                tv_item_stock.setTextColor(getResources().getColor(R.color.mod_items));
            }
        }

        btn_update_item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String name = et_item_name.getText().toString().trim();
					String purchase = et_purchase_price.getText().toString().trim();
					String sale = et_sale_price.getText().toString().trim();

					if (name.isEmpty() || purchase.isEmpty() || sale.isEmpty()) {

						Toast.makeText(
                            Itemseditactivity.this,
                            "Fill all fields",
                            Toast.LENGTH_SHORT
						).show();

						return;
					}

					double purchasePrice = Double.parseDouble(purchase);
					double salePrice = Double.parseDouble(sale);

					if (db.updateItem(
							itemId,
							name,
							purchasePrice,
							salePrice
						)) {

						Toast.makeText(
                            Itemseditactivity.this,
                            "Item updated",
                            Toast.LENGTH_SHORT
						).show();

						finish();

					} else {

						Toast.makeText(
                            Itemseditactivity.this,
                            "Update failed",
                            Toast.LENGTH_SHORT
						).show();
					}
				}
			});
    }
}
