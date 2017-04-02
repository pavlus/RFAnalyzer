package com.mantz_it.rfanalyzer.ui.activity;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.mantz_it.rfanalyzer.AnalyzerProcessingLoop;
import com.mantz_it.rfanalyzer.BookmarksDialog;
import com.mantz_it.rfanalyzer.Demodulator;
import com.mantz_it.rfanalyzer.IQSource;
import com.mantz_it.rfanalyzer.R;
import com.mantz_it.rfanalyzer.RFControlInterface;
import com.mantz_it.rfanalyzer.Scheduler;
import com.mantz_it.rfanalyzer.device.file.FileIQSource;
import com.mantz_it.rfanalyzer.device.hackrf.HackrfSource;
import com.mantz_it.rfanalyzer.device.hiqsdr.HiqsdrSource;
import com.mantz_it.rfanalyzer.device.rtlsdr.RtlsdrSource;
import com.mantz_it.rfanalyzer.sdr.controls.RXFrequency;
import com.mantz_it.rfanalyzer.sdr.controls.RXSampleRate;
import com.mantz_it.rfanalyzer.ui.component.AnalyzerSurface;
import com.mantz_it.rfanalyzer.ui.util.Toaster;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * <h1>RF Analyzer - Main Activity</h1>
 * <p/>
 * Module:      MainActivity.java
 * Description: Main Activity of the RF Analyzer
 *
 * @author Dennis Mantz
 *         <p/>
 *         Copyright (C) 2014 Dennis Mantz
 *         License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *         <p/>
 *         This library is free software; you can redistribute it and/or
 *         modify it under the terms of the GNU General Public
 *         License as published by the Free Software Foundation; either
 *         version 2 of the License, or (at your option) any later version.
 *         <p/>
 *         This library is distributed in the hope that it will be useful,
 *         but WITHOUT ANY WARRANTY; without even the implied warranty of
 *         MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *         General Public License for more details.
 *         <p/>
 *         You should have received a copy of the GNU General Public
 *         License along with this library; if not, write to the Free Software
 *         Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
public class MainActivity extends AppCompatActivity implements IQSource.Callback, RFControlInterface {

private MenuItem mi_startStop = null;
private MenuItem mi_demodulationMode = null;
private MenuItem mi_record = null;
private FrameLayout fl_analyzerFrame = null;
private AnalyzerSurface analyzerSurface = null;
private AnalyzerProcessingLoop analyzerProcessingLoop = null;
private Scheduler scheduler = null;
private Demodulator demodulator = null;
private SharedPreferences preferences = null;
private Bundle savedInstanceState = null;
private Process logcat = null;
private String versionName = null;

private boolean running = false;
private File recordingFile = null;
private int demodulationMode = Demodulator.DEMODULATION_OFF;

private IQSource source = null;
private RXFrequency rxFrequency;
private RXSampleRate rxSampleRate;

private Toaster toaster;
private static final String LOGTAG = "MainActivity";
private static final String RECORDING_DIR = "RFAnalyzer";
public static final int RTL2832U_RESULT_CODE = 1234;    // arbitrary value, used when sending intent to RTL2832U
/**
 * arbitrary value, used when requesting permission to open file for the file source
 */
public static final int PERMISSION_REQUEST_FILE_SOURCE_READ_FILES = 1111;
/**
 * arbitrary value, used when requesting permission to write file for the recording feature
 */
public static final int PERMISSION_REQUEST_RECORDING_WRITE_FILES = 1112;
private static final int FILE_SOURCE = 0;
private static final int HACKRF_SOURCE = 1;
private static final int RTLSDR_SOURCE = 2;
private static final int HIQSDR_SOURCE = 3;

private static final String[] SOURCE_NAMES = new String[]{"filesource", "hackrf", "rtlsdr", "hiqsdr"};

@Override
protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	toaster = new Toaster(this);

	setContentView(R.layout.activity_main);
	this.savedInstanceState = savedInstanceState;

	// Set default Settings on first run:
	PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

	// Get reference to the shared preferences:
	preferences = PreferenceManager.getDefaultSharedPreferences(this); // todo: separate preferences files for different PreferenceUsers
	// Overwrite defaults for file paths in the preferences:
	String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();    // get the path to the ext. storage
	// File Source file:
	String defaultFile = getString(R.string.pref_filesource_file_default);
	if (preferences.getString(getString(R.string.pref_filesource_file), "").equals(defaultFile))
		preferences.edit().putString(getString(R.string.pref_filesource_file), extStorage + "/" + defaultFile).apply();
	// Log file:
	defaultFile = getString(R.string.pref_logfile_default);
	if (preferences.getString(getString(R.string.pref_logfile), "").equals(defaultFile))
		preferences.edit().putString(getString(R.string.pref_logfile), extStorage + "/" + defaultFile).apply();

	// Start logging if enabled:
	if (preferences.getBoolean(getString(R.string.pref_logging), false)) {
		if (ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE")
		    == PackageManager.PERMISSION_GRANTED) {
			try {
				File logfile = new File(preferences.getString(getString(R.string.pref_logfile), ""));
				logfile.getParentFile().mkdir();    // Create folder
				logcat = Runtime.getRuntime().exec("logcat -f " + logfile);
				Log.i("MainActivity", "onCreate: started logcat (" + logcat.toString() + ") to " + logfile);
			}
			catch (Exception e) {
				Log.e("MainActivity", "onCreate: Failed to start logging!");
			}
		} else {
			preferences.edit().putBoolean(getString(R.string.pref_logging), false).apply();
			Log.i(LOGTAG, "onCreate: deactivate logging because of missing storage permission.");
		}
	}
// Get version name:
	try {
		versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		Log.i(LOGTAG, "This is RF Analyzer " + versionName + " by Dennis Mantz (modified).");
	}
	catch (PackageManager.NameNotFoundException e) {
		Log.e(LOGTAG, "onCreate: Cannot read version name: " + e.getMessage());
	}

	// Get references to the GUI components:
	fl_analyzerFrame = (FrameLayout) findViewById(R.id.fl_analyzerFrame);

	// Create a analyzer surface:
	analyzerSurface = new AnalyzerSurface(this, this);
	analyzerSurface.init(preferences);

	// Put the analyzer surface in the analyzer frame of the layout:
	fl_analyzerFrame.addView(analyzerSurface);

