package com.mantz_it.rfanalyzer.ui.util;

import android.graphics.Color;

/**
 * Created by Pavel on 29.03.2017.
 */

public enum Colormap {
	JET() { // BLUE(0,0,1) - LIGHT_BLUE(0,1,1) - GREEN(0,1,0) - YELLOW(1,1,0) - RED(1,0,0)

		@Override
		public int[] getColormap() {
			int[] colormap = new int[256 * 4];
			for (int i = 0; i < 256; i++) colormap[i] = Color.argb(0xff, 0, i, 255);
			for (int i = 0; i < 256; i++)
				colormap[256 + i] = Color.argb(0xff, 0, 255, 255 - i);
			for (int i = 0; i < 256; i++) colormap[512 + i] = Color.argb(0xff, i, 255, 0);
			for (int i = 0; i < 256; i++)
				colormap[768 + i] = Color.argb(0xff, 255, 255 - i, 0);
			return colormap;
		}
	},
	HOT { // BLACK (0,0,0) - RED (1,0,0) - YELLOW (1,1,0) - WHITE (1,1,1)

		@Override
		public int[] getColormap() {
			int[] colormap = new int[256 * 3];
			for (int i = 0; i < 256; i++) colormap[i] = Color.argb(0xff, i, 0, 0);
			for (int i = 0; i < 256; i++) colormap[256 + i] = Color.argb(0xff, 255, i, 0);
			for (int i = 0; i < 256; i++) colormap[512 + i] = Color.argb(0xff, 255, 255, i);
			return colormap;
		}
	},
	OLD { // from version 1.00 :)

		@Override
		public int[] getColormap() {
			int[] colormap = new int[512];
			for (int i = 0; i < 512; i++) {
				int blue = i <= 255 ? i : 511 - i;
				int red = i <= 255 ? 0 : i - 256;
				colormap[i] = Color.argb(0xff, red, 0, blue);
			}
			return colormap;
		}
	},
	GQRX { // from https://github.com/csete/gqrx  -> qtgui/plotter.cpp

		@Override
		public int[] getColormap() {
			int[] colormap = new int[256];
			for (int i = 0; i < 256; i++) {
				if (i < 20)
					colormap[i] = Color.argb(0xff, 0, 0, 0); // level 0: black background
				else if ((i >= 20) && (i < 70))
					colormap[i] = Color.argb(0xff, 0, 0, 140 * (i - 20) / 50); // level 1: black -> blue
				else if ((i >= 70) && (i < 100))
					colormap[i] = Color.argb(0xff, 60 * (i - 70) / 30, 125 * (i - 70) / 30, 115 * (i - 70) / 30 + 140); // level 2: blue -> light-blue / greenish
				else if ((i >= 100) && (i < 150))
					colormap[i] = Color.argb(0xff, 195 * (i - 100) / 50 + 60, 130 * (i - 100) / 50 + 125, 255 - (255 * (i - 100) / 50)); // level 3: light blue -> yellow
				else if ((i >= 150) && (i < 250))
					colormap[i] = Color.argb(0xff, 255, 255 - 255 * (i - 150) / 100, 0); // level 4: yellow -> red
				else if (i >= 250)
					colormap[i] = Color.argb(0xff, 255, 255 * (i - 250) / 5, 255 * (i - 250) / 5); // level 5: red -> white
			}
			return colormap;
		}
	};

public abstract int[] getColormap();
}
