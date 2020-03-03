package com.neilsmiker.textmessenger;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ContactsFragment extends Fragment  implements LoaderManager.LoaderCallbacks<Cursor> {

    private RecyclerView recyclerView;
    private ContactsRecyclerAdapter recyclerAdapter;
    private ContentResolver cr;
    private Activity activity;

    // Called just before the Fragment displays its UI
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Always call the super method first
        super.onCreate(savedInstanceState);

        activity = getActivity();

        // Initializes the loader
        LoaderManager.getInstance(this).initLoader(0, null, this);
        cr = activity.getContentResolver();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the fragment layout
        return inflater.inflate(R.layout.contacts_fragment,
                container, false);
    }

    public ContactsFragment () {}

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        recyclerView = Objects.requireNonNull(getActivity()).findViewById(R.id.contact_recyclerview);
        recyclerView.setHasFixedSize(true);
        DividerItemDecoration itemDecor = new DividerItemDecoration(getActivity(),DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(itemDecor);

        ItemClickSupport.addTo(recyclerView).setOnItemClickListener(new ItemClickSupport.OnItemClickListener() {
            @Override
            public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                String name = recyclerAdapter.getContactName(position);
                List<LabelData> numbers = recyclerAdapter.getContactNumber(position);

                if (numbers.size() == 1) {
                    if (activity instanceof MainActivitySMS) {
                        ((MainActivitySMS) activity).insertRecipientNumber(numbers.get(0).getValue(), name);
                    }
                } else {
                    showNumberSelectionDialog(name,numbers);
                }
            }
        })/*.setOnItemLongClickListener(new ItemClickSupport.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClicked(RecyclerView recyclerView, int position, View v) {
                selectListItem(position, v, true);
                return true;
            }
        })*/;
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created.
        return contactsLoader();
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        //The framework will take care of closing the
        // old cursor once we return.
        List<Contact> contacts = contactsFromCursor(cursor);
        //Collections.sort(contacts,Contact.ContactComparator);

        // Define global mutable variables
        // Define a RecyclerView object
        recyclerAdapter = new ContactsRecyclerAdapter(contacts, getActivity());
        recyclerView.setAdapter(recyclerAdapter);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(linearLayoutManager);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
    }

    private  Loader<Cursor> contactsLoader() {
        Uri contactsUri = ContactsContract.Contacts.CONTENT_URI; // The content URI of the phone contacts

        String[] projection = {                                  // The columns to return for each row
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
        } ;

        //String selection = null;                                 //Selection criteria
        String[] selectionArgs = {};                             //Selection criteria
        String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " ASC"; //The sort order for the returned rows

        return new CursorLoader(
                Objects.requireNonNull(getActivity()).getApplicationContext(),
                contactsUri,
                projection,
                null,
                selectionArgs,
                sortOrder);
    }

    private List<Contact> contactsFromCursor(Cursor cursor) {
        List<Contact> contacts = new ArrayList<>();

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                contacts.add(createContactObject(cursor));
            } while (cursor.moveToNext());
        }

        return contacts;
    }

    private Contact createContactObject(Cursor c){
        Contact objContact = new Contact();
        String id = c.getString(c.getColumnIndex(ContactsContract.Contacts._ID));
        objContact.setId(id);
        objContact.setPhone(getPhoneNumbers(id));
        objContact.setEmail(getEmailAddresses(id));

        String userName = c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
        if (userName == null){
            userName = getString(R.string.unknown);
        }
        objContact.setName(userName);

        return objContact;
    }

    private List<LabelData> getPhoneNumbers(String id){
        Cursor cursor = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = ?",
                new String[]{id}, null);
        List<LabelData> phoneNumbers = new ArrayList<>();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                LabelData objNumber = new LabelData();
                int phoneType = cursor.getInt(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.TYPE));
                String phoneNumber = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER)).replaceAll("[^0-9]", "");
                objNumber.setValue(phoneNumber);
                switch (phoneType) {
                    case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                        objNumber.setLabel("Mobile");
                        break;
                    case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                        objNumber.setLabel("Home");
                        break;
                    case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                        objNumber.setLabel("Work");
                        break;
                    case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:
                        objNumber.setLabel("Other");
                        break;
                    default:
                        objNumber.setLabel("Unknown");
                        break;
                }

                boolean containsNumber = false;
                if (phoneNumbers.size() > 0) {
                    for (LabelData data : phoneNumbers){
                        if (data.getValue().contains(phoneNumber)) {
                            containsNumber = true;
                            break;
                        }
                    }
                }
                if (!containsNumber) {
                    phoneNumbers.add(objNumber);
                }
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return phoneNumbers;
    }

    private List<LabelData> getEmailAddresses(String id){
        Cursor cursor = cr.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Email.CONTACT_ID +" = ?",
                new String[]{id}, null);
        List<LabelData> emails = new ArrayList<>();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                LabelData objEmail = new LabelData();
                int emailType = cursor.getInt(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Email.TYPE));
                String email = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Email.ADDRESS));
                objEmail.setValue(email);
                switch (emailType) {
                    case ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM:
                        objEmail.setLabel("Custom");
                        break;
                    case ContactsContract.CommonDataKinds.Email.TYPE_MOBILE:
                        objEmail.setLabel("Mobile");
                        break;
                    case ContactsContract.CommonDataKinds.Email.TYPE_HOME:
                        objEmail.setLabel("Home");
                        break;
                    case ContactsContract.CommonDataKinds.Email.TYPE_WORK:
                        objEmail.setLabel("Work");
                        break;
                    default:
                        objEmail.setLabel("Other");
                        break;
                }

                boolean containsNumber = false;
                if (emails.size() > 0) {
                    for (LabelData data : emails){
                        if (data.getValue().contains(email)) {
                            containsNumber = true;
                            break;
                        }
                    }
                }
                if (!containsNumber) {
                    emails.add(objEmail);
                }
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return emails;
    }

    private void showNumberSelectionDialog(final String name, final List<LabelData> numbers) {

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setContentView(R.layout.contact_number_dialog);

        TextView title = dialog.findViewById(R.id.txtNumSelectContactName);
        title.setText(name);

        final RadioGroup rg = dialog.findViewById(R.id.numberRadioGroup);

        int i = 0;
        for (LabelData data : numbers){
            RadioButton rb = new RadioButton(activity);
            rb.setText(getString(R.string.colon_label,data.getLabel(),data.getValue()));
            rb.setId(i);
            rg.addView(rb);
            i++;
        }

        final TextView selectButton = dialog.findViewById(R.id.selectButton);
        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivitySMS) activity).insertRecipientNumber(numbers.get(rg.getCheckedRadioButtonId()).getValue(), name);
                dialog.dismiss();
            }
        });

        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                selectButton.setVisibility(View.VISIBLE);
            }
        });

        dialog.show();
    }

    void filterContacts(String query){
        recyclerAdapter.getFilter().filter(query);
    }
}
