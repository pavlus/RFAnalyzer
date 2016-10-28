package com.mantz_it.rfanalyzer;

import android.app.Application;
import android.test.ApplicationTestCase;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends ApplicationTestCase<Application> {
public ApplicationTest() {
	super(Application.class);
}

protected byte[] testPacket;
protected byte[] test24bitPacket;
protected Random rnd;

protected final int SAMPLES_IN_PACKET = 2 * (1 << 20);  // 2 MiB packet

@Override
public void setUp() throws Exception {
	super.setUp();
	rnd = new Random();/*
		testPacket = new byte[SAMPLES_IN_PACKET];
		test24bitPacket = new byte[SAMPLES_IN_PACKET * 3];
		rnd.nextBytes(testPacket);
		rnd.nextBytes(test24bitPacket);*/
}

public void testGetOptimalDecimation() {
	System.out.println("Testing getOptimalDecimationOutputRate()");

	int minRate = 4000;
	int maxRate = 196000;
	int idealRate = 44100;
	HiQSDRSource src = new HiQSDRSource("localhost", 0, 0, 0);
	int[] inputRates = src.getSupportedSampleRates();
	System.out.println("MinRate=" + minRate + " IdealRate=" + idealRate + " MaxRate=" + maxRate);
	LinkedList<Integer> factors = new LinkedList<>();
	for (int rate : inputRates) {
		System.out.println("InputRate=" + rate);
		int decimation = Decimator.getOptimalDecimationOutputRate(minRate, idealRate, maxRate, rate, factors);
		System.out.println("Calculated optimal output rate: " + decimation);
		System.out.println("Selected factors: " + Arrays.toString(factors.toArray()));
		factors.clear();
	}
}

public void testHiQSDRPacketCntr() {
	final byte[] buff = new byte[1442];
	HiQSDRSource src = new HiQSDRSource();
	src.previousPacketIdx = 0;
	for (int i = 1; i < 520; ++i) {
		buff[0] = (byte) (i & 0xff);
		final int m = src.updatePacketIndex(buff);
		if (m != 0)
			System.out.println("testHiQSDRPacketCntr: false positive ("
			                   + "i=" + i
			                   + ", missed=" + m
			                   + ", but must be 0).");
		//else System.out.println("testHiQSDRPacketCntr: ok = "+m);
	}
	for (int j = 2; j < 255; ++j) {
		src.previousPacketIdx = 0;
		for (int i = j; i < j * 520; i += j) {
			buff[0] = (byte) (i & 0xff);
			final byte prev = src.previousPacketIdx;
			final int m = src.updatePacketIndex(buff);
			final byte current = src.previousPacketIdx;
			if (m != (j - 1))
				System.out.println("testHiQSDRPacketCntr: false negative ("
				                   + "i=" + i
				                   + ", j=" + j
				                   + ",prev=" + prev
				                   + ", current=" + current
				                   + ", missed=" + m
				                   + ", must be " + (j - 1) + ").");
				/*else System.out.println("testHiQSDRPacketCntr: ok ("
				                        + "i=" + i
				                        + ", j=" + j
				                        + ",prev=" + prev
				                        + ", current=" + current
				                        + ", missed=" + m
				                        + ").");*/
		}
	}


}

public void testInitArrays() {
	System.out.println("Testing HiQSDR.initArrays()");
	HiQSDRSource.initArrays();
	System.out.println("\tSamplerate codes: " + Arrays.toString(HiQSDRSource.SAMPLE_RATE_CODES));
	System.out.println("\tSamplerates     : " + Arrays.toString(HiQSDRSource.SAMPLE_RATES));
	System.out.println("\tPairs:");
	for (int i = 0; i < HiQSDRSource.SAMPLE_RATE_CODES.length; ++i)
		System.out.println("\t\t" + HiQSDRSource.SAMPLE_RATE_CODES[i] + ':' + HiQSDRSource.SAMPLE_RATES[i]);
	System.out.println("---------------------------------------------------------------------");
}

public long testConverterPerformance(IQConverter converter, byte[] packet, SamplePacket samplePacket) {
	long start = System.currentTimeMillis();
	converter.fillPacketIntoSamplePacket(packet, samplePacket);
	return System.currentTimeMillis() - start;
}

public void testMinSumFactorsFuzzy() {
	final int COUNT = 100000;
	final int MAX = 1000000;
	double averageTime = 0;
	long timeAccum = 0;
	long minTime = 99999999;
	long maxTime = 0;
	//System.out.println("Running fuzzy test on minSumFactors function over " + COUNT + " random numbers [0:" + MAX + ')');
	for (int i = 1; i <= COUNT; ++i) {
		int next = i;//Math.abs(rnd.nextInt());
		next %= MAX;
		System.out.print("Test#" + i + ", n=" + next);
		long time = runMinSumFactorTestForNumber(next, 0);
		timeAccum += time;
		if (time > maxTime)
			maxTime = time;
		if (time < minTime)
			minTime = time;
		if (i % 1000 == 0) {
			averageTime = timeAccum / 1000;
			timeAccum = 0;
			System.out.println("Average time: " + averageTime + "; min time: " + minTime + "; max time: " + maxTime);
		}
	}

}


protected long runMinSumFactorTestForNumber(int n, int desiredMinSum) {
	TreeMap<Integer, Integer> out = new TreeMap<>();
	long elapsed;
	final long then = System.currentTimeMillis();
	int factorsCnt = Decimator.minSumFactors(n, out);
	System.out.print(" took " + (elapsed = System.currentTimeMillis() - then) + " ms ");
	if (desiredMinSum > 0) {
		System.out.print("Factors count: " + factorsCnt);
		int factorsSum = Decimator.factorsSum(out);
		System.out.print("; Factors sum: " + factorsSum);
		if (factorsSum != desiredMinSum)
			System.out.print(" But should be " + desiredMinSum);
	}
	System.out.print(" Result: ");
	int prod = 1;
	for (Map.Entry<Integer, Integer> factor : out.entrySet()) {
		for (int j = 0; j < factor.getValue(); ++j) {
			int k = factor.getKey();
			System.out.print(" " + k);
			prod *= k;
		}
	}
	System.out.println();
	if (prod != n) {
		System.out.println("Resulting product: " + prod + " BUT MUST BE " + n + '.');
		this.terminateApplication();
	}
	return elapsed;
}

public void testMinSumFactors() {
	int[] numbers = {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 16, 18, 20, 32, 41, 48, 64};
	int[] minSums = {2, 3, 4, 5, 5, 7, 6, 6, 7, 7, 8, 8, 8, 9, 10, 41, 11, 12};

	for (int i = 0; i < numbers.length; ++i) {
		System.out.print("Test#" + i + ", n=" + numbers[i]);
		runMinSumFactorTestForNumber(numbers[i], minSums[i]);
	}
}

public void testConverters() {
	System.out.println("Testing converters...");
	for (int i = 1; i <= 10; ++i) {
		System.out.println("Pass #" + i);
		testUnsigned24BitIQConverter();
		testUnsigned8BitIQConverter();
		testSigned8BitIQConverter();
	}
}

public void testSigned8BitIQConverter() {
	IQConverter converter = new Signed8BitIQConverter();
	SamplePacket sp = new SamplePacket(SAMPLES_IN_PACKET);
	long time = testConverterPerformance(converter, testPacket, sp);
	System.out.println("Signed8BitIQConverter fills packet in " + time + " ms");
}

public void testUnsigned8BitIQConverter() {
	IQConverter converter = new Unsigned8BitIQConverter();
	SamplePacket sp = new SamplePacket(SAMPLES_IN_PACKET);
	long time = testConverterPerformance(converter, testPacket, sp);
	System.out.println("Unsigned8BitIQConverter fills packet in " + time + " ms");
}

public void testUnsigned24BitIQConverter() {
	IQConverter converter = new Unsigned24BitIQConverter();
	SamplePacket sp = new SamplePacket(SAMPLES_IN_PACKET);
	long time = testConverterPerformance(converter, test24bitPacket, sp);
	System.out.println("Unsigned24BitIQConverter fills packet in " + time + " ms");
}
}