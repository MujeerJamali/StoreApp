package com.mujeer.businesserp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

// A small dependency-free pie/donut chart for a "share of a whole"
// breakdown (e.g. Cash Sale vs Party, Shoes vs Non-Shoes) - same plain-
// Canvas convention as SimpleBarChartView, no charting library (see
// that class's own comment for why). Reuses SimpleBarChartView.Entry
// (label/value/color) rather than a parallel class, since every
// existing caller already builds that type for the bar chart.
//
// Only non-negative entries make sense as a share of a whole, so a
// negative value is simply dropped rather than rendered - a caller
// with genuinely signed data (a profit/loss swing, say) should use
// SimpleBarChartView instead, not this view.
public class SimplePieChartView extends View {

	private List<SimpleBarChartView.Entry> entries = new ArrayList<SimpleBarChartView.Entry>();

	private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint legendSwatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint centerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	public SimplePieChartView(Context context) {
		super(context);
		init();
	}

	public SimplePieChartView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {

		float density = getResources().getDisplayMetrics().density;

		holePaint.setColor(Color.parseColor("#FFFFFF"));

		legendSwatchPaint.setStyle(Paint.Style.FILL);

		legendTextPaint.setColor(Color.parseColor("#1F2937"));
		legendTextPaint.setTextSize(12 * density);
		legendTextPaint.setTextAlign(Paint.Align.LEFT);

		centerTextPaint.setColor(Color.parseColor("#6B7280"));
		centerTextPaint.setTextSize(11 * density);
		centerTextPaint.setTextAlign(Paint.Align.CENTER);
	}

	// Entries with a negative or zero value are dropped before storing -
	// onDraw() below can then just assume everything left is a real,
	// positive slice. Left with fewer than 2 slices (one category, or
	// everything zero), there is nothing meaningful to compare shares
	// of, so this renders nothing at all rather than a misleading full
	// circle or an empty one - the view's own version of this feature's
	// "skip a chart that doesn't fit the data" rule.
	public void setEntries(List<SimpleBarChartView.Entry> rawEntries) {

		List<SimpleBarChartView.Entry> positive = new ArrayList<SimpleBarChartView.Entry>();

		if (rawEntries != null) {

			for (SimpleBarChartView.Entry e : rawEntries) {

				if (e.value > 0) {
					positive.add(e);
				}
			}
		}

		this.entries = positive.size() >= 2 ? positive : new ArrayList<SimpleBarChartView.Entry>();

		requestLayout();
		invalidate();
	}

	public boolean hasChartableData() {
		return !entries.isEmpty();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		float density = getResources().getDisplayMetrics().density;

		float legendRowHeight = 20 * density;
		float pieDiameter = 120 * density;
		float topPadding = 8 * density;
		float bottomPadding = 8 * density;

		int desiredHeight = entries.isEmpty() ?
			0 :
			(int) (topPadding + pieDiameter + bottomPadding + entries.size() * legendRowHeight);

		int width = MeasureSpec.getSize(widthMeasureSpec);

		setMeasuredDimension(width, desiredHeight);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (entries.isEmpty()) {
			return;
		}

		float density = getResources().getDisplayMetrics().density;

		int width = getWidth();

		float topPadding = 8 * density;
		float pieDiameter = 120 * density;
		float holeDiameter = pieDiameter * 0.55f;

		double total = 0;

		for (SimpleBarChartView.Entry e : entries) {
			total += e.value;
		}

		float cx = width / 2f;
		float cy = topPadding + pieDiameter / 2f;

		RectF pieRect = new RectF(
			cx - pieDiameter / 2f, cy - pieDiameter / 2f,
			cx + pieDiameter / 2f, cy + pieDiameter / 2f
		);

		float startAngle = -90f;

		for (SimpleBarChartView.Entry e : entries) {

			float sweep = (float) (e.value / total * 360.0);

			slicePaint.setColor(e.color);
			canvas.drawArc(pieRect, startAngle, sweep, true, slicePaint);

			startAngle += sweep;
		}

		// Donut hole, so the chart doesn't read as a plain filled circle
		// (visually lighter, and leaves room for "N items" in the middle).
		canvas.drawCircle(cx, cy, holeDiameter / 2f, holePaint);

		canvas.drawText(
			entries.size() + (entries.size() == 1 ? " item" : " items"),
			cx, cy + (4 * density), centerTextPaint
		);

		float legendY = topPadding + pieDiameter + 20 * density;
		float legendRowHeight = 20 * density;
		float swatchSize = 10 * density;
		float legendLeft = 12 * density;

		for (SimpleBarChartView.Entry e : entries) {

			legendSwatchPaint.setColor(e.color);

			canvas.drawRect(
				legendLeft, legendY - swatchSize, legendLeft + swatchSize, legendY, legendSwatchPaint
			);

			double percent = total > 0 ? (e.value / total * 100.0) : 0;

			String line = e.label + " - " + AmountFormat.format(e.value) +
				" (" + AmountFormat.formatPlain(percent) + "%)";

			canvas.drawText(line, legendLeft + swatchSize + 6 * density, legendY, legendTextPaint);

			legendY += legendRowHeight;
		}
	}
}
