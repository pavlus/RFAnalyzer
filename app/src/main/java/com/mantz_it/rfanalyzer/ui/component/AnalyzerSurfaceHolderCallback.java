package com.mantz_it.rfanalyzer.ui.component;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.SurfaceHolder;

/**
 * Created by Pavel on 29.03.2017.
 */
class AnalyzerSurfaceHolderCallback implements SurfaceHolder.Callback {
private AnalyzerSurface analyzerSurface;

public AnalyzerSurfaceHolderCallback(AnalyzerSurface analyzerSurface) {this.analyzerSurface = analyzerSurface;}

/**
 * SurfaceHolder.Callback function. Gets called when the surface view is created.
 * We do all the work in surfaceChanged()...
 *
 * @param holder reference to the surface holder
 */
@Override
public void surfaceCreated(SurfaceHolder holder) {

}

/**
 * SurfaceHolder.Callback function. This is called every time the dimension changes
 * (and after the SurfaceView is created).
 *
 * @param holder reference to the surface holder
 * @param format
 * @param width  current width of the surface view
 * @param height current height of the surface view
 */
@Override
public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	if (analyzerSurface.width != width || analyzerSurface.height != height) {
		analyzerSurface.width = width;
		analyzerSurface.height = height;
		analyzerSurface.setFftHeight((int) (height * analyzerSurface.getFftRatio()));
		// Recreate the shaders:
		analyzerSurface.getFftPaint().setShader(new LinearGradient(0, 0, 0, analyzerSurface.getFftHeight(), Color.WHITE, Color.BLUE, Shader.TileMode.MIRROR));

		// Recreate the waterfall bitmaps:
		analyzerSurface.createWaterfallLineBitmaps();

		// Fix the text size of the text paint objects:
		analyzerSurface.setFontSize(analyzerSurface.getFontSize());
	}
}

/**
 * SurfaceHolder.Callback function. Gets called before the surface view is destroyed
 *
 * @param holder reference to the surface holder
 */
@Override
public void surfaceDestroyed(SurfaceHolder holder) {

}
}
