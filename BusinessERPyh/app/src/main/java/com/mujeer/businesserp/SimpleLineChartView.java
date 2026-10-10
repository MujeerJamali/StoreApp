package com.mujeer.businesserp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

// A small dependency-free line/trend chart for a series that moves over
// time (e.g. a 6-month expense ratio trend, a day-by-day cash flow
// forecast) - same plain-Canvas, no-charting-library convention as
// SimpleBarChartView/SimplePieChartView (see SimpleBarChartView's own
// comment for why). Reuses SimpleBarChartView.Entry rather than a
// parallel class; every existing trend report already builds that type
// with every entry sharing one color (see ExpenseRatioTrendReportActivity),
// so this view just takes the first entry's color as the line's single
// stroke color instead of asking callers to pass a second value.
//
// A single point has no trend to show, so this renders nothing (height
// 0) below 2 points - this view's own version of this feature's "skip a
// chart that doesn't fit the data" rule.
public class SimpleLineChartView extends View {

	private List<SimpleBarChartView.Entry> entries = new ArrayList<SimpleBarChartView.Entry>();

	private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	public SimpleLineChartView(Context context) {
		super(context);
		init();
	}

	public SimpleLineChartView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {

		float density = getResources().getDisplayMetrics().density;

		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setStrokeWidth(2 * density);
		linePaint.setStrokeJoin(Paint.Join.ROUND);
		linePaint.setStrokeCap(Paint.Cap.ROUND);

		fillPaint.setStyle(Paint.Style.FILL);

		dotPaint.setStyle(Paint.Style.FILL);

		labelPaint.setColor(Color.parseColor("#6B7280"));
		labelPaint.setTextSize(11 * density);
		labelPaint.setTextAlign(Paint.Align.CENTER);

		valuePaint.setColor(Color.parseColor("#1F2937"));
		valuePaint.setTextSize(11 * density);
		valuePaint.setTextAlign(Paint.Align.CENTER);
		valuePaint.setFakeBoldText(true);

		baselinePaint.setColor(Color.parseColor("#EAECF3"));
		baselinePaint.setStrokeWidth(1 * density);
	}

	// Fewer than 2 points can't show a trend, so entries is left empty
	// (collapses the view entirely) rather than drawing a single dot.
	public void setEntries(List<SimpleBarChartView.Entry> rawEntries) {

		this.entries = (rawEntries != null && rawEntries.size() >= 2) ?
			rawEntries : new ArrayList<SimpleBarChartView.Entry>();

		requestLayout();
		invalidate();
	}

	public boolean hasChartableData() {
		return !entries.isEmpty();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		float density = getResources().getDisplayMetrics().density;

		int desiredHeight = entries.isEmpty() ? 0 : (int) (150 * density);

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
		int height = getHeight();

		float labelHeight = 16 * density;
		float valueHeight = 16 * density;
		float topPadding = 8 * density;
		float bottomPadding = 4 * density;
		float dotRadius = 3 * density;

		float plotTop = topPadding + valueHeight;
		float plotBottom = height - bottomPadding - labelHeight;
		float plotHeight = plotBottom - plotTop;

		int count = entries.size();

		double minValue = entries.get(0).value;
		double maxValue = entries.get(0).value;

		for (SimpleBarChartView.Entry e : entries) {
			minValue = Math.min(minValue, e.value);
			maxValue = Math.max(maxValue, e.value);
		}

		// Zero is always included in the plotted range (never cropped
		// out), so a reader can see at a glance whether the series ever
		// crosses it - important for things like a cash flow forecast
		// where a dip below zero is the entire point of the chart.
		minValue = Math.min(minValue, 0);
		maxValue = Math.max(maxValue, 0);

		double range = maxValue - minValue;

		if (range < 0.0001) {
			range = 1;
		}

		float zeroY = (float) (plotBottom - ((0 - minValue) / range) * plotHeight);

		canvas.drawLine(0, zeroY, width, zeroY, baselinePaint);

		int lineColor = entries.get(0).color;

		linePaint.setColor(lineColor);
		dotPaint.setColor(lineColor);
		fillPaint.setColor(lineColor);
		fillPaint.setAlpha(28);

		// A handful of points (every trend report so far, e.g. 6 months)
		// always gets a value above every dot and a label below it. A
		// sparkline-style chart with many points (e.g. a 30-day cash
		// flow forecast) would make every point's value/label overlap
		// its neighbors, so past this threshold only every few points
		// gets a label and none gets a value - same threshold and
		// reasoning as SimpleBarChartView's own sparse mode.
		boolean sparse = count > 10;
		int labelInterval = sparse ? Math.max(1, (int) Math.ceil(count / 6.0)) : 1;

		float slotWidth = width / (float) count;

		float[] xs = new float[count];
		float[] ys = new float[count];

		for (int i = 0; i < count; i++) {

			SimpleBarChartView.Entry e = entries.get(i);

			xs[i] = count == 1 ? width / 2f : slotWidth * i + slotWidth / 2f;
			ys[i] = (float) (plotBottom - ((e.value - minValue) / range) * plotHeight);
		}

		Path fillPath = new Path();
		fillPath.moveTo(xs[0], zeroY);

		for (int i = 0; i < count; i++) {
			fillPath.lineTo(xs[i], ys[i]);
		}

		fillPath.lineTo(xs[count - 1], zeroY);
		fillPath.close();

		canvas.drawPath(fillPath, fillPaint);

		Path linePath = new Path();
		linePath.moveTo(xs[0], ys[0]);

		for (int i = 1; i < count; i++) {
			linePath.lineTo(xs[i], ys[i]);
		}

		canvas.drawPath(linePath, linePaint);

		for (int i = 0; i < count; i++) {

			SimpleBarChartView.Entry e = entries.get(i);

			canvas.drawCircle(xs[i], ys[i], dotRadius, dotPaint);

			if (!sparse) {

				float valueY = Math.max(ys[i] - 6 * density, topPadding + valueHeight - 2 * density);

				canvas.drawText(AmountFormat.format(e.value), xs[i], valueY, valuePaint);
			}

			if (!sparse || i % labelInterval == 0 || i == count - 1) {
				canvas.drawText(e.label, xs[i], height - 2 * density, labelPaint);
			}
		}
	}
}
