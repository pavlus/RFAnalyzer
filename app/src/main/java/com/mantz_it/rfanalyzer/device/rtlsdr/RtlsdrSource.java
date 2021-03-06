package com.mantz_it.rfanalyzer.device.rtlsdr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mantz_it.rfanalyzer.IQConverter;
import com.mantz_it.rfanalyzer.IQSource;
import com.mantz_it.rfanalyzer.R;
import com.mantz_it.rfanalyzer.SamplePacket;
import com.mantz_it.rfanalyzer.Unsigned8BitIQConverter;
import com.mantz_it.rfanalyzer.control.Control;
import com.mantz_it.rfanalyzer.sdr.controls.AutomaticGainSwitchControl;
import com.mantz_it.rfanalyzer.sdr.controls.FrequencyCorrectionControl;
import com.mantz_it.rfanalyzer.sdr.controls.IntermediateFrequencyGainControl;
import com.mantz_it.rfanalyzer.sdr.controls.ManualGainControl;
import com.mantz_it.rfanalyzer.sdr.controls.ManualGainSwitchControl;
import com.mantz_it.rfanalyzer.sdr.controls.MixerFrequency;
import com.mantz_it.rfanalyzer.sdr.controls.MixerSampleRate;
import com.mantz_it.rfanalyzer.sdr.controls.RXFrequency;
import com.mantz_it.rfanalyzer.sdr.controls.RXSampleRate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h1>RF Analyzer - rtl-sdr source</h1>
 * <p>
 * Module:      RtlsdrSource.java
 * Description: Simple source of IQ sampling by reading from IQ files generated by the
 * HackRF. Just for testing.
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
public class RtlsdrSource implements IQSource {

private ReceiverThread receiverThread = null;

RtlsdrCommandThread getCommandThread() {
	return commandThread;
}

// todo: change to callback
public void commandThreadClosed(RtlsdrCommandThread commandThread) {
	if (this.commandThread == commandThread)
		this.commandThread = null;
}

private RtlsdrCommandThread commandThread = null;
private Callback callback = null;
InputStream inputStream = null;
OutputStream outputStream = null;

void onSourceReady() {
	callback.onIQSourceReady(this);
}

void setName(String name) {
	this.name = name;
}

private String name = "RTL-SDR";


public RtlsdrTuner getTuner() {
	return tuner;
}

void setTuner(RtlsdrTuner tuner) {
	this.tuner = tuner;
}

private RtlsdrTuner tuner = RtlsdrTuner.UNKNOWN;
private String ipAddress = "127.0.0.1";
private int port = 1234;
private ArrayBlockingQueue<byte[]> queue = null;
private ArrayBlockingQueue<byte[]> returnQueue = null;

private IQConverter iqConverter;
private static final String LOGTAG = "RtlsdrSource";
private static final int QUEUE_SIZE = 20;
public static final int[] OPTIMAL_SAMPLE_RATES = {1000000, 1024000, 1800000, 1920000, 2000000, 2048000, 2400000};

public static final int PACKET_SIZE = 16384;

private final RXFrequency rxFrequency;
private final MixerFrequency mixerFrequency;
private final AutomaticGainSwitchControl automaticGainSwitch;
private final ManualGainSwitchControl manualGainSwitch;
private final ManualGainControl manualGainControl;
private final RXSampleRate rxSampleRate;
private final IntermediateFrequencyGainControl ifGain;
private final MixerSampleRate mixerSampleRate;
private final FrequencyCorrectionControl frequencyCorrectionControl;

private final Map<Class<? extends Control>, Control> controls = new HashMap<>();

RtlsdrSource() {
	this.iqConverter = new Unsigned8BitIQConverter();
	mixerFrequency = iqConverter.getControl(MixerFrequency.class);
	mixerSampleRate = iqConverter.getControl(MixerSampleRate.class);

	rxFrequency = new RtlsdrRXFrequency(this, mixerFrequency);
	rxSampleRate = new RtlsdrRXSampleRate(this, mixerSampleRate);
	manualGainSwitch = new RtlsdrManualGainSwitch(this);
	manualGainControl = new RtlsdrManualGain(this);
	automaticGainSwitch = new RtlsdrAutomaticGainSwitch(this);
	ifGain = new RtlsdrIFGain(this);
	frequencyCorrectionControl = new RtlsdrFrequencyCorrection(this);
	controls.put(RXFrequency.class, rxFrequency);
	controls.put(RXSampleRate.class, rxSampleRate);
	controls.put(ManualGainSwitchControl.class, manualGainSwitch);
	controls.put(ManualGainControl.class, manualGainControl);
	controls.put(AutomaticGainSwitchControl.class, automaticGainSwitch);
	controls.put(IntermediateFrequencyGainControl.class, ifGain);
	controls.put(FrequencyCorrectionControl.class, frequencyCorrectionControl);
}

public RtlsdrSource(String ip, int port) {
	this();
	this.ipAddress = ip;
	this.port = port;

	// Create queues and buffers:
	queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
	returnQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
	for (int i = 0; i < QUEUE_SIZE; i++)
		returnQueue.offer(new byte[PACKET_SIZE]);


}

public RtlsdrSource(Context context, SharedPreferences preferences) {
	this();
	if (preferences.getBoolean(context.getString(R.string.pref_rtlsdr_externalServer), false)) {
		this.ipAddress = preferences.getString(context.getString(R.string.pref_rtlsdr_ip), "");
		this.port = Integer.parseInt(preferences.getString(context.getString(R.string.pref_rtlsdr_port), "1234"));
	} else {
		this.ipAddress = "127.0.0.1";
		this.port = 1234;
	}
	// Create queues and buffers:
	queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
	returnQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
	for (int i = 0; i < QUEUE_SIZE; i++)
		returnQueue.offer(new byte[PACKET_SIZE]);

	this.iqConverter = new Unsigned8BitIQConverter();

	// todo: add separate frequency preferences for every source type
	long frequency = preferences.getLong(context.getString(R.string.pref_frequency), 97000000);
	int sampleRate = preferences.getInt(context.getString(R.string.pref_sampleRate), rxSampleRate.getMax());
	if (sampleRate > 2000000)    // might be the case after switching over from HackRF
		sampleRate = 2000000;
	rxFrequency.set(frequency);
	rxSampleRate.set(sampleRate);

	frequencyCorrectionControl.set(Integer.parseInt(
			preferences.getString(context.getString(R.string.pref_rtlsdr_frequencyCorrection), "0")));
	rxFrequency.setFrequencyOffset(Integer.parseInt(
			preferences.getString(context.getString(R.string.pref_rtlsdr_frequencyOffset), "0")));
	manualGainSwitch.set(preferences.getBoolean(context.getString(R.string.pref_rtlsdr_manual_gain), false));
	automaticGainSwitch.set(preferences.getBoolean(context.getString(R.string.pref_rtlsdr_agc), false));
	if (manualGainSwitch.get()) {
		manualGainControl.set(preferences.getInt(context.getString(R.string.pref_rtlsdr_gain), 0));
		ifGain.set(preferences.getInt(context.getString(R.string.pref_rtlsdr_ifGain), 0));
	}
}

/**
 * Will forward an error message to the callback object
 *
 * @param msg error message
 */
void reportError(String msg) {
	if (callback != null)
		callback.onIQSourceError(this, msg);
	else
		Log.e(LOGTAG, "reportError: Callback is null. (Error: " + msg + ")");
}

/**
 * This will start the RTL2832U driver app if ip address is loopback and connect to the rtl_tcp instance
 *
 * @param context  not used
 * @param callback reference to a class that implements the Callback interface for notification
 * @return
 */
@Override
public boolean open(Context context, Callback callback) {
	this.callback = callback;

	// Start the command thread (this will perform the "open" procedure:
	// connecting to the rtl_tcp instance, read information and inform the callback handler
	if (commandThread != null) {
		Log.e(LOGTAG, "open: Command thread is still running");
		reportError("Error while opening device");
		return false;
	}
	commandThread = new RtlsdrCommandThread(this, ipAddress, port, mixerFrequency);
	commandThread.start();

	return true;
}

@Override
public void showGainDialog(final Activity activity, final SharedPreferences preferences) {
	final IntermediateFrequencyGainControl ifGain = getControl(IntermediateFrequencyGainControl.class);
	final AutomaticGainSwitchControl agcSwitch = getControl(AutomaticGainSwitchControl.class);
	final ManualGainSwitchControl manualGainSwitch = getControl(ManualGainSwitchControl.class);
	final ManualGainControl manualGainControl = getControl(ManualGainControl.class);
	if (ifGain.size() == 0) {
		Toast.makeText(activity, getName() + " does not support gain adjustment!", Toast.LENGTH_LONG).show();
	}
	// Prepare layout:
	final LinearLayout view_rtlsdr = (LinearLayout) activity.getLayoutInflater().inflate(R.layout.rtlsdr_gain, null);
	final LinearLayout ll_rtlsdr_gain = (LinearLayout) view_rtlsdr.findViewById(R.id.ll_rtlsdr_gain);
	final LinearLayout ll_rtlsdr_ifgain = (LinearLayout) view_rtlsdr.findViewById(R.id.ll_rtlsdr_ifgain);
	final Switch sw_rtlsdr_manual_gain = (Switch) view_rtlsdr.findViewById(R.id.sw_rtlsdr_manual_gain);
	final CheckBox cb_rtlsdr_agc = (CheckBox) view_rtlsdr.findViewById(R.id.cb_rtlsdr_agc);
	final SeekBar sb_rtlsdr_gain = (SeekBar) view_rtlsdr.findViewById(R.id.sb_rtlsdr_gain);
	final SeekBar sb_rtlsdr_ifGain = (SeekBar) view_rtlsdr.findViewById(R.id.sb_rtlsdr_ifgain);
	final TextView tv_rtlsdr_gain = (TextView) view_rtlsdr.findViewById(R.id.tv_rtlsdr_gain);
	final TextView tv_rtlsdr_ifGain = (TextView) view_rtlsdr.findViewById(R.id.tv_rtlsdr_ifgain);

	// Assign current gain:
	int gainIndex = 0;
	for (int i = 0; i < ifGain.size(); i++) {
		if ((ifGain.get().equals(ifGain.valueAt(i)))) {
			gainIndex = i;
			break;
		}
	}

	sb_rtlsdr_gain.setMax(manualGainControl.size() - 1);
	sb_rtlsdr_gain.setProgress(gainIndex);
	tv_rtlsdr_gain.setText(Integer.toString(manualGainControl.valueAt(gainIndex)));

	int ifGainIndex = 0;
	ifGainIndex = ifGain.getIndex();
	sb_rtlsdr_ifGain.setMax(ifGain.size());
	sb_rtlsdr_ifGain.setProgress(ifGainIndex);
	tv_rtlsdr_ifGain.setText(Integer.toString(ifGain.get()));

	// Assign current manual gain and agc setting
	sw_rtlsdr_manual_gain.setChecked(manualGainSwitch.get());
	cb_rtlsdr_agc.setChecked(agcSwitch.get());

	// Add listener to gui elements:
	sw_rtlsdr_manual_gain.setOnCheckedChangeListener((buttonView, isChecked) -> {
		sb_rtlsdr_gain.setEnabled(isChecked);
		tv_rtlsdr_gain.setEnabled(isChecked);
		sb_rtlsdr_ifGain.setEnabled(isChecked);
		tv_rtlsdr_ifGain.setEnabled(isChecked);
		manualGainSwitch.set(isChecked);
		if (isChecked) {
			manualGainControl.setByIndex(sb_rtlsdr_gain.getProgress());
			ifGain.setByIndex(sb_rtlsdr_ifGain.getProgress());
		}
	});
	cb_rtlsdr_agc.setOnCheckedChangeListener((buttonView, isChecked) -> getControl(AutomaticGainSwitchControl.class).set(isChecked));
	// todo: don't update on slide
	sb_rtlsdr_gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			tv_rtlsdr_gain.setText(Integer.toString(manualGainControl.setByIndex(progress)));
			;
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}
	});
	// todo: don't update on slide
	sb_rtlsdr_ifGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			ifGain.setByIndex(progress);
			tv_rtlsdr_ifGain.setText(Integer.toString(ifGain.get()));
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}
	});

	// Disable gui elements if gain cannot be adjusted:
	if (manualGainControl.size() == 0)
		ll_rtlsdr_gain.setVisibility(View.GONE);
	if (ifGain.size() == 0)
		ll_rtlsdr_ifgain.setVisibility(View.GONE);

	if (!sw_rtlsdr_manual_gain.isChecked()) {
		sb_rtlsdr_gain.setEnabled(false);
		tv_rtlsdr_gain.setEnabled(false);
		sb_rtlsdr_ifGain.setEnabled(false);
		tv_rtlsdr_ifGain.setEnabled(false);
	}

	// Show dialog:
	AlertDialog rtlsdrDialog = new AlertDialog.Builder(activity)
			.setTitle("Adjust Gain Settings")
			.setView(view_rtlsdr)
			.setPositiveButton("Set", (dialog, whichButton) -> {
				// safe preferences:
				SharedPreferences.Editor edit = preferences.edit();
				edit.putBoolean(activity.getString(R.string.pref_rtlsdr_manual_gain), sw_rtlsdr_manual_gain.isChecked());
				edit.putBoolean(activity.getString(R.string.pref_rtlsdr_agc), cb_rtlsdr_agc.isChecked());
				edit.putInt(activity.getString(R.string.pref_rtlsdr_gain), manualGainControl.valueAt(sb_rtlsdr_gain.getProgress()));
				edit.putInt(activity.getString(R.string.pref_rtlsdr_ifGain),
						ifGain.valueAt(sb_rtlsdr_ifGain.getProgress()));
				edit.apply();
			})
			.setNegativeButton("Cancel", (dialog, whichButton) -> {
				// do nothing
			})
			.create();
	rtlsdrDialog.setOnDismissListener(dialog -> {
		boolean manualGain = preferences.getBoolean(activity.getString(R.string.pref_rtlsdr_manual_gain), false);
		boolean agc = preferences.getBoolean(activity.getString(R.string.pref_rtlsdr_agc), false);
		int gain = preferences.getInt(activity.getString(R.string.pref_rtlsdr_gain), 0);
		int ifGainValue = preferences.getInt(activity.getString(R.string.pref_rtlsdr_ifGain), 0);
		manualGainControl.set(gain);
		ifGain.set(ifGainValue);
		manualGainSwitch.set(manualGain);
		agcSwitch.set(agc);
		if (manualGain) {
			// Note: This is a workaround. After setting manual gain to true we must
			// rewrite the manual gain values:
			manualGainControl.set(gain);
			ifGain.set(ifGainValue);
		}
	});
	rtlsdrDialog.show();
	rtlsdrDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

}

