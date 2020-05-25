/**
 * Copyright (C) 2009 Michael A. MacDonald
 */
package de.toshsoft.tsvnc;

import android.widget.ImageView.ScaleType;

import de.toshsoft.tsvnc.R;

/**
 * @author Michael A. MacDonald
 */
class FitToScreenScaling extends AbstractScaling {

	private float screenWidth;
	private float screenHeight;
	private float framebufferWidth;
	private float framebufferHeight;
	public float mousePosCorrectionX;
	public float mousePosCorrectionY;

	/**
	 * @param id
	 * @param scaleType
	 */
	FitToScreenScaling() {
		super(R.id.itemFitToScreen, ScaleType.FIT_CENTER);
	}

	/* (non-Javadoc)
	 * @see android.androidVNC.AbstractScaling#isAbleToPan()
	 */
	@Override
	boolean isAbleToPan() {
		return false;
	}

	/* (non-Javadoc)
	 * @see android.androidVNC.AbstractScaling#isValidInputMode(int)
	 */
	@Override
	boolean isValidInputMode(int mode) {
		return mode == R.id.itemInputFitToScreen;
	}

	/* (non-Javadoc)
	 * @see android.androidVNC.AbstractScaling#getDefaultHandlerId()
	 */
	@Override
	int getDefaultHandlerId() {
		return R.id.itemInputFitToScreen;
	}

	/* (non-Javadoc)
	 * @see android.androidVNC.AbstractScaling#setCanvasScaleType(android.androidVNC.VncCanvas)
	 */
	@Override
	void setScaleTypeForActivity(VncCanvasActivity activity) {
		super.setScaleTypeForActivity(activity);
		activity.vncCanvas.absoluteXPosition = activity.vncCanvas.absoluteYPosition = 0;
		activity.vncCanvas.scrollTo(0, 0);

		framebufferWidth = activity.vncCanvas.rfb.framebufferWidth;
		framebufferHeight = activity.vncCanvas.rfb.framebufferHeight;
		screenWidth = activity.vncCanvas.displayWidth;
		screenHeight = activity.vncCanvas.displayHeight;
	}

	@Override
	float getScale() {
		float scalingX = 1.0f;
		float scalingY = 1.0f;
		if(framebufferWidth > 0.0f && screenWidth > 0.0f)
			scalingX = screenWidth / framebufferWidth;
		if(framebufferHeight > 0.0f && screenHeight > 0.0f)
			scalingY = screenHeight / framebufferHeight;

		return scalingX < scalingY ? scalingX : scalingY;
	}
}