	// Restore / Initialize the running state and the demodulator mode:
	if (savedInstanceState != null) {
		running = savedInstanceState.getBoolean(getString(R.string.save_state_running));
		demodulationMode = savedInstanceState.getInt(getString(R.string.save_state_demodulatorMode));

            /* BUGFIX / WORKAROUND:
             * The RTL2832U driver will not allow to close the socket and immediately start the driver
             * again to reconnect after an orientation change / app kill + restart.
             * It will report back in onActivityResult() with a -1 (not specified).
             *
             * Work-around:
             * 1) We won't restart the Analyzer if the current source is set to a local RTL-SDR instance:
             * 2) Delay the restart of the Analyzer after the driver was shut down correctly...
             */
		if (running && Integer.parseInt(preferences.getString(getString(R.string.pref_sourceType), "1")) == RTLSDR_SOURCE
		    && !preferences.getBoolean(getString(R.string.pref_rtlsdr_externalServer), false)) {
			// 1) don't start Analyzer immediately
			running = false;

			// Just inform the user about what is going on (why does this take so long? ...)
			toaster.showShort("Stopping and restarting RTL2832U driver...");

			// 2) Delayed start of the Analyzer:
			// todo: can we use notifyAll() instead of this?

			Thread timer = new Thread(() ->
			{
				try {
					Thread.sleep(1500);
					startAnalyzer();
				}
				catch (InterruptedException e) {
					Log.e(LOGTAG, "onCreate: (timer thread): Interrupted while sleeping.");
				}
			}, "Timer Thread");

			timer.start();
		}

	} else {
		// Set running to true if autostart is enabled (this will start the analyzer in onStart() )
		running = preferences.getBoolean((getString(R.string.pref_autostart)), false);
	}

	// Set the hardware volume keys to work on the music audio stream:
	setVolumeControlStream(AudioManager.STREAM_MUSIC);
}

@Override
protected void onDestroy() {
	super.onDestroy();
	// close source
	if (source != null && source.isOpen())
		source.close();

	// stop logging:
	if (logcat != null) {
		try {
			logcat.destroy();
			logcat.waitFor();
			Log.i(LOGTAG, "onDestroy: logcat exit value: " + logcat.exitValue());
		}
		catch (Exception e) {
			Log.e(LOGTAG, "onDestroy: couldn't stop logcat: " + e.getMessage());
		}
	}

	// shut down RTL2832U driver if running:
	if (running && Integer.parseInt(preferences.getString(getString(R.string.pref_sourceType), "1")) == RTLSDR_SOURCE
	    && !preferences.getBoolean(getString(R.string.pref_rtlsdr_externalServer), false)) {
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setClassName("marto.rtl_tcp_andro", "com.sdrtouch.rtlsdr.DeviceOpenActivity");
			intent.setData(Uri.parse("iqsrc://-x"));    // -x is invalid. will cause the driver to shut down (if running)
			startActivity(intent);
		}
		catch (ActivityNotFoundException e) {
			Log.e(LOGTAG, "onDestroy: RTL2832U is not installed");
		}
	}
}

// todo: inverse to analyzerSurface.saveState(Bundle)
@Override
protected void onSaveInstanceState(Bundle outState) {
	outState.putBoolean(getString(R.string.save_state_running), running);
	outState.putInt(getString(R.string.save_state_demodulatorMode), demodulationMode);
	// todo: also save source settings? definitely in need of interface handling settings
	if (analyzerSurface != null) {
		outState.putLong(getString(R.string.save_state_channelFrequency), analyzerSurface.getChannelFrequency());
		outState.putInt(getString(R.string.save_state_channelWidth), analyzerSurface.getChannelWidth());
		outState.putFloat(getString(R.string.save_state_squelch), analyzerSurface.getSquelch());
		outState.putLong(getString(R.string.save_state_virtualFrequency), analyzerSurface.getVirtualFrequency());
		outState.putInt(getString(R.string.save_state_virtualSampleRate), analyzerSurface.getVirtualSampleRate());
		outState.putFloat(getString(R.string.save_state_minDB), analyzerSurface.getMinDB());
		outState.putFloat(getString(R.string.save_state_maxDB), analyzerSurface.getMaxDB());
	}
}

@Override
public boolean onCreateOptionsMenu(Menu menu) {
	// Inflate the menu; this adds items to the action bar if it is present.
	getMenuInflater().inflate(R.menu.main, menu);
	// Get a reference to the start-stop button:
	mi_startStop = menu.findItem(R.id.action_startStop);
	mi_demodulationMode = menu.findItem(R.id.action_setDemodulation);
	mi_record = menu.findItem(R.id.action_record);

	// update the action bar icons and titles according to the app state:
	updateActionBar();
	return true;
}

@Override
public boolean onOptionsItemSelected(MenuItem item) {
	// Handle action bar item clicks here. The action bar will
	// automatically handle clicks on the Home/Up button, so long
	// as you specify a parent activity in AndroidManifest.xml.
	int id = item.getItemId();
	switch (id) {
		case R.id.action_startStop:
			if (running)
				stopAnalyzer();
			else
				startAnalyzer();
			break;
		case R.id.action_setDemodulation: showDemodulationDialog(); break;
		case R.id.action_setFrequency: tuneToFrequency(); break;
		case R.id.action_setGain: adjustGain(); break;
		case R.id.action_autoscale: analyzerSurface.autoscale(); break;
		case R.id.action_record:
			if (scheduler != null && scheduler.isRecording())
				stopRecording();
			else
				showRecordingDialog();
			break;
		case R.id.action_bookmarks: showBookmarksDialog(); break;
		case R.id.action_settings:
			Intent intentShowSettings = new Intent(getApplicationContext(), SettingsActivity.class);
			startActivity(intentShowSettings);
			break;
		case R.id.action_help:
			Intent intentShowHelp = new Intent(Intent.ACTION_VIEW);
			intentShowHelp.setData(Uri.parse(getString(R.string.help_url)));
			startActivity(intentShowHelp);
			break;
		case R.id.action_info: showInfoDialog(); break;
		default:
	}
	return true;
}

/**
 * Will update the action bar icons and titles according to the current app state
 */

private void updateActionBar() {
	this.runOnUiThread(() -> {
		// Set title and icon of the start/stop button according to the state:
		if (mi_startStop != null) {
			if (running) {
				mi_startStop.setTitle(R.string.action_stop);
				mi_startStop.setIcon(R.drawable.ic_action_pause);
			} else {
				mi_startStop.setTitle(R.string.action_start);
				mi_startStop.setIcon(R.drawable.ic_action_play);
			}
		}

		// Set title and icon for the demodulator mode button
		if (mi_demodulationMode != null) {
			int iconRes;
			int titleRes;
			switch (demodulationMode) {
				case Demodulator.DEMODULATION_OFF:
					iconRes = R.drawable.ic_action_demod_off;
					titleRes = R.string.action_demodulation_off;
					break;
				case Demodulator.DEMODULATION_AM:
					iconRes = R.drawable.ic_action_demod_am;
					titleRes = R.string.action_demodulation_am;
					break;
				case Demodulator.DEMODULATION_NFM:
					iconRes = R.drawable.ic_action_demod_nfm;
					titleRes = R.string.action_demodulation_nfm;
					break;
				case Demodulator.DEMODULATION_WFM:
					iconRes = R.drawable.ic_action_demod_wfm;
					titleRes = R.string.action_demodulation_wfm;
					break;
				case Demodulator.DEMODULATION_LSB:
					iconRes = R.drawable.ic_action_demod_lsb;
					titleRes = R.string.action_demodulation_lsb;
					break;
				case Demodulator.DEMODULATION_USB:
					iconRes = R.drawable.ic_action_demod_usb;
					titleRes = R.string.action_demodulation_usb;
					break;
				default:
					Log.e(LOGTAG, "updateActionBar: invalid mode: " + demodulationMode);
					iconRes = -1;
					titleRes = -1;
					break;
			}
			if (titleRes > 0 && iconRes > 0) {
				mi_demodulationMode.setTitle(titleRes);
				mi_demodulationMode.setIcon(iconRes);
			}
		}

		// Set title and icon of the record button according to the state:
		if (mi_record != null) {
			if (recordingFile != null) {
				mi_record.setTitle(R.string.action_recordOn);
				mi_record.setIcon(R.drawable.ic_action_record_on);
			} else {
				mi_record.setTitle(R.string.action_recordOff);
				mi_record.setIcon(R.drawable.ic_action_record_off);
			}
		}
	});

}