@Override
public boolean isOpen() {
	return (commandThread != null);
}

@Override
public boolean close() {
	// Stop receving:
	if (receiverThread != null)
		stopSampling();

	// Stop the command thread:
	if (commandThread != null) {
		commandThread.stopCommandThread();
		// Join the thread only if the current thread is NOT the commandThread ^^
		if (!Thread.currentThread().getName().equals(commandThread.threadName)) {
			try {
				commandThread.join();
			}
			catch (InterruptedException e) {
			}
		}
		commandThread = null;
	}

	this.tuner = RtlsdrTuner.UNKNOWN;
	this.name = "RTL-SDR";
	return true;
}

@Override
public String getName() {
	return name;
}

@Override
public RtlsdrSource updatePreferences(Context context, SharedPreferences preferences) {
	String ip = preferences.getString(context.getString(R.string.pref_rtlsdr_ip), "");
	int port = Integer.parseInt(preferences.getString(context.getString(R.string.pref_rtlsdr_port), "1234"));

	if (preferences.getBoolean(context.getString(R.string.pref_rtlsdr_externalServer), false)) {
		if (!ip.equals(ipAddress) || port != this.port) {
			this.close();
			return new RtlsdrSource(context, preferences);
		}
	} else {
		if (!ipAddress.equals("127.0.0.1") || 1234 != this.port) {
			this.close();
			return new RtlsdrSource(context, preferences);
		}
	}

	int frequencyCorrection = Integer.parseInt(preferences.getString(context.getString(R.string.pref_rtlsdr_frequencyCorrection), "0"));
	int frequencyOffset = Integer.parseInt(preferences.getString(context.getString(R.string.pref_rtlsdr_frequencyOffset), "0"));
	if (frequencyCorrection != frequencyCorrectionControl.get())
		frequencyCorrectionControl.set(frequencyCorrection);
	if (rxFrequency.getFrequencyOffset() != frequencyOffset)
		rxFrequency.setFrequencyOffset(frequencyOffset);
	return this;
}

