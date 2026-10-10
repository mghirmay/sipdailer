package de.sinitpower.sinitphone;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactsFragment extends Fragment {

    private static final int TYPE_HEADER  = 0;
    private static final int TYPE_CONTACT = 1;

    private RecyclerView recyclerView;
    private AlphabetIndexView alphabetIndex;
    private TextView letterBubble;
    private SectionedAdapter adapter;

    private final List<Contact> allContacts    = new ArrayList<>();
    private final Map<String, Integer> letterPositions = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);

        recyclerView  = view.findViewById(R.id.contacts_recycler);
        alphabetIndex = view.findViewById(R.id.alphabet_index);
        letterBubble  = view.findViewById(R.id.letter_bubble);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SectionedAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);


        EditText searchView = view.findViewById(R.id.contacts_search);
        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString().trim());
            }
        });

        alphabetIndex.setListener(new AlphabetIndexView.Listener() {
            @Override
            public void onLetterPick(String letter) {
                letterBubble.setText(letter);
                letterBubble.setVisibility(View.VISIBLE);
                Integer pos = letterPositions.get(letter);
                if (pos != null) {
                    ((LinearLayoutManager) recyclerView.getLayoutManager())
                            .scrollToPositionWithOffset(pos, 0);
                }
            }

            @Override
            public void onRelease() {
                letterBubble.setVisibility(View.GONE);
            }
        });

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        }

        return view;
    }

    private void loadContacts() {
        allContacts.clear();
        ContentResolver cr = requireContext().getContentResolver();
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " IS NOT NULL"
                + " AND " + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " != ''"
                + " AND " + ContactsContract.CommonDataKinds.Phone.NUMBER + " IS NOT NULL"
                + " AND " + ContactsContract.CommonDataKinds.Phone.NUMBER + " != ''";
        Cursor cur = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                selection, null,
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

        applyFilter("");
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
                        || c.number.replace(" ", "").contains(q)) {
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

        // Hide sidebar when search is active — letters jump around while filtering
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
                vh.itemView.setOnClickListener(v -> showCallDialog(c));
            }
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView letter;
        HeaderVH(View v) { super(v); letter = (TextView) v; }
    }

    static class ContactVH extends RecyclerView.ViewHolder {
        final TextView name, number;
        ContactVH(View v) {
            super(v);
            name   = v.findViewById(R.id.contact_name);
            number = v.findViewById(R.id.contact_number);
        }
    }

    // ── Call dialog ───────────────────────────────────────────────────────────

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
                        activity.dialNumber(num);
                        activity.makeCall(num);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