@Override
protected void onStart() {
	super.onStart();
	// Check if the user changed the preferences:
	checkForChangedPreferences();

	// Start the analyzer if running is true:
	if (running)
		startAnalyzer();

	// on the first time after the app was killed by the system, savedInstanceState will be
	// non-null and we restore the settings:
	if (savedInstanceState != null) {
		restoreAnalyzerSurface(analyzerSurface, savedInstanceState);
		if (demodulator != null && scheduler != null) {
			demodulator.setChannelWidth(savedInstanceState.getInt(getString(R.string.save_state_channelWidth)));
			scheduler.setChannelFrequency(savedInstanceState.getLong(getString(R.string.save_state_channelFrequency)));
		}
		savedInstanceState = null; // not needed any more...
	}
}

private void restoreAnalyzerSurface(AnalyzerSurface analyzerSurface, Bundle savedInstanceState) {
	analyzerSurface.setVirtualFrequency(savedInstanceState.getLong(getString(R.string.save_state_virtualFrequency)));
	analyzerSurface.setVirtualSampleRate(savedInstanceState.getInt(getString(R.string.save_state_virtualSampleRate)));
	analyzerSurface.setDBScale(savedInstanceState.getFloat(getString(R.string.save_state_minDB)),
			savedInstanceState.getFloat(getString(R.string.save_state_maxDB)));
	analyzerSurface.setChannelFrequency(savedInstanceState.getLong(getString(R.string.save_state_channelFrequency)));
	analyzerSurface.setChannelWidth(savedInstanceState.getInt(getString(R.string.save_state_channelWidth)));
	analyzerSurface.setSquelch(savedInstanceState.getFloat(getString(R.string.save_state_squelch)));
}

// todo: inverse to source.storeState(SharedPreferences.Editor)
@Override
protected void onStop() {
	super.onStop();
	boolean runningSaved = running;    // save the running state, to restore it after the app re-starts...
	stopAnalyzer();                    // will stop the processing loop, scheduler and source
	running = runningSaved;            // running will be saved in onSaveInstanceState()

	// safe preferences:
	if (source != null) {
		SharedPreferences.Editor edit = preferences.edit();
		if (source instanceof HackrfSource || source instanceof RtlsdrSource) {
			edit.putLong(getString(R.string.pref_frequency), rxFrequency.get());
			edit.putInt(getString(R.string.pref_sampleRate), rxSampleRate.get());
		} else { //todo: method to save settings by source
			edit.putString(getString(R.string.pref_hiqsdr_rx_frequency), Long.toString(rxFrequency.get()));
			edit.putString(getString(R.string.pref_hiqsdr_sampleRate), Integer.toString(rxSampleRate.get()));
		}
		edit.apply();
	}
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	super.onActivityResult(requestCode, resultCode, data);

	// err_info from RTL2832U:
	String[] rtlsdrErrInfo = {
			"permission_denied",
			"root_required",
			"no_devices_found",
			"unknown_error",
			"replug",
			"already_running"};

	switch (requestCode) {
		case RTL2832U_RESULT_CODE:
			// This happens if the RTL2832U driver was started.
			// We check for errors and print them:
			if (resultCode == RESULT_OK)
				Log.i(LOGTAG, "onActivityResult: RTL2832U driver was successfully started.");
			else {
				int errorId = -1;
				int exceptionCode = 0;
				String detailedDescription = null;
				if (data != null) {
					errorId = data.getIntExtra("marto.rtl_tcp_andro.RtlTcpExceptionId", -1);
					exceptionCode = data.getIntExtra("detailed_exception_code", 0);
					detailedDescription = data.getStringExtra("detailed_exception_message");
				}
				String errorMsg = "ERROR NOT SPECIFIED";
				if (errorId >= 0 && errorId < rtlsdrErrInfo.length)
					errorMsg = rtlsdrErrInfo[errorId];

				Log.e(LOGTAG, "onActivityResult: RTL2832U driver returned with error: " + errorMsg + " (" + errorId + ")"
				              + (detailedDescription != null ? ": " + detailedDescription + " (" + exceptionCode + ")" : ""));

				if (source != null && source instanceof RtlsdrSource) {
					toaster.showLong("Error with Source [" + source.getName() + "]: " + errorMsg + " (" + errorId + ")"
					                 + (detailedDescription != null ? ": " + detailedDescription + " (" + exceptionCode + ")" : ""));
					source.close();
				}
			}
			break;
	}
}

//@Override
public void onRequestPermissionsResult(int requestCode, String permissions[], @NonNull int[] grantResults) {
	switch (requestCode) {
		case PERMISSION_REQUEST_FILE_SOURCE_READ_FILES: {
			// If request is cancelled, the result arrays are empty.
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (source != null && source instanceof FileIQSource) {
					if (!source.open(this, this))
						Log.e(LOGTAG, "onRequestPermissionResult: source.open() exited with an error.");
				} else {
					Log.e(LOGTAG, "onRequestPermissionResult: source is null or of other type.");
				}
			}
		}
		break;
		case PERMISSION_REQUEST_RECORDING_WRITE_FILES: {
			// If request is cancelled, the result arrays are empty.
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				showRecordingDialog();
			}
		}
		break;
	}
	super.onRequestPermissionsResult(requestCode, permissions, grantResults);
}

@Override
public void onIQSourceReady(IQSource source) {    // is called after source.open()
	Log.i(LOGTAG, "onIQSourceReady: " + source.getName());
	if (running)
		startAnalyzer();    // will start the processing loop, scheduler and source
}

@Override
public void onIQSourceError(final IQSource source, final String message) {
	Log.e(LOGTAG, source.getName() + ": " + message);
	this.runOnUiThread(() -> toaster.showLong("Error with Source [" + source.getName() + "]: " + message));
	stopAnalyzer();

	if (this.source != null && this.source.isOpen())
		this.source.close();
}

/**
 * Reflection-based source settings updater
 *
 * @param clazz desired class of source
 * @return current source with updated settings, or new source if current source type isn't instance of desired source.
 */
