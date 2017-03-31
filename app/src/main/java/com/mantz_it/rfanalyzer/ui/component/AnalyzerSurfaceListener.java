package com.mantz_it.rfanalyzer.ui.component;

/**
 * Interface used to report user actions (channel frequency/width changes)
 */
public interface AnalyzerSurfaceListener {
	/**
	 * Is called when the user adjusts the channel width.
	 *
	 * @param newChannelWidth new channel width (single sided) in Hz
	 * @return true if valid width; false if width is out of range
	 */
	boolean onUpdateChannelWidth(int newChannelWidth);

	/**
	 * Is called when the user adjusts the channel frequency.
	 *
	 * @param newChannelFrequency new channel frequency in Hz
	 */
	void onUpdateChannelFrequency(long newChannelFrequency);

	/**
	 * Is called when the signal strength of the selected channel
	 * crosses the squelch threshold
	 *
	 * @param squelchSatisfied true: the signal is now stronger than the threshold; false: signal is now weaker
	 */
	void onUpdateSquelchSatisfied(boolean squelchSatisfied);

	/**
	 * Is called when the AnalyzerSurface has to determine the current
	 * channel width
	 *
	 * @return the current channel width
	 */
	int onCurrentChannelWidthRequested();
}
