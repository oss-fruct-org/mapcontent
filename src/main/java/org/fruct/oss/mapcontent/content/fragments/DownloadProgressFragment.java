package org.fruct.oss.mapcontent.content.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.fruct.oss.mapcontent.R;
import org.fruct.oss.mapcontent.content.ContentItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class DownloadProgressFragment extends Fragment implements View.OnClickListener {
	private OnFragmentInteractionListener mListener;

	private ProgressBar progressBar;
	private TextView textView;
	private TextView textView2;

	public DownloadProgressFragment() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_download_progress, container, false);

		this.progressBar = ((ProgressBar) view.findViewById(R.id.progress_bar));
		ImageButton stopButton = ((ImageButton) view.findViewById(R.id.button));

		this.textView = (TextView) view.findViewById(R.id.text);
		this.textView2 = (TextView) view.findViewById(R.id.text2);

		stopButton.setOnClickListener(this);

		return view;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Android doesn't preserve fragment hidden state
		if (savedInstanceState != null && savedInstanceState.getBoolean("hidden") && !isHidden()) {
			getFragmentManager().beginTransaction().hide(this).commit();
		}
	}

	public void startDownload() {
		if (isHidden()) {
			getFragmentManager().beginTransaction()
					.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
					.show(this)
					.commit();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("hidden", isHidden());
	}

	public void downloadStateUpdated(ContentItem item, int downloaded, int max) {
		startDownload();

		progressBar.setMax(max);
		progressBar.setProgress(downloaded);

		float mbMax = (float) max / (1024 * 1024);
		float mbCurrent = (float) downloaded / (1024 * 1024);
		String downloadString = String.format(Locale.getDefault(), "%.3f/%.3f MB", mbCurrent, mbMax);

		textView.setText(item.getDescription());
		textView2.setText(downloadString);
	}

	public void stopDownload() {
		if (!isHidden()) {
			getFragmentManager().beginTransaction()
					.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
					.hide(this)
					.commit();
		}
	}

	@Override
	public void onClick(View v) {
		mListener.stopButtonPressed();
	}

	public void setListener(ContentFragment listener) {
		this.mListener = listener;
	}

	public interface OnFragmentInteractionListener {
		public void stopButtonPressed();
	}

}
