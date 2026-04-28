package de.sinitpower.sinitphone;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class DialpadFragment extends Fragment {

    private EditText dialInput;
    private FloatingActionButton btnCall;
    private ImageButton btnSpeaker;
    private String pendingNumber;
    private boolean speakerOn = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dialpad, container, false);
        dialInput = view.findViewById(R.id.dialInput);
        btnCall = view.findViewById(R.id.btnCall);
        btnSpeaker = view.findViewById(R.id.btnSpeaker);
        GridLayout dialPadGrid = view.findViewById(R.id.dialPadGrid);
        ImageButton btnDelete = view.findViewById(R.id.btnDelete);

        if (pendingNumber != null) {
            dialInput.setText(pendingNumber);
            dialInput.setSelection(pendingNumber.length());
            pendingNumber = null;
        }

        for (int i = 0; i < dialPadGrid.getChildCount(); i++) {
            View child = dialPadGrid.getChildAt(i);
            if (child instanceof Button) {
                Button b = (Button) child;
                b.setOnClickListener(v -> {
                    dialInput.append(b.getText());
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                });
            }
        }

        btnDelete.setOnClickListener(v -> {
            String text = dialInput.getText().toString();
            if (text.length() > 0) {
                dialInput.setText(text.substring(0, text.length() - 1));
                dialInput.setSelection(dialInput.getText().length());
            }
        });

        btnCall.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                if (activity.isInCall()) {
                    activity.hangUp();
                } else {
                    String number = dialInput.getText().toString();
                    if (!number.isEmpty()) {
                        activity.makeCall(number);
                    }
                }
            }
        });

        btnSpeaker.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                speakerOn = !speakerOn;
                activity.setSpeakerEnabled(speakerOn);
                updateSpeakerButton(speakerOn);
            }
        });

        btnSpeaker.post(() -> updateSpeakerButton(speakerOn));

        return view;
    }

    private void updateSpeakerButton(boolean enabled) {
        if (btnSpeaker == null || !isAdded()) return;
        int iconColor = ContextCompat.getColor(requireContext(), enabled ? R.color.on_primary : R.color.text_secondary);
        int bgColor   = enabled ? ContextCompat.getColor(requireContext(), R.color.primary) : 0x00FFFFFF;
        btnSpeaker.setColorFilter(iconColor);
        btnSpeaker.setBackgroundTintList(ColorStateList.valueOf(bgColor));
    }

    public void updateCallButton(boolean inCall) {
        if (btnCall != null && isAdded()) {
            int color = ContextCompat.getColor(requireContext(), 
                inCall ? R.color.hangup_button_bg : R.color.call_button_bg);
            btnCall.setBackgroundTintList(ColorStateList.valueOf(color));
            btnCall.setImageResource(inCall ? R.drawable.ic_call_end : R.drawable.ic_call);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dialInput = null;
        btnCall = null;
        btnSpeaker = null;
    }

    public void setDialNumber(String number) {
        if (dialInput != null) {
            dialInput.setText(number);
            dialInput.setSelection(number.length());
        } else {
            pendingNumber = number;
        }
    }
}