protected void updateSourcePreferences(final Class<?> clazz) {
	// if src is of desired class -- just update
	if (clazz.isInstance(this.source)) {
		setSource(this.source.updatePreferences(this, preferences));
		analyzerSurface.setSource(this.source);
	} else {
		// create new
		this.source.close();
		final String msg;
		try {
			// we can't force sources to implement constructor with needed parameters,
			// to drop need of tracking all sources that could be added later just use reflection
			// to call constructor with current Context and SharedPreferences and let source configure itself
			Constructor ctor = clazz.getDeclaredConstructor(Context.class, SharedPreferences.class);
			ctor.setAccessible(true);
			setSource((IQSource) ctor.newInstance(this, preferences));
			analyzerSurface.setSource(this.source);
			return;
		}
		catch (NoSuchMethodException e) {
			Log.e(LOGTAG, "updateSourcePreferences: "
			              + (msg = "selected source doesn't have constructor with demanded parameters (Context, SharedPreferences)"));
			e.printStackTrace();
		}
		catch (IllegalAccessException e) {
			Log.e(LOGTAG, "updateSourcePreferences: "
			              + (msg = "selected source doesn't have accessible constructor with demanded parameters (Context, SharedPreferences)"));
			e.printStackTrace();
		}
		catch (InstantiationException e) {
			Log.e(LOGTAG, "updateSourcePreferences: "
			              + (msg = "selected source doesn't have accessible for MainActivity constructor with demanded parameters (Context, SharedPreferences)"));
			e.printStackTrace();
		}
		catch (InvocationTargetException e) {
			Log.e(LOGTAG, "updateSourcePreferences: "
			              + (msg = "source's constructor thrown exception: " + e.getMessage()));
			e.printStackTrace();
		}
		stopAnalyzer();
		this.runOnUiThread(() -> toaster.showLong("Error with instantiating source [" + clazz.getName() + "]: "));
		setSource(null);
	}
}

/**
 * Will check if any preference conflicts with the current state of the app and fix it
 */
public void checkForChangedPreferences() {
	int sourceType = Integer.parseInt(preferences.getString(getString(R.string.pref_sourceType), "1"));
	    /* todo: rework settings repository, so we could use reflection to instantiate source instead of hardcoded switch */
	    /* todo dependency injection*/
	if (source != null) {
		switch (sourceType) {
			case FILE_SOURCE: updateSourcePreferences(FileIQSource.class); break;
			case HACKRF_SOURCE: updateSourcePreferences(HackrfSource.class); break;
			case RTLSDR_SOURCE: updateSourcePreferences(RtlsdrSource.class); break;
			case HIQSDR_SOURCE: updateSourcePreferences(HiqsdrSource.class); break;
			default:
				Log.e(LOGTAG, "checkForChangedPreferences: selected source type (" + sourceType + "is not supported");
		}
	}

	if (analyzerSurface != null) {
		onPreferencesChanged(analyzerSurface, preferences);

	}

	// Screen Orientation:
	String screenOrientation = preferences.getString(getString(R.string.pref_screenOrientation), "auto").toLowerCase();
	int orientation;
	switch (screenOrientation) {
		case "landscape": orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; break;
		case "portrait": orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; break;
		case "reverse_landscape": orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE; break;
		case "reverse_portrait": orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT; break;
		default: case "auto": orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; break;
	}
	setRequestedOrientation(orientation);
}

public void onPreferencesChanged(AnalyzerSurface analyzerSurface, SharedPreferences preferences) {
	// todo: move this to AnalyzerSurface, create separate interface for updating settings?
	// All GUI settings will just be overwritten:
	analyzerSurface.setVerticalScrollEnabled(preferences.getBoolean(getString(R.string.pref_scrollDB), true));
	analyzerSurface.setVerticalZoomEnabled(preferences.getBoolean(getString(R.string.pref_zoomDB), true));
	analyzerSurface.setDecoupledAxis(preferences.getBoolean(getString(R.string.pref_decoupledAxis), false));
	analyzerSurface.setDisplayRelativeFrequencies(preferences.getBoolean(getString(R.string.pref_relativeFrequencies), false));
	analyzerSurface.setWaterfallColorMapType(Integer.parseInt(preferences.getString(getString(R.string.pref_colorMapType), "4")));
	analyzerSurface.setFftDrawingType(Integer.parseInt(preferences.getString(getString(R.string.pref_fftDrawingType), "2")));
	analyzerSurface.setAverageLength(Integer.parseInt(preferences.getString(getString(R.string.pref_averaging), "0")));
	analyzerSurface.setPeakHoldEnabled(preferences.getBoolean(getString(R.string.pref_peakHold), false));
	analyzerSurface.setFftRatio(Float.parseFloat(preferences.getString(getString(R.string.pref_spectrumWaterfallRatio), "0.5")));
	analyzerSurface.setFontSize(Integer.parseInt(preferences.getString(getString(R.string.pref_fontSize), "2")));
	analyzerSurface.setShowDebugInformation(preferences.getBoolean(getString(R.string.pref_showDebugInformation), false));
}

public void setSource(IQSource source) {
	//if (Proxy.isProxyClass(source.getClass()))
	this.source = source;
	//else this.source = MethodInterceptor.wrapWithLog(source, "IQSource", IQSource.class);
	this.rxFrequency = source.getControl(RXFrequency.class);
	this.rxSampleRate = source.getControl(RXSampleRate.class);
}

/**
 * Will create a IQ Source instance according to the user settings.
 *
 * @return true on success; false on error
 */
public boolean createSource() {
	int sourceType = Integer.parseInt(preferences.getString(getString(R.string.pref_sourceType), "1"));
	// todo: rework settings repository to use reflection instead of hardcoded switch statement
	switch (sourceType) {
		case FILE_SOURCE: setSource(new FileIQSource(this, preferences)); break;
		case HACKRF_SOURCE: setSource(new HackrfSource(this, preferences)); break;
		case RTLSDR_SOURCE: setSource(new RtlsdrSource(this, preferences)); break;
		case HIQSDR_SOURCE: setSource(new HiqsdrSource(this, preferences)); break;
		default:
			Log.e(LOGTAG, "createSource: Invalid source type: " + sourceType);
			return false;
	}

	// inform the analyzer surface about the new source
	analyzerSurface.setSource(source);

	return true;
}

/**
 * Will open the IQ Source instance.
 * Note: some sources need special treatment on opening, like the rtl-sdr source.
 *
 * @return true on success; false on error
 */
