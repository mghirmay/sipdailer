package de.synitlabs.sinitphone;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialpadFragment extends Fragment {

    private static final int TYPE_HEADER  = 0;
    private static final int TYPE_CONTACT = 1;

    private EditText dialInput;
    private FloatingActionButton btnCall;
    private ImageButton btnSpeaker;
    private String pendingNumber;
    private boolean speakerOn = false;

    private SectionedAdapter adapter;
    private AlphabetIndexView alphabetIndex;
    private TextView letterBubble;

    private final List<Contact> allContacts = new ArrayList<>();
    private final Map<String, Integer> letterPositions = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dialpad, container, false);

        dialInput     = view.findViewById(R.id.dialInput);
        btnCall       = view.findViewById(R.id.btnCall);
        btnSpeaker    = view.findViewById(R.id.btnSpeaker);
        alphabetIndex = view.findViewById(R.id.dialAlphabetIndex);
        letterBubble  = view.findViewById(R.id.dialLetterBubble);
        ImageButton btnDelete = view.findViewById(R.id.btnDelete);

        RecyclerView recycler = view.findViewById(R.id.dialContactsRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SectionedAdapter(new ArrayList<>());
        recycler.setAdapter(adapter);

        if (pendingNumber != null) {
            dialInput.setText(pendingNumber);
            dialInput.setSelection(pendingNumber.length());
            pendingNumber = null;
        }

        // Search / filter as user types
        dialInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString().trim());
            }
        });

        // Alphabet sidebar
        alphabetIndex.setListener(new AlphabetIndexView.Listener() {
            @Override
            public void onLetterPick(String letter) {
                letterBubble.setText(letter);
                letterBubble.setVisibility(View.VISIBLE);
                Integer pos = letterPositions.get(letter);
                if (pos != null) {
                    ((LinearLayoutManager) recycler.getLayoutManager())
                            .scrollToPositionWithOffset(pos, 0);
                }
            }
            @Override
            public void onRelease() {
                letterBubble.setVisibility(View.GONE);
            }
        });

        btnDelete.setOnClickListener(v -> {
            String text = dialInput.getText().toString();
            if (!text.isEmpty()) {
                dialInput.setText(text.substring(0, text.length() - 1));
                dialInput.setSelection(dialInput.getText().length());
            }
        });

        btnCall.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity == null) return;
            if (activity.isInCall()) {
                activity.hangUp();
            } else {
                String number = dialInput.getText().toString().trim();
                if (!number.isEmpty()) activity.makeCall(number);
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

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        }

        return view;
    }

    // ── Contacts ──────────────────────────────────────────────────────────────

    private void loadContacts() {
        allContacts.clear();
        ContentResolver cr = requireContext().getContentResolver();
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " IS NOT NULL"
                + " AND " + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " != ''"
                + " AND " + ContactsContract.CommonDataKinds.Phone.NUMBER + " IS NOT NULL"
                + " AND " + ContactsContract.CommonDataKinds.Phone.NUMBER + " != ''";
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        Cursor cur = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

        if (cur != null) {
            int nameIdx = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numIdx  = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cur.moveToNext()) {
                String name   = cur.getString(nameIdx);
                String number = cur.getString(numIdx);
                if (name != null && !name.trim().isEmpty()
                        && number != null && !number.trim().isEmpty()) {
                    allContacts.add(new Contact(name, number));
                }
            }
            cur.close();
        }

        applyFilter(dialInput != null ? dialInput.getText().toString().trim() : "");
    }

    private void applyFilter(String query) {
        List<Contact> filtered;
        if (query.isEmpty()) {
            filtered = allContacts;
        } else {
            String q = query.toLowerCase();
            filtered = new ArrayList<>();
            for (Contact c : allContacts) {
                if (c.name.toLowerCase().contains(q)
                        || c.number.replaceAll("[\\s\\-()./]+", "").contains(q)) {
                    filtered.add(c);
                }
            }
        }

        List<Object> items = buildSectionedList(filtered);
        adapter.setItems(items);

        List<String> letters = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof String) letters.add((String) item);
        }
        alphabetIndex.setLetters(letters);
        alphabetIndex.setVisibility(query.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<Object> buildSectionedList(List<Contact> contacts) {
        letterPositions.clear();
        List<Object> items = new ArrayList<>();
        String lastLetter = null;
        for (Contact c : contacts) {
            String letter = c.name.trim().substring(0, 1).toUpperCase();
            if (!letter.equals(lastLetter)) {
                letterPositions.put(letter, items.size());
                items.add(letter);
                lastLetter = letter;
            }
            items.add(c);
        }
        return items;
    }

    private void showCallDialog(Contact contact) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        EditText editNumber = new EditText(activity);
        editNumber.setText(contact.number);
        editNumber.setSelection(contact.number.length());
        int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
        editNumber.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(activity)
                .setTitle(contact.name)
                .setView(editNumber)
                .setPositiveButton("Call", (dialog, which) -> {
                    String num = editNumber.getText().toString().trim();
                    if (!num.isEmpty()) {
                        dialInput.setText(num);
                        dialInput.setSelection(num.length());
                        activity.makeCall(num);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Dialpad controls ──────────────────────────────────────────────────────

    public void updateCallButton(boolean inCall) {
        if (btnCall != null && isAdded()) {
            int color = ContextCompat.getColor(requireContext(),
                    inCall ? R.color.hangup_button_bg : R.color.call_button_bg);
            btnCall.setBackgroundTintList(ColorStateList.valueOf(color));
            btnCall.setImageResource(inCall ? R.drawable.ic_call_end : R.drawable.ic_call);
        }
    }

    private void updateSpeakerButton(boolean enabled) {
        if (btnSpeaker == null || !isAdded()) return;
        int iconColor = ContextCompat.getColor(requireContext(), enabled ? R.color.on_primary : R.color.text_secondary);
        int bgColor   = enabled ? ContextCompat.getColor(requireContext(), R.color.primary) : 0x00FFFFFF;
        btnSpeaker.setColorFilter(iconColor);
        btnSpeaker.setBackgroundTintList(ColorStateList.valueOf(bgColor));
    }

    public void setDialNumber(String number) {
        if (dialInput != null) {
            dialInput.setText(number);
            dialInput.setSelection(number.length());
        } else {
            pendingNumber = number;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dialInput     = null;
        btnCall       = null;
        btnSpeaker    = null;
        alphabetIndex = null;
        letterBubble  = null;
        adapter       = null;
    }

    // ── Avatar colors (Material palette, picked by first letter) ─────────────

    private static final int[] AVATAR_COLORS = {
        0xFF1976D2, // blue
        0xFF388E3C, // green
        0xFFF57C00, // orange
        0xFF7B1FA2, // purple
        0xFFD32F2F, // red
        0xFF0097A7, // cyan
        0xFF5D4037, // brown
        0xFF455A64, // blue-grey
    };

    private static int avatarColor(String name) {
        return AVATAR_COLORS[Math.abs(name.charAt(0)) % AVATAR_COLORS.length];
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private static class Contact {
        final String name, number;
        Contact(String name, String number) { this.name = name; this.number = number; }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class SectionedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private List<Object> items;

        SectionedAdapter(List<Object> items) { this.items = items; }

        void setItems(List<Object> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? TYPE_HEADER : TYPE_CONTACT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderVH(inf.inflate(R.layout.item_contact_header, parent, false));
            }
            return new ContactVH(inf.inflate(R.layout.item_contact, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).letter.setText((String) items.get(position));
            } else {
                Contact c = (Contact) items.get(position);
                ContactVH vh = (ContactVH) holder;
                vh.name.setText(c.name);
                vh.number.setText(c.number);

                // Alphabetical avatar
                String letter = c.name.trim().substring(0, 1).toUpperCase();
                vh.avatar.setText(letter);
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(avatarColor(c.name));
                vh.avatar.setBackground(circle);

                vh.itemView.setOnClickListener(v -> showCallDialog(c));
            }
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView letter;
        HeaderVH(View v) { super(v); letter = (TextView) v; }
    }

    static class ContactVH extends RecyclerView.ViewHolder {
        final TextView avatar, name, number;
        ContactVH(View v) {
            super(v);
            avatar = v.findViewById(R.id.contact_avatar);
            name   = v.findViewById(R.id.contact_name);
            number = v.findViewById(R.id.contact_number);
        }
    }
}
