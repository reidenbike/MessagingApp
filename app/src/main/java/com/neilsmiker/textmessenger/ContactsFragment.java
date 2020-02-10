package com.neilsmiker.textmessenger;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ContactsFragment extends Fragment  implements LoaderManager.LoaderCallbacks<Cursor> {

    private RecyclerView recyclerView;
    private ContentResolver cr;

    // Called just before the Fragment displays its UI
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Always call the super method first
        super.onCreate(savedInstanceState);
        // Initializes the loader
        LoaderManager.getInstance(this).initLoader(0, null, this);
        cr = Objects.requireNonNull(getActivity()).getContentResolver();
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
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created.

        /*if (id == CONTACTS_LOADER_ID) {
            return contactsLoader();
        }
        return null;*/

        return contactsLoader();
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        //The framework will take care of closing the
        // old cursor once we return.
        List<Sms> contacts = contactsFromCursor(cursor);

        // Define global mutable variables
        // Define a RecyclerView object
        ConversationRecyclerAdapter recyclerAdapter = new ConversationRecyclerAdapter(contacts, getActivity());
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

        String selection = null;                                 //Selection criteria
        String[] selectionArgs = {};                             //Selection criteria
        String sortOrder = null;                                 //The sort order for the returned rows

        return new CursorLoader(
                Objects.requireNonNull(getActivity()).getApplicationContext(),
                contactsUri,
                projection,
                selection,
                selectionArgs,
                sortOrder);
    }

    private List<Sms> contactsFromCursor(Cursor cursor) {
        List<Sms> contacts = new ArrayList<>();

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();

            do {
                contacts.add(createSmsObject(cursor));
            } while (cursor.moveToNext());
        }

        return contacts;
    }

    //Stand-in until Contacts Object is complete:
    private Sms createSmsObject(Cursor c){
        Sms objSms = new Sms();
        String id = c.getString(c.getColumnIndex(ContactsContract.Contacts._ID));
        objSms.setId(id);
        objSms.setThreadId("1");
        String number = String.valueOf(getPhoneNumbers(id));
        String email = String.valueOf(getEmailAddresses(id));
        objSms.setAddress(number);
        objSms.setMsg(number + email);
        objSms.setTime("1581220574000");
        objSms.setFolderName("inbox");
        objSms.setDisplayName(c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)));

        //Set SMS object read state to "1" since this is always true at this point
        objSms.setReadState("1");
        objSms.setNumberUnread("0");

        return objSms;
    }

    private List<String> getPhoneNumbers(String id){
        Cursor cursor = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = ?",
                new String[]{id}, null);
        List<String> phoneNumbers = new ArrayList<>();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                int phoneType = cursor.getInt(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.TYPE));
                String phoneNumber = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER));
                switch (phoneType) {
                    case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                        phoneNumbers.add("Mobile: " + phoneNumber);
                        break;
                    case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                        phoneNumbers.add("Home: " + phoneNumber);
                        break;
                    case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                        phoneNumbers.add("Work: " + phoneNumber);
                        break;
                    case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:
                        phoneNumbers.add("Other: " + phoneNumber);
                        break;
                    default:
                        phoneNumbers.add("Unknown: " + phoneNumber);
                        break;
                }
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return phoneNumbers;
    }

    private List<String> getEmailAddresses(String id){
        Cursor cursor = cr.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Email.CONTACT_ID +" = ?",
                new String[]{id}, null);
        List<String> emails = new ArrayList<>();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                int emailType = cursor.getInt(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Email.TYPE));
                String email = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Email.ADDRESS));
                switch (emailType) {
                    case ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM:
                        emails.add("Custom: " + email);
                        break;
                    case ContactsContract.CommonDataKinds.Email.TYPE_MOBILE:
                        emails.add("Mobile: " + email);
                        break;
                    case ContactsContract.CommonDataKinds.Email.TYPE_HOME:
                        emails.add("Home: " + email);
                        break;
                    case ContactsContract.CommonDataKinds.Email.TYPE_WORK:
                        emails.add("Work: " + email);
                        break;
                    /*case ContactsContract.CommonDataKinds.Email.TYPE_OTHER:
                        emails.add("Other: " + email);
                        break;*/
                    default:
                        emails.add("Other: " + email);
                        break;
                }
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return emails;
    }
}
