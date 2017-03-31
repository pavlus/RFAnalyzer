package com.mantz_it.rfanalyzer.device.rtlsdr;

import android.util.Log;

import com.mantz_it.rfanalyzer.sdr.controls.MixerFrequency;
import com.mantz_it.rfanalyzer.sdr.controls.RXFrequency;

/**
 * Created by Pavel on 21.03.2017.
 */
class RtlsdrRXFrequency implements RXFrequency {
private static final String LOGTAG = "[RTL-SDR]:RXFrequency";
private long frequency = 0;
private int frequencyOffset = 0;    // virtually shift the frequency according to an external up/down-converter

private RtlsdrSource rtlsdrSource;
private MixerFrequency mixerFrequency;

RtlsdrRXFrequency(RtlsdrSource rtlsdrSource, MixerFrequency mixerFrequency) {
	this.rtlsdrSource = rtlsdrSource;
	this.mixerFrequency = mixerFrequency;
}

@Override
public Long get() {
	return frequency + frequencyOffset;
}

@Override
public void set(Long to) {
	long actualSourceFrequency = to - frequencyOffset;
	if (rtlsdrSource.isOpen()) {
		if (to < getMin() || to > getMax()) {
			Log.w(LOGTAG, "setFrequency: Frequency out of valid range: " + to
			              + "  (upconverterFrequency=" + frequencyOffset + " is subtracted!)");
		}
		if (to < getMin())
			to = getMin();
		if (to > getMax())
			to = getMax();
		rtlsdrSource.getCommandThread().executeCommand(RtlsdrCommand.SET_FREQUENCY, (int) actualSourceFrequency);
	}

	// Flush the queue:
	rtlsdrSource.flushQueue();

	frequency = actualSourceFrequency;
	mixerFrequency.set(to);
}

@Override
public Long getMax() {
	return rtlsdrSource.getTuner().maxFrequency + frequencyOffset;
}

@Override
public Long getMin() {
	return rtlsdrSource.getTuner().minFrequency + frequencyOffset;
}

@Override
public int getFrequencyOffset() {
	return frequencyOffset;
}

@Override
public void setFrequencyOffset(int frequencyOffset) {
	this.frequencyOffset = frequencyOffset;
	mixerFrequency.set(frequency + frequencyOffset);
}


}
