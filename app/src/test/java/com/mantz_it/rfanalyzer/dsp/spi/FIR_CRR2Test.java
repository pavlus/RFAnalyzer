package com.mantz_it.rfanalyzer.dsp.spi;

import com.mantz_it.rfanalyzer.dsp.FIRDesigner;
import com.mantz_it.rfanalyzer.dsp.Util;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by Pavel on 18.12.2016.
 */
public class FIR_CRR2Test {

	static final float LINEAR_FILTER_DELTA = 1E-6f;

	@Test
	public void apply() throws Exception {
		final int samplesCnt = 1024;
		final int decimation = 2;
		float[] taps = FIRDesigner.low_pass(1, samplesCnt, samplesCnt / (4 * decimation), samplesCnt / (4 * decimation), 60, Window.Type.WIN_BLACKMAN, 0);
		FIR_CRR golden = new FIR_CRR(taps, 2);
		//System.out.printf("%%Designed filter %s linear phase response.\n", FilterBuilder.isLinearPhase(golden.getTaps(),1e-6f)?"has":"has not");
		FIR_CRR_LinearPhase tested = new FIR_CRR_LinearPhase(golden);
		//System.out.printf("TapsPrototype = %s;\n", Arrays.toString(golden.getTaps()));
		//System.out.printf("TapsCountPrototype = %d;\n", golden.tapsCount);
		//System.out.printf("TapsLinearPhase = %s;\n", Arrays.toString(tested.getTaps()));
		//System.out.printf("TapsCountLinearPhase = %d;\n", tested.tapsCount);
		float[] interleavedSamples = new float[samplesCnt << 1];
		Util.noise(interleavedSamples);
		Packet in = new Packet(interleavedSamples);
		float[] out_golden = Util.applyFilter(golden, in, samplesCnt, decimation);
		float[] out_tested = Util.applyFilter(tested, in, samplesCnt, decimation);
		Assert.assertArrayEquals(out_golden, out_tested, LINEAR_FILTER_DELTA);
		double err = 0;
		for (int i = 0; i < out_golden.length; ++i) {
			final float diff = out_tested[i] - out_golden[i];
			err += diff * diff;
		}
		Assert.assertEquals(err, 0, LINEAR_FILTER_DELTA);
		//System.out.printf("TotalError = %e;", err);
	}


}
