package com.mantz_it.rfanalyzer;

import android.graphics.Canvas;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h1>RF Analyzer - Analyzer Processing Loop</h1>
 *
 * Module:      AnalyzerProcessingLoop.java
 * Description: This Thread will fetch samples from the incoming queue (provided by the scheduler),
 *              do the signal processing (fft) and then forward the result to the AnalyzerSurface at a
 *              fixed rate. It stabilises the rate at which the fft is generated to give the
 *              waterfall display a linear time scale.
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2014 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
public class AnalyzerProcessingLoop extends Thread {
	private int fftSize = 0;				// Size of the FFT
	private int frameRate = 1;				// Frames per Second
	private double load = 0;				// Time_for_processing_and_drawing / Time_per_Frame
	private boolean stopRequested = true;	// Will stop the thread when set to true
	private static final String logtag = "AnalyzerProcessingLoop";

	private AnalyzerSurface view;
	private FFT fftBlock = null;
	private ArrayBlockingQueue<SamplePacket> inputQueue = null;		// queue that delivers sample packets
	private ArrayBlockingQueue<SamplePacket> returnQueue = null;	// queue to return unused buffers

	/**
	 * Constructor. Will initialize the member attributes.
	 *
	 * @param view			reference to the AnalyzerSurface for drawing
	 * @param fftSize		Size of the FFT
	 * @param inputQueue	queue that delivers sample packets
	 * @param returnQueue	queue to return unused buffers
	 */
	public AnalyzerProcessingLoop(AnalyzerSurface view, int fftSize,
				ArrayBlockingQueue<SamplePacket> inputQueue, ArrayBlockingQueue<SamplePacket> returnQueue) {
		this.view = view;

		// Check if fftSize is a power of 2
		int order = (int)(Math.log(fftSize) / Math.log(2));
		if(fftSize != (1<<order))
			throw new IllegalArgumentException("FFT size must be power of 2");
		this.fftSize = fftSize;

		this.fftBlock = new FFT(fftSize);
		this.inputQueue = inputQueue;
		this.returnQueue = returnQueue;
	}

	public int getFrameRate() {
		return frameRate;
	}

	public void setFrameRate(int frameRate) {
		this.frameRate = frameRate;
	}

	public int getFftSize() { return fftSize; }

	/**
	 * Will start the processing loop
	 */
	@Override
	public void start() {
		this.stopRequested = false;
		super.start();
	}

	/**
	 * Will set the stopRequested flag so that the processing loop will terminate
	 */
	public void stopLoop() {
		this.stopRequested = true;
	}

	/**
	 * @return true if loop is running; false if not.
	 */
	public boolean isRunning() {
		return !stopRequested;
	}

	@Override
	public void run() {
		Log.i(logtag,"Processing loop started. (Thread: " + this.getName() + ")");
		long startTime;		// timestamp when signal processing is started
		long sleepTime;		// time (in ms) to sleep before the next run to meet the frame rate
		long frequency;		// center frequency of the incoming samples
		int sampleRate;	// sample rate of the incoming samples

		while(!stopRequested) {
			// store the current timestamp
			startTime = System.currentTimeMillis();

			// fetch the next samples from the queue:
			SamplePacket samples;
			try {
				samples = inputQueue.poll(1000 / frameRate, TimeUnit.MILLISECONDS);
				if (samples == null) {
					Log.e(logtag, "run: Timeout while waiting on input data. stop.");
					this.stopLoop();
					break;
				}
			} catch (InterruptedException e) {
				Log.e(logtag, "run: Interrupted while polling from input queue. stop.");
				this.stopLoop();
				break;
			}

			frequency = samples.getFrequency();
			sampleRate = samples.getSampleRate();

			// do the signal processing:
			double[] mag = this.doProcessing(samples);

			// return samples to the buffer pool
			returnQueue.offer(samples);

			// Push the results on the surface:
			view.draw(mag, frequency, sampleRate, frameRate, load);

			// Calculate the remaining time in this frame (according to the frame rate) and sleep
			// for that time:
			sleepTime = (1000/frameRate)-(System.currentTimeMillis() - startTime);
			try {
				if (sleepTime > 0) {
					// load = processing_time / frame_duration
					load = (System.currentTimeMillis() - startTime) / (1000.0 / frameRate);
					Log.d(logtag,"Load: " + load + "; Sleep for " + sleepTime + "ms.");
					sleep(sleepTime);
				}
				else {
					Log.w(logtag, "Couldn't meet requested frame rate!");
					load = 1;
				}
			} catch (Exception e) {
				Log.e(logtag,"Error while calling sleep()");
			}
		}
		this.stopRequested = true;
		Log.i(logtag,"Processing loop stopped. (Thread: " + this.getName() + ")");
	}

	/**
	 * This method will do the signal processing (fft) on the given samples
	 *
	 * @param samples	input samples for the signal processing
	 * @return array with the magnitudes of the frequency spectrum (logarithmic)
	 */
	public double[] doProcessing(SamplePacket samples) {
		double[] mag = new double[samples.size()];		// Magnitude of the frequency spectrum

		// Multiply the samples with a Window function:
		this.fftBlock.applyWindow(samples.re(), samples.im());

		// Calculate the fft:
		this.fftBlock.fft(samples.re(), samples.im());

		// Calculate the logarithmic magnitude:
		for (int i = 0; i < samples.size(); i++) {
			// We have to flip both sides of the fft to draw it centered on the screen:
			int targetIndex = (i+samples.size()/2) % samples.size();

			// Calc the magnitude = log(  re^2 + im^2  )
			// note that we still have to divide re and im by the fft size
			mag[targetIndex] = Math.log(Math.pow(samples.re(i)/fftSize,2) + Math.pow(samples.im(i)/fftSize,2));
		}
		return mag;
	}
}