public boolean openSource() {
	int sourceType = Integer.parseInt(preferences.getString(getString(R.string.pref_sourceType), "1"));

	switch (sourceType) {
		case FILE_SOURCE:
			if (source != null && source instanceof FileIQSource) {
				// Check for the READ_EXTERNAL_STORAGE permission:
				if (ContextCompat.checkSelfPermission(this, "android.permission.READ_EXTERNAL_STORAGE")
				    != PackageManager.PERMISSION_GRANTED) {
					// request permission:
					ActivityCompat.requestPermissions(this,
							new String[]{"android.permission.READ_EXTERNAL_STORAGE"},
							PERMISSION_REQUEST_FILE_SOURCE_READ_FILES);
					return true; // return and wait for the response (is handled in onRequestPermissionResult())
				} else {
					return source.open(this, this);
				}
			} else {
				Log.e(LOGTAG, "openSource: sourceType is FILE_SOURCE, but source is null or of other type.");
				return false;
			}
		case HACKRF_SOURCE:
			if (source != null && source instanceof HackrfSource)
				return source.open(this, this);
			else {
				Log.e(LOGTAG, "openSource: sourceType is HACKRF_SOURCE, but source is null or of other type.");
				return false;
			}
		case RTLSDR_SOURCE:
			if (source != null && source instanceof RtlsdrSource) {
				// todo: let RTL-SDR manage it dependencies
				// We might need to start the driver:
				if (!preferences.getBoolean(getString(R.string.pref_rtlsdr_externalServer), false)) {
					// start local rtl_tcp instance:
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setClassName("marto.rtl_tcp_andro", "com.sdrtouch.rtlsdr.DeviceOpenActivity");
						intent.setData(Uri.parse("iqsrc://-a 127.0.0.1 -p 1234 -n 1"));
						startActivityForResult(intent, RTL2832U_RESULT_CODE);
					}
					catch (ActivityNotFoundException e) {
						Log.e(LOGTAG, "createSource: RTL2832U is not installed");

						// Show a dialog that links to the play market:
						new AlertDialog.Builder(this)
								.setTitle("RTL2832U driver not installed!")
								.setMessage("You need to install the (free) RTL2832U driver to use RTL-SDR dongles.")
								.setPositiveButton("Install from Google Play", (dialog, whichButton) -> {
									Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=marto.rtl_tcp_andro"));
									startActivity(marketIntent);
								})
								.setNegativeButton("Cancel", (dialog, whichButton) -> {
									// do nothing
								})
								.show();
						return false;
					}
				}

				return source.open(this, this);
			} else {
				Log.e(LOGTAG, "openSource: sourceType is RTLSDR_SOURCE, but source is null or of other type.");
				return false;
			}
		case HIQSDR_SOURCE:
			if (source != null && source instanceof HiqsdrSource)
				return source.open(this, this);
			else {
				Log.e(LOGTAG, "openSource: sourceType is HIQSDR_SOURCE, but source is null or of other type.");
				return false;
			}

		default:
			Log.e(LOGTAG, "openSource: Invalid source type: " + sourceType);
			return false;
	}
}

/**
 * Will stop the RF Analyzer. This includes shutting down the scheduler (which turns of the
 * source), the processing loop and the demodulator if running.
 */
