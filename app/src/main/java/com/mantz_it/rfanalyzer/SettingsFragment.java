package com.mantz_it.rfanalyzer;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.mantz_it.rfanalyzer.util.FileUtils;

import java.io.File;

import static com.mantz_it.rfanalyzer.ui.activity.SettingsActivity.PERMISSION_REQUEST_LOGGING_WRITE_FILES;

/**
 * <h1>RF Analyzer - Settings Fragment</h1>
 * <p>
 * Module:      SettingsFragment.java
 * Description: This fragment shows all app settings
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
public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener,
		Preference.OnPreferenceClickListener {

	private static final int FILESOURCE_RESULT_CODE = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		// Add click listener to preferences which use external apps:
		Preference pref = findPreference(getString(R.string.pref_filesource_file));
		pref.setOnPreferenceClickListener(this);
		pref = findPreference(getString(R.string.pref_showLog));
		pref.setOnPreferenceClickListener(this);

	}

	@Override
	public void onResume() {
		super.onResume();

		// Screen Orientation:
		String screenOrientation = getPreferenceScreen().getSharedPreferences().getString(getString(R.string.pref_screenOrientation), "auto");
		setScreenOrientation(screenOrientation);

		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		updateSummaries();
	}

	@Override
	public void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		// FileSource file:
		if (preference.getKey().equals(getString(R.string.pref_filesource_file))) {
			try {
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("*/*");
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				startActivityForResult(Intent.createChooser(intent, "Select a file (8-bit complex IQ samples)"), FILESOURCE_RESULT_CODE);

				// No error so far... let's dismiss the text input dialog:
				Dialog dialog = ((EditTextPreference) preference).getDialog();
				if (dialog != null)
					dialog.dismiss();
				return true;
			} catch (ActivityNotFoundException e) {
				Toast.makeText(SettingsFragment.this.getActivity(), "No file browser is installed!", Toast.LENGTH_LONG).show();
				// Note that there is still the text dialog visible for the user to input a file path... so no more error handling necessary
			}
			return false;
		}
		// Show Log:
		else if (preference.getKey().equals(getString(R.string.pref_showLog))) {
			try {
				String logfile = ((EditTextPreference) findPreference(getString(R.string.pref_logfile))).getText();
				Uri uri = Uri.fromFile(new File(logfile));
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(uri, "text/plain");
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				this.startActivity(intent);
				return true;
			} catch (ActivityNotFoundException e) {
				Toast.makeText(SettingsFragment.this.getActivity(), "No text viewer is installed!", Toast.LENGTH_LONG).show();
			}
			return false;
		}
		return false;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (data != null) {
			switch (requestCode) {
				case FILESOURCE_RESULT_CODE:
					Uri uri = data.getData();
					if (uri != null) {
						String filepath = FileUtils.getPath(getActivity(), uri);
						if (filepath != null) {
							((EditTextPreference) findPreference(getString(R.string.pref_filesource_file))).setText(filepath);
							updateFileSourcePrefs();
						} else {
							Toast.makeText(SettingsFragment.this.getActivity(), "Can't resolve file path from: " + uri.toString(), Toast.LENGTH_LONG).show();
						}
					}
					break;
				default:
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// update the summeries:
		updateSummaries();

		// Screen Orientation:
		String screenOrientation = sharedPreferences.getString(getString(R.string.pref_screenOrientation), "auto");
		setScreenOrientation(screenOrientation);

		// check WRITE_EXTERNAL_STORAGE permission if logging is active:
		if(sharedPreferences.getBoolean(getString(R.string.pref_logging), false)) {
			if (ContextCompat.checkSelfPermission(this.getActivity(), "android.permission.WRITE_EXTERNAL_STORAGE")
					!= PackageManager.PERMISSION_GRANTED) {
				// request permission:
				ActivityCompat.requestPermissions(this.getActivity(), new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
						PERMISSION_REQUEST_LOGGING_WRITE_FILES);
			}
		}
	}

	/**
	 * Will go through each preference element and initialize/update the summary according to its value.
	 *
	 * @note this will also correct invalid user inputs on EdittextPreferences!
	 */
	public void updateSummaries() {
		// Source Type
		ListPreference listPref = (ListPreference) findPreference(getString(R.string.pref_sourceType));
		listPref.setSummary(getString(R.string.pref_sourceType_summ, listPref.getEntry()));

		// FileSource Frequency
		EditTextPreference editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_filesource_frequency));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText(getString(R.string.pref_filesource_frequency_default));
		editTextPref.setSummary(getString(R.string.pref_filesource_frequency_summ, editTextPref.getText()));

		// FileSource Sample Rate
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_filesource_sampleRate));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText(getString(R.string.pref_filesource_sampleRate_default));
		editTextPref.setSummary(getString(R.string.pref_filesource_sampleRate_summ, editTextPref.getText()));

		// FileSource File
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_filesource_file));
		editTextPref.setSummary(getString(R.string.pref_filesource_file_summ, editTextPref.getText()));

		// FileSource Format
		listPref = (ListPreference) findPreference(getString(R.string.pref_filesource_format));
		listPref.setSummary(getString(R.string.pref_filesource_format_summ, listPref.getEntry()));

		// HackRF frequency shift
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hackrf_frequencyOffset));
		if(editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hackrf_frequencyOffset_summ, editTextPref.getText()));

		// RTL-SDR IP
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_rtlsdr_ip));
		editTextPref.setSummary(getString(R.string.pref_rtlsdr_ip_summ, editTextPref.getText()));

		// RTL-SDR Port
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_rtlsdr_port));
		editTextPref.setSummary(getString(R.string.pref_rtlsdr_port_summ, editTextPref.getText()));

		// RTL-SDR frequency correction
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_rtlsdr_frequencyCorrection));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText(getString(R.string.pref_rtlsdr_frequencyCorrection_default));
		editTextPref.setSummary(getString(R.string.pref_rtlsdr_frequencyCorrection_summ, editTextPref.getText()));

		// RTL-SDR frequency shift
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_rtlsdr_frequencyOffset));
		if(editTextPref.getText().length() == 0)

			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_rtlsdr_frequencyOffset_summ, editTextPref.getText()));

		// HiQSDR RX frequency
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_rx_frequency));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_rx_frequency_summ, editTextPref.getText()));

		// HiQSDR TX frequency
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_tx_frequency));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_tx_frequency_summ, editTextPref.getText()));

		// HiQSDR sample rate
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_sampleRate));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_sampleRate_summ, editTextPref.getText()));

		// HiQSDR TX mode
		listPref = (ListPreference) findPreference(getString(R.string.pref_hiqsdr_tx_mode));
		listPref.setSummary(getString(R.string.pref_hiqsdr_tx_mode_summ, listPref.getEntry()));

		// HiQSDR firmware version
		listPref = (ListPreference) findPreference(getString(R.string.pref_hiqsdr_firmware));
		listPref.setSummary(getString(R.string.pref_hiqsdr_firmware_summ, listPref.getEntry()));

		// HiQSDR antenna
		listPref = (ListPreference) findPreference(getString(R.string.pref_hiqsdr_antenna));
		listPref.setSummary(getString(R.string.pref_hiqsdr_antenna_summ, listPref.getEntry()));

		// HiQSDR IP address
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_ip));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_ip_summ, editTextPref.getText()));

		// HiQSDR command port
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_command_port));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_command_port_summ, editTextPref.getText()));

		// HiQSDR RX port
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_rx_port));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_rx_port_summ, editTextPref.getText()));

		// HiQSDR TX port
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_tx_port));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_tx_port_summ, editTextPref.getText()));

		// HiQSDR RX frequency
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_rx_frequency));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_rx_frequency_summ, editTextPref.getText()));

		// HiQSDR TX frequency
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_tx_frequency));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_tx_frequency_summ, editTextPref.getText()));

		// HiQSDR sample rate
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_sampleRate));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_sampleRate_summ, editTextPref.getText()));

		// HiQSDR TX mode
		listPref = (ListPreference) findPreference(getString(R.string.pref_hiqsdr_tx_mode));
		listPref.setSummary(getString(R.string.pref_hiqsdr_tx_mode_summ, listPref.getEntry()));

		// HiQSDR firmware version
		listPref = (ListPreference) findPreference(getString(R.string.pref_hiqsdr_firmware));
		listPref.setSummary(getString(R.string.pref_hiqsdr_firmware_summ, listPref.getEntry()));

		// HiQSDR antenna
		listPref = (ListPreference) findPreference(getString(R.string.pref_hiqsdr_antenna));
		listPref.setSummary(getString(R.string.pref_hiqsdr_antenna_summ, listPref.getEntry()));

		// HiQSDR IP address
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_ip));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_ip_summ, editTextPref.getText()));

		// HiQSDR command port
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_command_port));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_command_port_summ, editTextPref.getText()));

		// HiQSDR RX port
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_rx_port));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_rx_port_summ, editTextPref.getText()));

		// HiQSDR TX port
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_hiqsdr_tx_port));
		if (editTextPref.getText().length() == 0)
			editTextPref.setText("0");
		editTextPref.setSummary(getString(R.string.pref_hiqsdr_tx_port_summ, editTextPref.getText()));

		// FFT size
		listPref = (ListPreference) findPreference(getString(R.string.pref_fftSize));
		listPref.setSummary(getString(R.string.pref_fftSize_summ, listPref.getEntry()));

		// Color map type
		listPref = (ListPreference) findPreference(getString(R.string.pref_colorMapType));
		listPref.setSummary(getString(R.string.pref_colorMapType_summ, listPref.getEntry()));

		// FFT drawing type
		listPref = (ListPreference) findPreference(getString(R.string.pref_fftDrawingType));
		listPref.setSummary(getString(R.string.pref_fftDrawingType_summ, listPref.getEntry()));

		// Averaging
		listPref = (ListPreference) findPreference(getString(R.string.pref_averaging));
		listPref.setSummary(getString(R.string.pref_averaging_summ, listPref.getEntry()));

		// Screen Orientation
		listPref = (ListPreference) findPreference(getString(R.string.pref_screenOrientation));
		listPref.setSummary(getString(R.string.pref_screenOrientation_summ, listPref.getEntry()));

		// Spectrum Waterfall Ratio
		listPref = (ListPreference) findPreference(getString(R.string.pref_spectrumWaterfallRatio));
		listPref.setSummary(getString(R.string.pref_spectrumWaterfallRatio_summ, listPref.getEntry()));

		// Font Size
		listPref = (ListPreference) findPreference(getString(R.string.pref_fontSize));
		listPref.setSummary(getString(R.string.pref_fontSize_summ, listPref.getEntry()));

		// Frame Rate
		SwitchPreference switchPref = (SwitchPreference) findPreference(getString(R.string.pref_dynamicFrameRate));
		listPref = (ListPreference) findPreference(getString(R.string.pref_frameRate));
		if (switchPref.isChecked())
			listPref.setSummary(getString(R.string.pref_frameRate_summ, "auto"));
		else
			listPref.setSummary(getString(R.string.pref_frameRate_summ, listPref.getEntry()));

		// Logfile
		editTextPref = (EditTextPreference) findPreference(getString(R.string.pref_logfile));
		editTextPref.setSummary(getString(R.string.pref_logfile_summ, editTextPref.getText()));

		// Shared preferences updated in e.g. the onRequestPermissionResult() method are
		// not automatically updated in the preference fragment gui. do it manually:
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
		switchPref = (SwitchPreference) findPreference(getString(R.string.pref_logging));
		switchPref.setChecked(preferences.getBoolean(getString(R.string.pref_logging), false));
	}

	/**
	 * Will set the screen orientation of the hosting activity
	 *
	 * @param orientation auto, landscape, portrait, reverse_landscape or reverse_portrait
	 */
	public void setScreenOrientation(String orientation) {
		switch (orientation) {
			case "auto":
				getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
				break;
			case "landscape":
				getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
			case "portrait":
				getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			case "reverse_landscape":
				getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
				break;
			case "reverse_portrait":
				getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
				break;
		}
	}

	/**
	 * Extract the path from an uri
	 * This code was published on StackOverflow by dextor
	 *
	 * @param contentUri uri that contains the file path
	 * @return absolute file path as string
	 */
	private String getRealPathFromURI(Uri contentUri) {
		String[] proj = {MediaStore.Images.Media.DATA};
		CursorLoader loader = new CursorLoader(this.getActivity(), contentUri, proj, null, null, null);
		Cursor cursor = loader.loadInBackground();
		int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		cursor.moveToFirst();
		return cursor.getString(column_index);
	}

	/**
	 * Will try to extract the file source preferences (frequency, sample rate, format) from the filename
	 */
	public void updateFileSourcePrefs() {
		EditTextPreference etp_filename = (EditTextPreference) findPreference(getString(R.string.pref_filesource_file));
		EditTextPreference etp_frequency = (EditTextPreference) findPreference(getString(R.string.pref_filesource_frequency));
		EditTextPreference etp_sampleRate = (EditTextPreference) findPreference(getString(R.string.pref_filesource_sampleRate));
		ListPreference lp_format = (ListPreference) findPreference(getString(R.string.pref_filesource_format));
		String filename = etp_filename.getText();

		// Format. Search for strings like hackrf, rtl-sdr, ...
		if (filename.matches(".*hackrf.*") || filename.matches(".*HackRF.*") ||
		    filename.matches(".*HACKRF.*") || filename.matches(".*hackrfone.*"))
			lp_format.setValue("0");
		if (filename.matches(".*rtlsdr.*") || filename.matches(".*rtl-sdr.*") ||
		    filename.matches(".*RTLSDR.*") || filename.matches(".*RTL-SDR.*"))
			lp_format.setValue("1");

		// Sampe Rate. Search for pattern XXXXXXXSps
		if (filename.matches(".*(_|-|\\s)([0-9]+)(sps|Sps|SPS).*"))
			etp_sampleRate.setText(filename.replaceFirst(".*(_|-|\\s)([0-9]+)(sps|Sps|SPS).*", "$2"));
		if (filename.matches(".*(_|-|\\s)([0-9]+)(msps|Msps|MSps|MSPS).*"))
			etp_sampleRate.setText(Integer.toString(Integer.parseInt(filename.replaceFirst(".*(_|-|\\s)([0-9]+)(msps|Msps|MSps|MSPS).*", "$2")) * 1000000));

		// Frequency. Search for pattern XXXXXXXHz
		if (filename.matches(".*(_|-|\\s)([0-9]+)(hz|Hz|HZ).*"))
			etp_frequency.setText(filename.replaceFirst(".*(_|-|\\s)([0-9]+)(hz|Hz|HZ).*", "$2"));
		if (filename.matches(".*(_|-|\\s)([0-9]+)(mhz|Mhz|MHz|MHZ).*"))
			etp_frequency.setText(Integer.toString(Integer.parseInt(filename.replaceFirst(".*(_|-|\\s)([0-9]+)(mhz|Mhz|MHz|MHZ).*", "$2")) * 1000000));
	}
}
