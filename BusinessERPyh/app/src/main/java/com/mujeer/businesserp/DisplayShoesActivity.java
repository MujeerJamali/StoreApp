package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// A free-form grid the user arranges by hand to mirror the physical
// shelf - each cell either holds one shoe reference ("Code - Size") or
// is empty. Placing a shoe here is a pure reference overlay: it never
// reserves/removes stock. Grid dimensions (row/column count) are a
// display preference, not data, so they live in SharedPreferences
// rather than the database - see PREFS_NAME below. Landscape-locked
// via AndroidManifest (a shelf mirrors better wide than tall).
//
// Interchangeable-entry removal: since two entries can share the same
// item+size (this shop may display more than one physical unit of the
// same model/size at once), removing "the display one" for a sale just
// deletes any ONE matching row - it doesn't matter structurally WHICH
// one, since every row for the same item+combo is identical and
// interchangeable; only the count at that item+size matters. See
// DatabaseHelper.removeOneDisplayShoeForCombo() and
// Transactioneditactivity's post-sale hook.
// =====================
public class DisplayShoesActivity extends Activity {

	private static final String PREFS_NAME = "display_shoes_grid";
	private static final String PREF_ROWS = "rows";
	private static final String PREF_COLS = "cols";

	private static final int DEFAULT_ROWS = 5;
	private static final int DEFAULT_COLS = 6;

	private Button btn_add_row;
	private Button btn_remove_row;
	private Button btn_add_column;
	private Button btn_remove_column;
	private LinearLayout container_grid;

	private DatabaseHelper db;

	private int gridRows;
	private int gridCols;