public void stopAnalyzer() {
	// Stop the Scheduler if running:
	if (scheduler != null) {
		// Stop recording in case it is running:
		stopRecording();
		scheduler.stopScheduler();
	}

	// Stop the Processing Loop if running:
	if (analyzerProcessingLoop != null)
		analyzerProcessingLoop.stopLoop();

	// Stop the Demodulator if running:
	if (demodulator != null)
		demodulator.stopDemodulator();

	// Wait for the scheduler to stop:
	if (scheduler != null && !scheduler.getName().equals(Thread.currentThread().getName())) {
		try {
			scheduler.join();
		}
		catch (InterruptedException e) {
			Log.e(LOGTAG, "stopAnalyzer: Error while stopping Scheduler.");
		}
	}

	// Wait for the processing loop to stop
	if (analyzerProcessingLoop != null) {
		try {
			analyzerProcessingLoop.join();
		}
		catch (InterruptedException e) {
			Log.e(LOGTAG, "stopAnalyzer: Error while stopping Processing Loop.");
		}
	}

	// Wait for the demodulator to stop
	if (demodulator != null) {
		try {
			demodulator.join();
		}
		catch (InterruptedException e) {
			Log.e(LOGTAG, "stopAnalyzer: Error while stopping Demodulator.");
		}
	}

	running = false;

	// update action bar icons and titles:
	updateActionBar();

	// allow screen to turn off again:
	this.runOnUiThread(() -> getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
}

/**
 * Will start the RF Analyzer. This includes creating a source (if null), open a source
 * (if not open), starting the scheduler (which starts the source) and starting the
 * processing loop.
 */
public void startAnalyzer() {
	this.stopAnalyzer();    // Stop if running; This assures that we don't end up with multiple instances of the thread loops

	// Retrieve fft size and frame rate from the preferences
	int fftSize = Integer.parseInt(preferences.getString(getString(R.string.pref_fftSize), "1024"));
	int frameRate = Integer.parseInt(preferences.getString(getString(R.string.pref_frameRate), "1"));
	boolean dynamicFrameRate = preferences.getBoolean(getString(R.string.pref_dynamicFrameRate), true);

	running = true;

	if (source == null) {
		if (!this.createSource())
			return;
	}

	// check if the source is open. if not, open it!
	if (!source.isOpen()) {
		if (!openSource()) {
			toaster.showLong("Source not available (" + source.getName() + ")");
			running = false;
			return;
		}
		return;    // we have to wait for the source to become ready... onIQSourceReady() will call startAnalyzer() again...
	}

	// Create a new instance of Scheduler and Processing Loop:
	scheduler = new Scheduler(fftSize, source);
	analyzerProcessingLoop = new AnalyzerProcessingLoop(
			analyzerSurface,            // Reference to the Analyzer Surface
			fftSize,                    // FFT size
			scheduler.getFftOutputQueue(), // Reference to the input queue for the processing loop
			scheduler.getFftInputQueue()); // Reference to the buffer-pool-return queue
	if (dynamicFrameRate)
		analyzerProcessingLoop.setDynamicFrameRate(true);
	else {
		analyzerProcessingLoop.setDynamicFrameRate(false);
		analyzerProcessingLoop.setFrameRate(frameRate);
	}

	// Start both threads:
	scheduler.start();
	analyzerProcessingLoop.start();

	scheduler.setChannelFrequency(analyzerSurface.getChannelFrequency());

	// Start the demodulator thread:
	demodulator = new Demodulator(scheduler.getDemodOutputQueue(), scheduler.getDemodInputQueue(), source.getSampledPacketSize());
	demodulator.start();

	// Set the demodulation mode (will configure the demodulator correctly)
	this.setDemodulationMode(demodulationMode);

	// update the action bar icons and titles:
	updateActionBar();

	// Prevent the screen from turning off:
	this.runOnUiThread(() -> getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
}

/**
 * Will pop up a dialog to let the user choose a demodulation mode.
 */
private void showDemodulationDialog() {
	if (scheduler == null || demodulator == null || source == null) {
		toaster.showLong("Analyzer must be running to change modulation mode");
		return;
	}

	new AlertDialog.Builder(this)
			.setTitle("Select a demodulation mode:")
			.setSingleChoiceItems(R.array.demodulation_modes, demodulator.getDemodulationMode(), (dialog, which) -> {
				setDemodulationMode(which);
				dialog.dismiss();
			})
			.show();
}

/**
 * Will set the modulation mode to the given value. Takes care of adjusting the
 * scheduler and the demodulator respectively and updates the action bar menu item.
 *
 * @param mode Demodulator.DEMODULATION_OFF, *_AM, *_NFM, *_WFM
 */
public void setDemodulationMode(int mode) {
	if (scheduler == null || demodulator == null || source == null) {
		Log.e(LOGTAG, "setDemodulationMode: scheduler/demodulator/source is null");
		return;
	}

	// (de-)activate demodulation in the scheduler and set the sample rate accordingly:
	if (mode == Demodulator.DEMODULATION_OFF) {
		scheduler.setDemodulationActivated(false);
	} else {
		if (recordingFile != null && !Demodulator.supportsSampleRate(mode, rxSampleRate.get())) {
			// We are recording at an incompatible sample rate right now.
			Log.i(LOGTAG, "setDemodulationMode: Recording is running at "
			              + rxSampleRate.get() + " Sps, but demodulator doesn't support it");
			runOnUiThread(() -> toaster.showLong("Recording is running at incompatible sample rate for demodulation!"));
			return;
		}

		// Verify that the source supports the sample rate:
		if (!Demodulator.supportsSampleRate(mode, rxSampleRate.get())) {
			Log.e(LOGTAG, "setDemodulationMode: demodulator doesn't support selected sample rate");
			toaster.showLong("Demodulator doesn't support current sample rate: " +
			                 rxSampleRate.get() / 1000 + " Ksps)");
			scheduler.setDemodulationActivated(false);
			mode = Demodulator.DEMODULATION_OFF;    // deactivate demodulation...
		} else
			scheduler.setDemodulationActivated(true);

	}

	// set demodulation mode in demodulator:
	demodulator.setDemodulationMode(mode);
	this.demodulationMode = mode;    // save the setting

	// disable/enable demodulation view in surface:
	if (mode == Demodulator.DEMODULATION_OFF) {
		analyzerSurface.setDemodulationEnabled(false);
	} else {
		analyzerSurface.setDemodulationEnabled(true);    // will re-adjust channel freq, width and squelch,
		// if they are outside the current viewport and update the
		// demodulator via callbacks.
		analyzerSurface.setShowLowerBand(mode != Demodulator.DEMODULATION_USB);        // show lower side band if not USB
		analyzerSurface.setShowUpperBand(mode != Demodulator.DEMODULATION_LSB);        // show upper side band if not LSB
	}

	// update action bar:
	updateActionBar();
}

/**
 * Will pop up a dialog to let the user input a new frequency.
 * Note: A frequency can be entered either in Hz or in MHz. If the input value
 * is a number smaller than the maximum frequency of the source in MHz, then it
 * is interpreted as a frequency in MHz. Otherwise it will be handled as frequency
 * in Hz.
 */
private void tuneToFrequency() {
	if (source == null)
		return;

	// calculate max frequency of the source in MHz:
	final double maxFreqMHz = rxFrequency.getMax() / 1000000f;

	final LinearLayout ll_view = (LinearLayout) this.getLayoutInflater().inflate(R.layout.tune_to_frequency, null);
	final EditText et_frequency = (EditText) ll_view.findViewById(R.id.et_tune_to_frequency);
	final Spinner sp_unit = (Spinner) ll_view.findViewById(R.id.sp_tune_to_frequency_unit);
	final CheckBox cb_bandwidth = (CheckBox) ll_view.findViewById(R.id.cb_tune_to_frequency_bandwidth);
	final EditText et_bandwidth = (EditText) ll_view.findViewById(R.id.et_tune_to_frequency_bandwidth);
	final Spinner sp_bandwidthUnit = (Spinner) ll_view.findViewById(R.id.sp_tune_to_frequency_bandwidth_unit);
	final TextView tv_warning = (TextView) ll_view.findViewById(R.id.tv_tune_to_frequency_warning);

	// Show warning if we are currently recording to file:
	if (recordingFile != null)
		tv_warning.setVisibility(View.VISIBLE);

	cb_bandwidth.setOnCheckedChangeListener((buttonView, isChecked) -> {
		et_bandwidth.setEnabled(isChecked);
		sp_bandwidthUnit.setEnabled(isChecked);
	});
	cb_bandwidth.toggle();    // to trigger the onCheckedChangeListener at least once to set inital state
	cb_bandwidth.setChecked(preferences.getBoolean(getString(R.string.pref_tune_to_frequency_setBandwidth), false));
	et_bandwidth.setText(preferences.getString(getString(R.string.pref_tune_to_frequency_bandwidth), "1"));
	sp_unit.setSelection(preferences.getInt(getString(R.string.pref_tune_to_frequency_unit), 0));
	sp_bandwidthUnit.setSelection(preferences.getInt(getString(R.string.pref_tune_to_frequency_bandwidthUnit), 0));

	new AlertDialog.Builder(this)
			.setTitle("Tune to Frequency")
			.setMessage(String.format("Frequency is %f MHz. Type a new Frequency: ", rxFrequency.get() / 1000000f))
			.setView(ll_view)
			.setPositiveButton("Set", (dialog, whichButton) -> {
				try {
					double newFreq = rxFrequency.get() / 1000000f;
					if (et_frequency.getText().length() != 0)
						newFreq = Double.valueOf(et_frequency.getText().toString());
					switch (sp_unit.getSelectedItemPosition()) {
						case 0: // MHz
							newFreq *= 1000000; break;
						case 1: // KHz
							newFreq *= 1000; break;
						default: // Hz
					}

					if (newFreq < maxFreqMHz)
						newFreq = newFreq * 1000000;
					if (newFreq <= rxFrequency.getMax() && newFreq >= rxFrequency.getMin()) {
						rxFrequency.set((long) newFreq);
						analyzerSurface.setVirtualFrequency((long) newFreq);
						if (demodulationMode != Demodulator.DEMODULATION_OFF)
							analyzerSurface.setDemodulationEnabled(true);    // This will re-adjust the channel freq correctly

						// Set bandwidth (virtual sample rate):
						if (cb_bandwidth.isChecked() && et_bandwidth.getText().length() != 0) {
							float bandwidth = Float.parseFloat(et_bandwidth.getText().toString());
							if (sp_bandwidthUnit.getSelectedItemPosition() == 0)            //MHz
								bandwidth *= 1000000;
							else if (sp_bandwidthUnit.getSelectedItemPosition() == 1)    //KHz
								bandwidth *= 1000;
							if (bandwidth > rxSampleRate.getMax())
								bandwidth = rxFrequency.getMax();
							rxSampleRate.set(rxSampleRate.getNextHigherOptimalSampleRate((int) bandwidth));
							analyzerSurface.setVirtualSampleRate((int) bandwidth);
						}
						// safe preferences:
						SharedPreferences.Editor edit = preferences.edit();
						edit.putInt(getString(R.string.pref_tune_to_frequency_unit), sp_unit.getSelectedItemPosition());
						edit.putBoolean(getString(R.string.pref_tune_to_frequency_setBandwidth), cb_bandwidth.isChecked());
						edit.putString(getString(R.string.pref_tune_to_frequency_bandwidth), et_bandwidth.getText().toString());
						edit.putInt(getString(R.string.pref_tune_to_frequency_bandwidthUnit), sp_bandwidthUnit.getSelectedItemPosition());
						edit.apply();

					} else {
						toaster.showLong("Frequency is out of the valid range: " + (long) newFreq + " Hz");
					}
				}
				catch (NumberFormatException e) {
					// todo: notify user
					Log.e(LOGTAG, "tuneToFrequency: Error while setting frequency: " + e.getMessage());
				}
			})
			.setNegativeButton("Cancel", (dialog, whichButton) -> {
				// do nothing
			})
			.show();
}

/**
 * Will pop up a dialog to let the user adjust gain settings
 */
private void adjustGain() {
	if (source == null)
		return;
	source.showGainDialog(this, preferences);
}

public void showRecordingDialog() {
	if (!running || scheduler == null || demodulator == null || source == null) {
		toaster.showLong("Analyzer must be running to start recording");
		return;
	}
	// Check for the WRITE_EXTERNAL_STORAGE permission:
	if (ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE")
	    != PackageManager.PERMISSION_GRANTED) {
		ActivityCompat.requestPermissions(this, new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
				PERMISSION_REQUEST_RECORDING_WRITE_FILES);
		return; // wait for the permission response (handled in onRequestPermissionResult())
	}

	final String externalDir = Environment.getExternalStorageDirectory().getAbsolutePath();
	final int[] supportedSampleRates = rxSampleRate.getSupportedSampleRates();
	final double maxFreqMHz = rxFrequency.getMax() / 1000000f; // max frequency of the source in MHz
	final int sourceType = Integer.parseInt(preferences.getString(getString(R.string.pref_sourceType), "1"));
	final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);

	// Get references to the GUI components:
	final ScrollView view = (ScrollView) this.getLayoutInflater().inflate(R.layout.start_recording, null);
	final EditText et_filename = (EditText) view.findViewById(R.id.et_recording_filename);
	final EditText et_frequency = (EditText) view.findViewById(R.id.et_recording_frequency);
	final Spinner sp_sampleRate = (Spinner) view.findViewById(R.id.sp_recording_sampleRate);
	final TextView tv_fixedSampleRateHint = (TextView) view.findViewById(R.id.tv_recording_fixedSampleRateHint);
	final CheckBox cb_stopAfter = (CheckBox) view.findViewById(R.id.cb_recording_stopAfter);
	final EditText et_stopAfter = (EditText) view.findViewById(R.id.et_recording_stopAfter);
	final Spinner sp_stopAfter = (Spinner) view.findViewById(R.id.sp_recording_stopAfter);

	// Setup the sample rate spinner:
	final ArrayAdapter<Integer> sampleRateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
	for (int sampR : supportedSampleRates)
		sampleRateAdapter.add(sampR);
	sampleRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	sp_sampleRate.setAdapter(sampleRateAdapter);

	// Add listener to the frequency textfield, the sample rate spinner and the checkbox:
	et_frequency.addTextChangedListener(new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}

		@Override
		public void afterTextChanged(Editable s) {
			if (et_frequency.getText().length() == 0)
				return;
			double freq = Double.parseDouble(et_frequency.getText().toString());
			if (freq < maxFreqMHz)
				freq = freq * 1000000;
			et_filename.setText(simpleDateFormat.format(new Date()) + "_" + SOURCE_NAMES[sourceType] + "_"
			                    + (long) freq + "Hz_" + sp_sampleRate.getSelectedItem() + "Sps.iq");
		}
	});
	sp_sampleRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			if (et_frequency.getText().length() == 0)
				return;
			double freq = Double.parseDouble(et_frequency.getText().toString());
			if (freq < maxFreqMHz)
				freq = freq * 1000000;
			et_filename.setText(simpleDateFormat.format(new Date()) + "_" + SOURCE_NAMES[sourceType] + "_"
			                    + (long) freq + "Hz_" + sp_sampleRate.getSelectedItem() + "Sps.iq");
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	});
	cb_stopAfter.setOnCheckedChangeListener((buttonView, isChecked) -> {
		et_stopAfter.setEnabled(isChecked);
		sp_stopAfter.setEnabled(isChecked);
	});

	// Set default frequency, sample rate and stop after values:
	et_frequency.setText(Long.toString(analyzerSurface.getVirtualFrequency()));
	int sampleRateIndex = 0;
	int lastSampleRate = preferences.getInt(getString(R.string.pref_recordingSampleRate), 1000000);
	for (; sampleRateIndex < supportedSampleRates.length; sampleRateIndex++) {
		if (supportedSampleRates[sampleRateIndex] >= lastSampleRate)
			break;
	}
	if (sampleRateIndex >= supportedSampleRates.length)
		sampleRateIndex = supportedSampleRates.length - 1;
	sp_sampleRate.setSelection(sampleRateIndex);
	cb_stopAfter.toggle(); // just to trigger the listener at least once!
	cb_stopAfter.setChecked(preferences.getBoolean(getString(R.string.pref_recordingStopAfterEnabled), false));
	et_stopAfter.setText(Integer.toString(preferences.getInt(getString(R.string.pref_recordingStopAfterValue), 10)));
	sp_stopAfter.setSelection(preferences.getInt(getString(R.string.pref_recordingStopAfterUnit), 0));

	// disable sample rate selection if demodulation is running:
	if (demodulationMode != Demodulator.DEMODULATION_OFF) {
		sampleRateAdapter.add(rxSampleRate.get());    // add the current sample rate in case it's not already in the list
		sp_sampleRate.setSelection(sampleRateAdapter.getPosition(rxSampleRate.get()));    // select it
		sp_sampleRate.setEnabled(false);    // disable the spinner
		tv_fixedSampleRateHint.setVisibility(View.VISIBLE);
	}

	// Show dialog:
	new AlertDialog.Builder(this)
			.setTitle("Start recording")
			.setView(view)
			.setPositiveButton("Record", (dialog, whichButton) -> {
						String filename = et_filename.getText().toString();
						final int stopAfterUnit = sp_stopAfter.getSelectedItemPosition();
						final int stopAfterValue = Integer.parseInt(et_stopAfter.getText().toString());
						//todo check filename

						// Set the frequency in the source:
						if (et_frequency.getText().length() == 0)
							return;
						double freq = Double.parseDouble(et_frequency.getText().toString());
						if (freq < maxFreqMHz)
							freq = freq * 1000000;
						if (freq <= rxFrequency.getMax() && freq >= rxFrequency.getMin())
							rxFrequency.set((long) freq);
						else {
							toaster.showLong("Frequency is invalid!");
							return;
						}

						// Set the sample rate (only if demodulator is off):
						if (demodulationMode == Demodulator.DEMODULATION_OFF)
							rxSampleRate.set((Integer) sp_sampleRate.getSelectedItem());

						// Open file and start recording:
						recordingFile = new File(externalDir + "/" + RECORDING_DIR + "/" + filename);
						recordingFile.getParentFile().mkdir();    // Create directory if it does not yet exist
						try {
							scheduler.startRecording(new BufferedOutputStream(new FileOutputStream(recordingFile)));
						}
						catch (FileNotFoundException e) {
							Log.e(LOGTAG, "showRecordingDialog: File not found: " + recordingFile.getAbsolutePath());
						}

						// safe preferences:
						SharedPreferences.Editor edit = preferences.edit();
						edit.putInt(getString(R.string.pref_recordingSampleRate), (Integer) sp_sampleRate.getSelectedItem());
						edit.putBoolean(getString(R.string.pref_recordingStopAfterEnabled), cb_stopAfter.isChecked());
						edit.putInt(getString(R.string.pref_recordingStopAfterValue), stopAfterValue);
						edit.putInt(getString(R.string.pref_recordingStopAfterUnit), stopAfterUnit);
						edit.apply();

						analyzerSurface.setRecordingEnabled(true);

						updateActionBar();

						// if stopAfter was selected, start thread to supervise the recording:
						if (cb_stopAfter.isChecked()) {
							final String recorderSuperviserName = "Supervisor Thread";
							Thread supervisorThread = new Thread(()-> {
									Log.i(LOGTAG, "recording_superviser: Supervisor Thread started. (Thread: " + recorderSuperviserName + ")");
									try {
										long startTime = System.currentTimeMillis();
										boolean stop = false;

										// We check once per half a second if the stop criteria is met:
										Thread.sleep(500);
										while (recordingFile != null && !stop) {
											switch (stopAfterUnit) {    // see arrays.xml - recording_stopAfterUnit
												case 0: /* MB */
													if (recordingFile.length() / 1000000 >= stopAfterValue)
														stop = true;
													break;
												case 1: /* GB */
													if (recordingFile.length() / 1000000000 >= stopAfterValue)
														stop = true;
													break;
												case 2: /* sec */
													if (System.currentTimeMillis() - startTime >= stopAfterValue * 1000)
														stop = true;
													break;
												case 3: /* min */
													if (System.currentTimeMillis() - startTime >= stopAfterValue * 1000 * 60)
														stop = true;
													break;
											}
										}
										// stop recording:
										stopRecording();
									}
									catch (InterruptedException e) {
										// todo: shouldn't we call stopRecording() here? how about finally{}?
										Log.e(LOGTAG, "recording_superviser: Interrupted!");
									}
									catch (NullPointerException e) {
										Log.e(LOGTAG, "recording_superviser: Recording file is null!");
									}
									Log.i(LOGTAG, "recording_superviser: Supervisor Thread stopped. (Thread: " + recorderSuperviserName + ")");

							}, recorderSuperviserName);
							supervisorThread.start();
						}
					}
			)
			.setNegativeButton("Cancel", (dialog, whichButton) -> {
				// do nothing
			})
			.show()
			.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
}

