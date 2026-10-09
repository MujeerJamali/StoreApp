package com.mujeer.businesserp;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

// App-wide swipe-up/down navigation between the Add Sale / Add Purchase
// / Add Expense screens (see each Activity's own wiring - the cycle is
// Sale -> Purchase -> Expense -> Sale on swipe up, reverse on swipe
// down). Deliberately a fling-only gesture, not a plain drag: a long,
// fast, mostly-vertical swipe (tuned by MIN_DISTANCE/MIN_VELOCITY below)
// so ordinary scrolling or tapping inside the form is never mistaken
// for a navigation swipe. Each hosting Activity feeds every touch event
// through onTouchEvent() from its own dispatchTouchEvent() override
// (still calling super so normal touch handling - scrolling, taps,
// keyboard focus - is completely unaffected); this class never consumes
// or intercepts anything itself.
class SwipeNavigationHelper {

	private static final int MIN_DISTANCE_PX = 250;
	private static final int MIN_VELOCITY_PX_PER_SEC = 800;

	private final GestureDetector gestureDetector;

	SwipeNavigationHelper(
		Context context, final Runnable onSwipeUp, final Runnable onSwipeDown) {

		gestureDetector = new GestureDetector(
			context,
			new GestureDetector.SimpleOnGestureListener() {

				@Override
				public boolean onFling(
					MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

					if (e1 == null || e2 == null) {
						return false;
					}

					float deltaY = e2.getY() - e1.getY();
					float deltaX = e2.getX() - e1.getX();

					boolean mostlyVertical = Math.abs(deltaY) > Math.abs(deltaX) * 2;
					boolean longEnough = Math.abs(deltaY) > MIN_DISTANCE_PX;
					boolean fastEnough = Math.abs(velocityY) > MIN_VELOCITY_PX_PER_SEC;

					if (!mostlyVertical || !longEnough || !fastEnough) {
						return false;
					}

					if (deltaY < 0) {

						if (onSwipeUp != null) {
							onSwipeUp.run();
						}

					} else {

						if (onSwipeDown != null) {
							onSwipeDown.run();
						}
					}

					return true;
				}
			}
		);
	}

	void onTouchEvent(MotionEvent event) {
		gestureDetector.onTouchEvent(event);
	}
}
