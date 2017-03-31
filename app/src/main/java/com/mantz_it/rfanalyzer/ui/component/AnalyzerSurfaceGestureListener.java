package com.mantz_it.rfanalyzer.ui.component;

import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * Created by Pavel on 29.03.2017.
 */
class AnalyzerSurfaceGestureListener implements GestureDetector.OnGestureListener {
private static final String LOGTAG = "AnalyzerGestures";
	private AnalyzerSurface analyzerSurface;

	public AnalyzerSurfaceGestureListener(AnalyzerSurface analyzerSurface) {this.analyzerSurface = analyzerSurface;}

	//------------------- <OnGestureListener> -----------------------------------//
	@Override
	public boolean onDown(MotionEvent e) {
		// Find out which type of scrolling is requested:
		float hzPerPx = analyzerSurface.virtualSampleRate / (float) analyzerSurface.width;
		float dbPerPx = (analyzerSurface.maxDB - analyzerSurface.minDB) / (float) analyzerSurface.getFftHeight();
		float channelFrequencyVariation = (float) Math.max(analyzerSurface.channelWidth * 0.8f, analyzerSurface.width / 15f * hzPerPx);
		float channelWidthVariation = analyzerSurface.width / 15 * hzPerPx;
		long touchedFrequency = analyzerSurface.virtualFrequency - analyzerSurface.virtualSampleRate / 2 + (long) (e.getX() * hzPerPx);
		float touchedDB = analyzerSurface.maxDB - e.getY() * dbPerPx;

		// if the user touched the squelch indicator the user wants to adjust the squelch threshold:
		if (analyzerSurface.demodulationEnabled && touchedFrequency < analyzerSurface.channelFrequency + analyzerSurface.channelWidth
		    && touchedFrequency > analyzerSurface.channelFrequency - analyzerSurface.channelWidth
		    && touchedDB < analyzerSurface.squelch + (analyzerSurface.maxDB - analyzerSurface.minDB) / 7
		    && touchedDB > analyzerSurface.squelch - (analyzerSurface.maxDB - analyzerSurface.minDB) / 7)
			analyzerSurface.scrollType = AnalyzerSurface.SCROLLTYPE_SQUELCH;

			// if the user touched the channel frequency the user wants to shift the channel frequency:
		else if (analyzerSurface.demodulationEnabled && e.getY() <= analyzerSurface.getFftHeight()
		         && touchedFrequency < analyzerSurface.channelFrequency + channelFrequencyVariation
		         && touchedFrequency > analyzerSurface.channelFrequency - channelFrequencyVariation)
			analyzerSurface.scrollType = AnalyzerSurface.SCROLLTYPE_CHANNEL_FREQUENCY;

			// if the user touched the left channel selector border the user wants to adjust the channel width:
		else if (analyzerSurface.demodulationEnabled && e.getY() <= analyzerSurface.getFftHeight() && analyzerSurface.showLowerBand
		         && touchedFrequency < analyzerSurface.channelFrequency - analyzerSurface.channelWidth + channelWidthVariation
		         && touchedFrequency > analyzerSurface.channelFrequency - analyzerSurface.channelWidth - channelWidthVariation)
			analyzerSurface.scrollType = AnalyzerSurface.SCROLLTYPE_CHANNEL_WIDTH_LEFT;

			// if the user touched the right channel selector border the user wants to adjust the channel width:
		else if (analyzerSurface.demodulationEnabled && e.getY() <= analyzerSurface.getFftHeight() && analyzerSurface.showUpperBand
		         && touchedFrequency < analyzerSurface.channelFrequency + analyzerSurface.channelWidth + channelWidthVariation
		         && touchedFrequency > analyzerSurface.channelFrequency + analyzerSurface.channelWidth - channelWidthVariation)
			analyzerSurface.scrollType = AnalyzerSurface.SCROLLTYPE_CHANNEL_WIDTH_RIGHT;

			// otherwise the user wants to scroll the virtual frequency
		else
			analyzerSurface.scrollType = AnalyzerSurface.SCROLLTYPE_NORMAL;
		Log.d(LOGTAG, "onDown: virtualFrequency=" + analyzerSurface.virtualFrequency
		                              + ", virtualSampleRate=" + analyzerSurface.virtualSampleRate
		                              + ", channelFrequency=" + analyzerSurface.channelFrequency);
		return true;
	}

