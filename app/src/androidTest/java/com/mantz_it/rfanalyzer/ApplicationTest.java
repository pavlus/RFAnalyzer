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
	rnd = new Random();
		testPacket = new byte[SAMPLES_IN_PACKET];
		test24bitPacket = new byte[SAMPLES_IN_PACKET * 3];
		rnd.nextBytes(testPacket);
		rnd.nextBytes(test24bitPacket);
}


public long testConverterPerformance(IQConverter converter, byte[] packet, SamplePacket samplePacket) {
	long start = System.currentTimeMillis();
	converter.fillPacketIntoSamplePacket(packet, samplePacket);
	return System.currentTimeMillis() - start;
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
	IQConverter converter = new Signed24BitIQConverter();
	SamplePacket sp = new SamplePacket(SAMPLES_IN_PACKET);
	long time = testConverterPerformance(converter, test24bitPacket, sp);
	System.out.println("Unsigned24BitIQConverter fills packet in " + time + " ms");
}
}
