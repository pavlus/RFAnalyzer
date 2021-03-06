package com.mantz_it.rfanalyzer;

import com.mantz_it.rfanalyzer.sdr.controls.MixerFrequency;
import com.mantz_it.rfanalyzer.sdr.controls.MixerSampleRate;

/**
 * <h1>RF Analyzer - signed 8-bit IQ Conversion</h1>
 * <p>
 * Module:      Signed8BitIQConverter.java
 * Description: This class implements methods to convert the raw input data of IQ sources (8 bit signed)
 * to SamplePackets. It has also methods to do converting and down-mixing at the same
 * time.
 *
 * @author Dennis Mantz
 *         <p>
 *         Copyright (C) 2014 Dennis Mantz
 *         License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *         <p>
 *         This library is free software; you can redistribute it and/or
 *         modify it under the terms of the GNU General Public
 *         License as published by the Free Software Foundation; either
 *         version 2 of the License, or (at your option) any later version.
 *         <p>
 *         This library is distributed in the hope that it will be useful,
 *         but WITHOUT ANY WARRANTY; without even the implied warranty of
 *         MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *         General Public License for more details.
 *         <p>
 *         You should have received a copy of the GNU General Public
 *         License along with this library; if not, write to the Free Software
 *         Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
@Deprecated
public class Signed8BitIQConverter extends IQConverter {
private MixerFrequency mixerFrequency;
private MixerSampleRate mixerSampleRate;

public Signed8BitIQConverter() {
	super();
	mixerFrequency = getControl(MixerFrequency.class);
	mixerSampleRate = getControl(MixerSampleRate.class);
}

@Override
protected void generateLookupTable() {
	/**
	 * The HackRF delivers samples in the following format:
	 * The bytes are complex, 8-bit, signed IQ samples (in-phase
	 *  component first, followed by the quadrature component):
	 *
	 *  [--------- first sample ----------]   [-------- second sample --------]
	 *         I                  Q                  I                Q ...
	 *  receivedBytes[0]   receivedBytes[1]   receivedBytes[2]       ...
	 */

	lookupTable = new float[256];
	for (int i = 0; i < 256; i++)
		lookupTable[i] = (i - 128) / 128.0f;
}

@Override
protected void generateMixerLookupTable(int mixFrequency) {
	// If mix frequency is too low, just add the sample rate (sampled spectrum is periodic):
	if (mixFrequency == 0 || (mixerSampleRate.get() / Math.abs(mixFrequency) > MAX_COSINE_LENGTH))
		mixFrequency += mixerSampleRate.get();

	// Only generate lookupTable if null or invalid:
	if (cosineRealLookupTable == null || mixFrequency != cosineFrequency) {
		cosineFrequency = mixFrequency;
		int bestLength = calcOptimalCosineLength();
		cosineRealLookupTable = new float[bestLength][256];
		cosineImagLookupTable = new float[bestLength][256];
		float cosineAtT;
		float sineAtT;
		for (int t = 0; t < bestLength; t++) {
			cosineAtT = (float) Math.cos(2 * Math.PI * cosineFrequency * t / (float) mixerSampleRate.get());
			sineAtT = (float) Math.sin(2 * Math.PI * cosineFrequency * t / (float) mixerSampleRate.get());
			for (int i = 0; i < 256; i++) {
				cosineRealLookupTable[t][i] = (i - 128) / 128.0f * cosineAtT;
				cosineImagLookupTable[t][i] = (i - 128) / 128.0f * sineAtT;
			}
		}
		cosineIndex = 0;
	}
}

@Override
public int fillPacketIntoSamplePacket(byte[] packet, SamplePacket samplePacket, int offset) {
	int capacity = samplePacket.capacity();
	int count = 0;
	int startIndex = samplePacket.size();
	float[] re = samplePacket.re();
	float[] im = samplePacket.im();
	for (int i = 0; i < packet.length; i += 2) {
		re[startIndex + count] = lookupTable[packet[i] + 128];
		im[startIndex + count] = lookupTable[packet[i + 1] + 128];
		count++;
		if (startIndex + count >= capacity)
			break;
	}
	samplePacket.setSize(samplePacket.size() + count);    // update the size of the sample packet
	samplePacket.setSampleRate(mixerSampleRate.get());                // update the sample rate
	samplePacket.setFrequency(mixerFrequency.get());                // update the frequency
	return count;
}

@Override
public int mixPacketIntoSamplePacket(byte[] packet, SamplePacket samplePacket, int offset, long channelFrequency) {
	int mixFrequency = (int) (mixerFrequency.get() - channelFrequency);

	generateMixerLookupTable(mixFrequency);    // will only generate table if really necessary

	// Mix the samples from packet and store the results in the samplePacket
	int capacity = samplePacket.capacity();
	int count = 0;
	int startIndex = samplePacket.size();
	float[] re = samplePacket.re();
	float[] im = samplePacket.im();
	for (int i = 0; i < packet.length; i += 2) {
		re[startIndex + count] = cosineRealLookupTable[cosineIndex][packet[i] + 128] - cosineImagLookupTable[cosineIndex][packet[i + 1] + 128];
		im[startIndex + count] = cosineRealLookupTable[cosineIndex][packet[i + 1] + 128] + cosineImagLookupTable[cosineIndex][packet[i] + 128];
		cosineIndex = (cosineIndex + 1) % cosineRealLookupTable.length;
		count++;
		if (startIndex + count >= capacity)
			break;
	}
	samplePacket.setSize(samplePacket.size() + count);    // update the size of the sample packet
	samplePacket.setSampleRate(mixerSampleRate.get());                // update the sample rate
	samplePacket.setFrequency(channelFrequency);        // update the frequency
	return count;
}

@Override
public int getSampleSize() {
	return 1;
}
}
