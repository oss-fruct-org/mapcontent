package org.fruct.oss.mapcontent.content.helper;

import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import org.fruct.oss.mapcontent.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WarnDialog extends DialogFragment {
	private static Logger log = LoggerFactory.getLogger(WarnDialog.class);
	private CheckBox checkbox;

	private int messageId;
	private int titleId;
	private String disablePref;
	private PendingIntent onAcceptIntent;

	public static WarnDialog newInstance(@StringRes int messageId, @StringRes int titleId,
								  String disablePref, PendingIntent onAcceptIntent) {
		Bundle bundle = new Bundle();
		bundle.putInt("messageId", messageId);
		bundle.putInt("titleId", titleId);
		bundle.putString("disablePref", disablePref);
		bundle.putParcelable("onAcceptIntent", onAcceptIntent);

		WarnDialog dialog = new WarnDialog();
		dialog.setArguments(bundle);
		return dialog;
	}

	public WarnDialog() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		messageId = getArguments().getInt("messageId");
		titleId = getArguments().getInt("titleId");
		disablePref = getArguments().getString("disablePref");
		onAcceptIntent = getArguments().getParcelable("onAcceptIntent");

		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		getDialog().setTitle(titleId);

		View view = inflater.inflate(R.layout.layout_warn, container, false);

		TextView textView = (TextView) view.findViewById(R.id.text);
		Button configureButton = (Button) view.findViewById(R.id.configure_button);
		Button cancelButton = (Button) view.findViewById(R.id.cancel_button);
		checkbox = (CheckBox) view.findViewById(R.id.checkbox);

		configureButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applySettings();
				try {
					onAcceptIntent.send();
				} catch (Exception ignored) {
				}
				dismiss();
			}
		});

		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applySettings();
				dismiss();
			}
		});

		textView.setText(messageId);

		return view;
	}

	private void applySettings() {
		if (disablePref == null) {
			return;
		}

		if (checkbox.isChecked()) {
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
			pref.edit().putBoolean(disablePref, true).apply();
		}
	}

}