	// Keyed by "row,col" -> the entry occupying that cell, rebuilt on
	// every load.
	private final HashMap<String, HashMap<String, Object>> cellsByPosition =
		new HashMap<String, HashMap<String, Object>>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.display_shoes_activity);

		btn_add_row = findViewById(R.id.btn_add_row);
		btn_remove_row = findViewById(R.id.btn_remove_row);
		btn_add_column = findViewById(R.id.btn_add_column);
		btn_remove_column = findViewById(R.id.btn_remove_column);
		container_grid = findViewById(R.id.container_grid);

		db = new DatabaseHelper(this);

		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		gridRows = prefs.getInt(PREF_ROWS, DEFAULT_ROWS);
		gridCols = prefs.getInt(PREF_COLS, DEFAULT_COLS);

		btn_add_row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					gridRows++;
					saveGridSize();
					renderGrid();
				}
			});

		btn_remove_row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmRemoveRow();
				}
			});

		btn_add_column.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					gridCols++;
					saveGridSize();
					renderGrid();
				}
			});

		btn_remove_column.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmRemoveColumn();
				}
			});

		loadEntries();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadEntries();
	}

	private void saveGridSize() {

		getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
			.edit()
			.putInt(PREF_ROWS, gridRows)
			.putInt(PREF_COLS, gridCols)
			.apply();
	}

	private void confirmRemoveRow() {

		if (gridRows <= 1) {

			Toast.makeText(this, "Can't remove the last row", Toast.LENGTH_SHORT).show();
			return;
		}

		final int rowToRemove = gridRows - 1;

		if (!rowHasEntries(rowToRemove)) {
			removeRow(rowToRemove);
			return;
		}

		new AlertDialog.Builder(this)
			.setTitle("Remove Row")
			.setMessage("This row has shoes on it. Remove the row and everything on it?")
			.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeRow(rowToRemove);
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void confirmRemoveColumn() {

		if (gridCols <= 1) {

			Toast.makeText(this, "Can't remove the last column", Toast.LENGTH_SHORT).show();
			return;
		}

		final int colToRemove = gridCols - 1;

		if (!columnHasEntries(colToRemove)) {
			removeColumn(colToRemove);
			return;
		}

		new AlertDialog.Builder(this)
			.setTitle("Remove Column")
			.setMessage("This column has shoes on it. Remove the column and everything on it?")
			.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeColumn(colToRemove);
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private boolean rowHasEntries(int row) {

		for (int col = 0; col < gridCols; col++) {
			if (cellsByPosition.containsKey(row + "," + col)) {
				return true;
			}
		}

		return false;
	}

	private boolean columnHasEntries(int col) {

		for (int row = 0; row < gridRows; row++) {
			if (cellsByPosition.containsKey(row + "," + col)) {
				return true;
			}
		}

		return false;
	}

	private void removeRow(final int row) {

		new Thread(new Runnable() {
				@Override
				public void run() {

					db.removeDisplayGridRow(row);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								gridRows--;
								saveGridSize();
								loadEntries();
							}
						});
				}
			}).start();
	}

	private void removeColumn(final int col) {

		new Thread(new Runnable() {
				@Override
				public void run() {

					db.removeDisplayGridColumn(col);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								gridCols--;
								saveGridSize();
								loadEntries();
							}
						});
				}
			}).start();
	}

	private void loadEntries() {

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result = db.getDisplayShoes();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (isFinishing()) {
									return;
								}

								cellsByPosition.clear();

								int maxRow = gridRows - 1;
								int maxCol = gridCols - 1;

								for (HashMap<String, Object> entry : result) {

									int row = (Integer) entry.get("row_pos");
									int col = (Integer) entry.get("col_pos");

									cellsByPosition.put(row + "," + col, entry);

									maxRow = Math.max(maxRow, row);
									maxCol = Math.max(maxCol, col);
								}

								// A restored backup (or data from a device with a
								// larger grid) can carry positions past the locally
								// remembered grid size - grow to fit rather than
								// hiding those entries.
								if (maxRow + 1 != gridRows || maxCol + 1 != gridCols) {

									gridRows = maxRow + 1;
									gridCols = maxCol + 1;
									saveGridSize();
								}

								renderGrid();
							}
						});
				}
			}).start();
	}

	private void renderGrid() {

		container_grid.removeAllViews();

		float density = getResources().getDisplayMetrics().density;
		int cellWidth = (int) (100 * density);
		int cellHeight = (int) (56 * density);
		int cellMargin = (int) (4 * density);

		for (int row = 0; row < gridRows; row++) {

			LinearLayout rowLayout = new LinearLayout(this);
			rowLayout.setOrientation(LinearLayout.HORIZONTAL);

			for (int col = 0; col < gridCols; col++) {

				final int finalRow = row;
				final int finalCol = col;

				final HashMap<String, Object> entry = cellsByPosition.get(row + "," + col);

				Button cell = new Button(this);

				LinearLayout.LayoutParams params =
					new LinearLayout.LayoutParams(cellWidth, cellHeight);

				params.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);
				cell.setLayoutParams(params);

				cell.setMinWidth(0);
				cell.setMinHeight(0);
				cell.setMinimumWidth(0);
				cell.setMinimumHeight(0);
				cell.setPadding(4, 4, 4, 4);
				cell.setGravity(Gravity.CENTER);
				cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
				cell.setAllCaps(false);

				if (entry != null) {

					String code = String.valueOf(entry.get("code"));
					String comboLabel = String.valueOf(entry.get("combo_label"));

					cell.setText(code + " - " + comboLabel);
					cell.setBackgroundResource(R.drawable.bg_button_primary);
					cell.setTextColor(getResources().getColor(R.color.text_on_primary));

					cell.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								confirmRemoveEntry(entry);
							}
						});

				} else {

					cell.setText("+");
					cell.setBackgroundResource(R.drawable.bg_button_outline);
					cell.setTextColor(getResources().getColor(R.color.primary));

					cell.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								addAt(finalRow, finalCol);
							}
						});
				}

				rowLayout.addView(cell);
			}

			container_grid.addView(rowLayout);
		}
	}

	private void addAt(final int row, final int col) {

		ShoeBoardPicker.show(this, db, new ShoeBoardPicker.OnPickedListener() {
				@Override
				public void onPicked(int itemId, String itemCode, int comboId, String comboLabel) {

					new Thread(new Runnable() {
							@Override
							public void run() {

								db.addDisplayShoe(itemId, comboId, row, col);

								runOnUiThread(new Runnable() {
										@Override
										public void run() {
											loadEntries();
										}
									});
							}
						}).start();
				}
			});
	}

	private void confirmRemoveEntry(final HashMap<String, Object> entry) {

		String code = String.valueOf(entry.get("code"));
		String comboLabel = String.valueOf(entry.get("combo_label"));

		new AlertDialog.Builder(this)
			.setTitle(code + " - " + comboLabel)
			.setMessage("Remove this from the display?")
			.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						final int id = (Integer) entry.get("id");

						new Thread(new Runnable() {
								@Override
								public void run() {

									db.removeDisplayShoeById(id);

									runOnUiThread(new Runnable() {
											@Override
											public void run() {
												loadEntries();
											}
										});
								}
							}).start();
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}
}