public String getIpAddress() {
	return ipAddress;
}

public int getPort() {
	return port;
}

@Override
public <T extends Control> T getControl(Class<T> clazz) {
	return (T) controls.get(clazz);
}

@Override
public Collection<Control> getControls() {
	return Collections.unmodifiableCollection(controls.values());
}


@Override
public int getSampledPacketSize() {
	return PACKET_SIZE;
}

@Override
public byte[] getPacket(int timeout) {
	if (queue != null) {
		try {
			return queue.poll(timeout, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			Log.e(LOGTAG, "getPacket: Interrupted while polling packet from queue: " + e.getMessage());
		}
	} else {
		Log.e(LOGTAG, "getPacket: Queue is null");
	}
	return null;
}

@Override
public void returnPacket(byte[] buffer) {
	if (returnQueue != null) {
		returnQueue.offer(buffer);
	} else {
		Log.e(LOGTAG, "returnPacket: Return queue is null");
	}
}

@Override
public void startSampling() {
	if (receiverThread != null) {
		Log.e(LOGTAG, "startSampling: receiver thread still running.");
		reportError("Could not start sampling");
		return;
	}

	if (isOpen()) {
		// start ReceiverThread:
		receiverThread = new ReceiverThread(inputStream, returnQueue, queue);
		receiverThread.start();
	}
}

@Override
public void stopSampling() {
	// stop and join receiver thread:
	if (receiverThread != null) {
		receiverThread.stopReceiving();
		// Join the thread only if the current thread is NOT the receiverThread ^^
		if (!Thread.currentThread().getName().equals(receiverThread.threadName)) {
			try {
				receiverThread.join();
			}
			catch (InterruptedException e) {
				Log.e(LOGTAG, "stopSampling: Interrupted while joining receiver thread: " + e.getMessage());
			}
		}
		receiverThread = null;
	}
}

@Override
public int fillPacketIntoSamplePacket(byte[] packet, SamplePacket samplePacket) {
	return this.iqConverter.fillPacketIntoSamplePacket(packet, samplePacket);
}

@Override
public int mixPacketIntoSamplePacket(byte[] packet, SamplePacket samplePacket, long channelFrequency) {
	return this.iqConverter.mixPacketIntoSamplePacket(packet, samplePacket, channelFrequency);
}

/**
 * Will empty the queue
 */
public void flushQueue() {
	byte[] buffer;

	for (int i = 0; i < QUEUE_SIZE; i++) {
		buffer = queue.poll();
		if (buffer == null)
			return; // we are done; the queue is empty.
		this.returnPacket(buffer);
	}
}


/**
 * This thread will read samples from the socket and put them in the queue
 */
private class ReceiverThread extends Thread {
	public String threadName = null;    // We save the thread name to check against it in the stopSampling() method
	private boolean stopRequested = false;
	private InputStream inputStream = null;
	private ArrayBlockingQueue<byte[]> inputQueue = null;
	private ArrayBlockingQueue<byte[]> outputQueue = null;

	public ReceiverThread(InputStream inputStream, ArrayBlockingQueue<byte[]> inputQueue, ArrayBlockingQueue<byte[]> outputQueue) {
		super("RtlSdr Reciever Thread");
		this.inputStream = inputStream;
		this.inputQueue = inputQueue;
		this.outputQueue = outputQueue;
	}

	public void stopReceiving() {
		this.stopRequested = true;
	}

	public void run() {
		byte[] buffer = null;
		int index = 0;
		int bytesRead = 0;

		Log.i(LOGTAG, "ReceiverThread started (Thread: " + this.getName() + ")");
		threadName = this.getName();

		while (!stopRequested) {
			try {
				// if buffer is null we request a new buffer from the inputQueue:
				if (buffer == null) {
					buffer = inputQueue.poll(1000, TimeUnit.MILLISECONDS);
					index = 0;
				}

				if (buffer == null) {
					Log.e(LOGTAG, "ReceiverThread: Couldn't get buffer from input queue. stop.");
					this.stopRequested = true;
					break;
				}

				// Read into the buffer from the inputStream:
				bytesRead = inputStream.read(buffer, index, buffer.length - index);

				if (bytesRead <= 0) {
					Log.e(LOGTAG, "ReceiverThread: Couldn't read data from input stream. stop.");
					this.stopRequested = true;
					break;
				}

				index += bytesRead;
				if (index == buffer.length) {
					// buffer is full. Send it to the output queue:
					outputQueue.offer(buffer);
					buffer = null;
				}

			}
			catch (InterruptedException e) {
				Log.e(LOGTAG, "ReceiverThread: Interrupted while waiting: " + e.getMessage());
				this.stopRequested = true;
				break;
			}
			catch (IOException e) {
				Log.e(LOGTAG, "ReceiverThread: Error while reading from socket: " + e.getMessage());
				reportError("Error while receiving samples.");
				this.stopRequested = true;
				break;
			}
			catch (NullPointerException e) {
				Log.e(LOGTAG, "ReceiverThread: Nullpointer! (Probably inputStream): " + Arrays.toString(e.getStackTrace()));
				this.stopRequested = true;
				break;
			}
		}
		// check if we still hold a buffer and return it to the input queue:
		if (buffer != null)
			inputQueue.offer(buffer);

		Log.i(LOGTAG, "ReceiverThread stopped (Thread: " + this.getName() + ")");
	}
}

}
