package com.mantz_it.rfanalyzer.ui.component;

import android.util.Log;
import android.view.ScaleGestureDetector;

/**
 * Created by Pavel on 29.03.2017.
 */
class AnalyzerSurfaceScaleGestureListener implements ScaleGestureDetector.OnScaleGestureListener {
private static final String LOGTAG = "AnalyzerScaleListener";
private AnalyzerSurface analyzerSurface;

AnalyzerSurfaceScaleGestureListener(AnalyzerSurface analyzerSurface) {this.analyzerSurface = analyzerSurface;}

//------------------- <OnScaleGestureListener> ------------------------------//
@Override
public boolean onScale(ScaleGestureDetector detector) {
	if (analyzerSurface.getSource() != null) {
		// Zoom horizontal if focus in the main area or always if decoupled axis is deactivated:
		if (!analyzerSurface.decoupledAxis || detector.getFocusX() > analyzerSurface.getGridSize() * 1.5) {
			float xScale = detector.getCurrentSpanX() / detector.getPreviousSpanX();
			long frequencyFocus = analyzerSurface.virtualFrequency + (int) ((detector.getFocusX() / analyzerSurface.width - 0.5) * analyzerSurface.virtualSampleRate);
			int maxSampleRate = analyzerSurface.demodulationEnabled ? (int) (analyzerSurface.getRxSampleRate().get() * 0.9) : analyzerSurface.getRxSampleRate().getMax();
			if (analyzerSurface.isRecordingEnabled())
				maxSampleRate = analyzerSurface.getRxSampleRate().get();
			analyzerSurface.virtualSampleRate = (int) Math.min(Math.max(analyzerSurface.virtualSampleRate / xScale, AnalyzerSurface.MIN_VIRTUAL_SAMPLERATE), maxSampleRate);
			//int nextHigher = source.getNextHigherOptimalSampleRate(virtualSampleRate);
			//int nextLower = source.getNextLowerOptimalSampleRate(virtualSampleRate);
			//if (virtualSampleRate > source.getSampleRate())
			//	virtualSampleRate = ;
			//else if (virtualSampleRate < source.getSampleRate())
			//	virtualSampleRate = source.getNextLowerOptimalSampleRate(virtualSampleRate);
			analyzerSurface.virtualFrequency = Math.min(Math.max(frequencyFocus + (long) ((analyzerSurface.virtualFrequency - frequencyFocus) / xScale),
					analyzerSurface.getRxFrequency().getMin() - analyzerSurface.getRxSampleRate().get() / 2), analyzerSurface.getRxFrequency().getMax() + analyzerSurface.getRxSampleRate().get() / 2);

			// if we zoomed the channel selector out of the window, reset the channel selector:
			if (analyzerSurface.demodulationEnabled && analyzerSurface.channelFrequency < analyzerSurface.virtualFrequency - analyzerSurface.virtualSampleRate / 2) {
				analyzerSurface.channelFrequency = analyzerSurface.virtualFrequency - analyzerSurface.virtualSampleRate / 2;
				analyzerSurface.callbackHandler.onUpdateChannelFrequency(analyzerSurface.channelFrequency);
			}
			if (analyzerSurface.demodulationEnabled && analyzerSurface.channelFrequency > analyzerSurface.virtualFrequency + analyzerSurface.virtualSampleRate / 2) {
				analyzerSurface.channelFrequency = analyzerSurface.virtualFrequency + analyzerSurface.virtualSampleRate / 2;
				analyzerSurface.callbackHandler.onUpdateChannelFrequency(analyzerSurface.channelFrequency);
			}
			Log.d(LOGTAG, "onScale: virtualFrequency=" + analyzerSurface.virtualFrequency
			              + ", virtualSampleRate=" + analyzerSurface.virtualSampleRate
			              + ", channelFrequency=" + analyzerSurface.channelFrequency);
		}

		// Zoom vertical if enabled and focus in the left grid area or if decoupled axis is deactivated:
		if (analyzerSurface.verticalZoomEnabled && (!analyzerSurface.decoupledAxis || detector.getFocusX() <= analyzerSurface.getGridSize() * 1.5)) {
			float yScale = detector.getCurrentSpanY() / detector.getPreviousSpanY();
			float dBFocus = analyzerSurface.maxDB - (analyzerSurface.maxDB - analyzerSurface.minDB) * (detector.getFocusY() / analyzerSurface.getFftHeight());
			float newMinDB = Math.min(Math.max(dBFocus - (dBFocus - analyzerSurface.minDB) / yScale, AnalyzerSurface.MIN_DB), AnalyzerSurface.MAX_DB - 10);
			float newMaxDB = Math.min(Math.max(dBFocus - (dBFocus - analyzerSurface.maxDB) / yScale, newMinDB + 10), AnalyzerSurface.MAX_DB);
			analyzerSurface.setDBScale(newMinDB, newMaxDB);

			// adjust the squelch if it is outside the visible viewport right now:
			if (analyzerSurface.squelch < analyzerSurface.minDB)
				analyzerSurface.squelch = analyzerSurface.minDB;
			if (analyzerSurface.squelch > analyzerSurface.maxDB)
				analyzerSurface.squelch = analyzerSurface.maxDB;
		}

		// Automatically re-adjust the sample rate of the source if we zoom too far out or in
		// (only if not recording or demodulating!)
		if (!analyzerSurface.isRecordingEnabled() && !analyzerSurface.demodulationEnabled) {
			if (analyzerSurface.getRxSampleRate().get() < analyzerSurface.virtualSampleRate && analyzerSurface.virtualSampleRate < analyzerSurface.getRxSampleRate().getMax())
				analyzerSurface.getRxSampleRate().set(analyzerSurface.getRxSampleRate().getNextHigherOptimalSampleRate(analyzerSurface.virtualSampleRate));
			else {
				int nextLower = analyzerSurface.getRxSampleRate().getNextLowerOptimalSampleRate(analyzerSurface.getRxSampleRate().get());
				if ((analyzerSurface.virtualSampleRate < nextLower) && (analyzerSurface.getRxSampleRate().get() > nextLower)) {
					analyzerSurface.getRxSampleRate().set(nextLower);
				}
			}
		}
	}
	return true;
}

@Override
public boolean onScaleBegin(ScaleGestureDetector detector) {
	return true;
}

@Override
public void onScaleEnd(ScaleGestureDetector detector) {
}
}