public void stopRecording() {
	if (scheduler.isRecording()) {
		scheduler.stopRecording();
	}
	if (recordingFile != null) {
		final String filename = recordingFile.getAbsolutePath();
		final long filesize = recordingFile.length() / 1000000;    // file size in MB
		runOnUiThread(() -> toaster.showLong("Recording stopped: " + filename + " (" + filesize + " MB)"));
		recordingFile = null;
		updateActionBar();
	}
	if (analyzerSurface != null)
		analyzerSurface.setRecordingEnabled(false);
}

public void showBookmarksDialog() {
	// show warning toast if recording is running:
	if (recordingFile != null)
		toaster.showLong("WARNING: Recording is running!");
	new BookmarksDialog(this, this);
}

public void showInfoDialog() {
	AlertDialog dialog = new AlertDialog.Builder(this)
			.setTitle(Html.fromHtml(getString(R.string.info_title, versionName)))
			.setMessage(Html.fromHtml(getString(R.string.info_msg_body)))
			.setPositiveButton("OK", (dialog1, whichButton) -> {
				// Do nothing
			})
			.create();
	dialog.show();

	// make links clickable:
	((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
}

@Override
public boolean updateDemodulationMode(int newDemodulationMode) {
	if (scheduler == null || demodulator == null || source == null) {
		Log.e(LOGTAG, "updateDemodulationMode: scheduler/demodulator/source is null (no demodulation running)");
		return false;
	}

	setDemodulationMode(newDemodulationMode);
	return true;
}

/**
 * Called by the analyzer surface after the user changed the channel width
 *
 * @param newChannelWidth new channel width (single sided) in Hz
 * @return true if channel width is valid; false if out of range
 */
@Override
public boolean updateChannelWidth(int newChannelWidth) {
	if (demodulator != null) {
		if (demodulator.setChannelWidth(newChannelWidth)) {
			analyzerSurface.setChannelWidth(newChannelWidth);
			return true;
		}
	}
	return false;
}

@Override
public boolean updateChannelFrequency(long newChannelFrequency) {
	if (scheduler != null) {
		scheduler.setChannelFrequency(newChannelFrequency);
		analyzerSurface.setChannelFrequency(newChannelFrequency);
		return true;
	}
	return false;
}

public boolean updateSourceFrequency(long newSourceFrequency) {
	if (source != null && newSourceFrequency <= rxFrequency.getMax()
	    && newSourceFrequency >= rxFrequency.getMin()) {
		rxFrequency.set(newSourceFrequency);
		analyzerSurface.setVirtualFrequency(newSourceFrequency);
		return true;
	}
	return false;
}

public boolean updateSampleRate(int newSampleRate) {
	if (source != null) {
		if (scheduler == null || !scheduler.isRecording()) {
			rxSampleRate.set(newSampleRate);
			return true;
		}
	}
	return false;
}

@Override
public void updateSquelch(float newSquelch) {
	analyzerSurface.setSquelch(newSquelch);
}

@Override
public boolean updateSquelchSatisfied(boolean squelchSatisfied) {
	if (scheduler != null) {
		scheduler.setSquelchSatisfied(squelchSatisfied);
		return true;
	}
	return false;
}

@Override
public int requestCurrentChannelWidth() {
	if (demodulator != null)
		return demodulator.getChannelWidth();
	else
		return -1;
}

public long requestCurrentChannelFrequency() {
	if (scheduler != null)
		return scheduler.getChannelFrequency();
	else
		return -1;
}

public int requestCurrentDemodulationMode() {
	return demodulationMode;
}

public float requestCurrentSquelch() {
	if (analyzerSurface != null)
		return analyzerSurface.getSquelch();
	else
		return Float.NaN;
}

public long requestCurrentSourceFrequency() {
	if (source != null)
		return rxFrequency.get();
	else
		return -1;
}

public int requestCurrentSampleRate() {
	if (source != null)
		return rxSampleRate.get();
	else
		return -1;
}

public long requestMaxSourceFrequency() {
	if (source != null)
		return rxFrequency.getMax();
	else
		return -1;
}

public int[] requestSupportedSampleRates() {
	if (source != null)
		return rxSampleRate.getSupportedSampleRates();
	else
		return null;
}
}
