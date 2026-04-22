package com.example.android.sip;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ContactsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ContactsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);
        recyclerView = view.findViewById(R.id.contacts_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        }
        
        return view;
    }

    private void loadContacts() {
        List<Contact> contactList = new ArrayList<>();
        ContentResolver cr = requireContext().getContentResolver();
        Cursor cur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

        if (cur != null) {
            int nameIndex = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberIndex = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            
            while (cur.moveToNext()) {
                String name = cur.getString(nameIndex);
                String number = cur.getString(numberIndex);
                contactList.add(new Contact(name, number));
            }
            cur.close();
        }

        adapter = new ContactsAdapter(contactList);
        recyclerView.setAdapter(adapter);
    }

    private static class Contact {
        String name;
        String number;

        Contact(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }

    private class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder> {
        private List<Contact> contacts;

        ContactsAdapter(List<Contact> contacts) {
            this.contacts = contacts;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Contact contact = contacts.get(position);
            holder.nameText.setText(contact.name);
            holder.numberText.setText(contact.number);
            holder.itemView.setOnClickListener(v -> {
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    activity.makeCall(contact.number);
                }
            });
        }

        @Override
        public int getItemCount() {
            return contacts.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView numberText;

            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.contact_name);
                numberText = itemView.findViewById(R.id.contact_number);
            }
        }
    }
}