	@Override
	public void onShowPress(MotionEvent e) {
		// not used
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		// Set the channel frequency to the tapped position
		if (analyzerSurface.demodulationEnabled) {
			float hzPerPx = analyzerSurface.virtualSampleRate / (float) analyzerSurface.width;
			analyzerSurface.channelFrequency = analyzerSurface.virtualFrequency - analyzerSurface.virtualSampleRate / 2 + (long) (hzPerPx * e.getX());
			analyzerSurface.callbackHandler.onUpdateChannelFrequency(analyzerSurface.channelFrequency);
		}
		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		if (analyzerSurface.getSource() != null) {
			float hzPerPx = analyzerSurface.virtualSampleRate / (float) analyzerSurface.width;

			// scroll horizontally or adjust channel selector (scroll type was selected in onDown() event routine:
			switch (analyzerSurface.scrollType) {
				case AnalyzerSurface.SCROLLTYPE_NORMAL:
					// Scroll horizontal if touch point in the main area or always if decoupled axis is deactivated:
					if (!analyzerSurface.decoupledAxis || e1.getX() > analyzerSurface.getGridSize() * 1.5 || e1.getY() > analyzerSurface.getFftHeight() - analyzerSurface.getGridSize()) {
						long minFrequencyShift = Math.max(analyzerSurface.virtualFrequency * -1 + 1,
								analyzerSurface.getRxFrequency().getMin() - analyzerSurface.getRxSampleRate().get() / 2 - analyzerSurface.virtualFrequency);
						long maxFrequencyShift = analyzerSurface.getRxFrequency().getMax() + analyzerSurface.getRxSampleRate().get() / 2 - analyzerSurface.virtualFrequency;
						long virtualFrequencyShift = Math.min(Math.max((long) (hzPerPx * distanceX), minFrequencyShift), maxFrequencyShift);
						analyzerSurface.virtualFrequency += virtualFrequencyShift;
						analyzerSurface.channelFrequency += virtualFrequencyShift;
						analyzerSurface.callbackHandler.onUpdateChannelFrequency(analyzerSurface.channelFrequency);
					}
					break;
				case AnalyzerSurface.SCROLLTYPE_CHANNEL_FREQUENCY:
					analyzerSurface.channelFrequency -= distanceX * hzPerPx;
					analyzerSurface.callbackHandler.onUpdateChannelFrequency(analyzerSurface.channelFrequency);
					break;
				case AnalyzerSurface.SCROLLTYPE_CHANNEL_WIDTH_LEFT:
				case AnalyzerSurface.SCROLLTYPE_CHANNEL_WIDTH_RIGHT:
					int tmpChannelWidth = analyzerSurface.scrollType == AnalyzerSurface.SCROLLTYPE_CHANNEL_WIDTH_LEFT
							? (int) (analyzerSurface.channelWidth + distanceX * hzPerPx)
							: (int) (analyzerSurface.channelWidth - distanceX * hzPerPx);
					if (analyzerSurface.callbackHandler.onUpdateChannelWidth(tmpChannelWidth))
						analyzerSurface.channelWidth = tmpChannelWidth;
					break;
				case AnalyzerSurface.SCROLLTYPE_SQUELCH:
					float dbPerPx = (analyzerSurface.maxDB - analyzerSurface.minDB) / (float) analyzerSurface.getFftHeight();
					analyzerSurface.squelch = analyzerSurface.squelch + distanceY * dbPerPx;
					if (analyzerSurface.squelch < analyzerSurface.minDB)
						analyzerSurface.squelch = analyzerSurface.minDB;
					break;
				default:
					Log.e(LOGTAG, "onScroll: invalid scroll type: " + analyzerSurface.scrollType);
			}

			// scroll vertically
			if (analyzerSurface.verticalScrollEnabled && analyzerSurface.scrollType == AnalyzerSurface.SCROLLTYPE_NORMAL) {
				// if touch point in the left grid area of fft or if decoupled axis is deactivated:
				if (!analyzerSurface.decoupledAxis || (e1.getX() <= analyzerSurface.getGridSize() * 1.5 && e1.getY() <= analyzerSurface.getFftHeight() - analyzerSurface.getGridSize())) {
					float yDiff = (analyzerSurface.maxDB - analyzerSurface.minDB) * (distanceY / (float) analyzerSurface.getFftHeight());
					// Make sure we stay in the boundaries:
					if (analyzerSurface.maxDB - yDiff > AnalyzerSurface.MAX_DB)
						yDiff = AnalyzerSurface.MAX_DB - analyzerSurface.maxDB;
					if (analyzerSurface.minDB - yDiff < AnalyzerSurface.MIN_DB)
						yDiff = AnalyzerSurface.MIN_DB - analyzerSurface.minDB;
					analyzerSurface.setDBScale(analyzerSurface.minDB - yDiff, analyzerSurface.maxDB - yDiff);

					// adjust the squelch if it is outside the visible viewport right now and demodulation is enabled:
					if (analyzerSurface.demodulationEnabled) {
						if (analyzerSurface.squelch < analyzerSurface.minDB)
							analyzerSurface.squelch = analyzerSurface.minDB;
						if (analyzerSurface.squelch > analyzerSurface.maxDB)
							analyzerSurface.squelch = analyzerSurface.maxDB;
					}
				}
			}

			// Automatically re-tune the source if we scrolled the samples out of the visible window:
			// (only if not recording)
			if (!analyzerSurface.isRecordingEnabled()) {
				if (analyzerSurface.getRxFrequency().get() + analyzerSurface.getRxSampleRate().get() / 2 < analyzerSurface.virtualFrequency + analyzerSurface.virtualSampleRate / 2 ||
				    analyzerSurface.getRxFrequency().get() - analyzerSurface.getRxSampleRate().get() / 2 > analyzerSurface.virtualFrequency - analyzerSurface.virtualSampleRate / 2) {
					if (analyzerSurface.virtualFrequency >= analyzerSurface.getRxFrequency().getMin() && analyzerSurface.virtualFrequency <= analyzerSurface.getRxFrequency().getMax())
						analyzerSurface.getRxFrequency().set(analyzerSurface.virtualFrequency);
				}
			} else {
				// if recording, we restrict scrolling outside the fft:
				if (analyzerSurface.virtualFrequency + analyzerSurface.virtualSampleRate / 2 > analyzerSurface.getRxFrequency().get() + analyzerSurface.getRxSampleRate().get() / 2)
					analyzerSurface.virtualFrequency = analyzerSurface.getRxFrequency().get() + analyzerSurface.getRxSampleRate().get() / 2 - analyzerSurface.virtualSampleRate / 2;
				if (analyzerSurface.virtualFrequency - analyzerSurface.virtualSampleRate / 2 < analyzerSurface.getRxFrequency().get() - analyzerSurface.getRxSampleRate().get() / 2)
					analyzerSurface.virtualFrequency = analyzerSurface.getRxFrequency().get() - analyzerSurface.getRxSampleRate().get() / 2 + analyzerSurface.virtualSampleRate / 2;
			}
		}

		return true;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		Log.d(LOGTAG, "onLongPress: virtualFrequency=" + analyzerSurface.virtualFrequency
		                              + ", virtualSampleRate=" + analyzerSurface.virtualSampleRate
		                              + ", channelFrequency=" + analyzerSurface.channelFrequency);
		// not used
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		return true;
	}
}
