package com.neilsmiker.textmessenger;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ContactsRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements Filterable {
    private List<Contact> listContacts;
    private List<Contact> contactListFiltered;
    private Context context;

    //View Types
    private static final int VIEW_TYPE_CONTACT_DEFAULT = 0;
    private static final int VIEW_TYPE_CONTACT_LETTER = 1;

    // Provide a suitable constructor (depends on the kind of dataset)
    ContactsRecyclerAdapter(List<Contact> listContacts, Context context) {
        this.listContacts = listContacts;
        this.contactListFiltered = listContacts;
        this.context = context;
    }

    @Override
    public int getItemViewType(int position) {
        boolean showLetterDivider = false;
        if (position > 0){
            String previousName = contactListFiltered.get(position - 1).getName();
            String currentName = contactListFiltered.get(position).getName();
            if (previousName != null && currentName != null) {
                char lastLetter = previousName.toUpperCase().charAt(0);
                char currentLetter = currentName.toUpperCase().charAt(0);
                if (lastLetter != currentLetter) {
                    showLetterDivider = true;
                }
            }
        } else {
            showLetterDivider = true;
        }

        return showLetterDivider ? VIEW_TYPE_CONTACT_LETTER : VIEW_TYPE_CONTACT_DEFAULT;
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType){
            case VIEW_TYPE_CONTACT_LETTER:
                View otherContactView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_contact_letter, parent, false);
                return new ContactLetterHolder(otherContactView);
            //VIEW_TYPE_CONTACT_DEFAULT
            default:
                View defaultView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_contact, parent, false);
                return new ContactDefaultHolder(defaultView);
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        Contact contact = contactListFiltered.get(position);

        String userName = contact.getName();

        if (userName != null) {
            String firstLetter = userName.toUpperCase().substring(0, 1);
            String id = contact.getId();

            //Assign the holder:
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_CONTACT_LETTER:
                    ((ContactLetterHolder) holder).bind(userName, firstLetter, id);
                    break;

                //VIEW_TYPE_CONTACT_DEFAULT
                default:
                    ((ContactDefaultHolder) holder).bind(userName, id);
                    break;
            }
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString().toLowerCase();
                if (charString.isEmpty()) {
                    contactListFiltered = listContacts;
                } else {
                    List<Contact> filteredList = new ArrayList<>();
                    for (Contact row : listContacts) {
                        boolean addContact = false;

                        //Check for matching phone numbers:
                        for (LabelData data : row.getPhone()){
                            if (data.getValue().replaceAll("[^0-9]", "").contains(charString)){
                                addContact = true;
                            }
                        }

                        //Check for matching emails:
                        for (LabelData data : row.getEmail()){
                            if (data.getValue().toLowerCase().contains(charString)){
                                addContact = true;
                            }
                        }

                        //Check for matching names:
                        if (row.getName().toLowerCase().contains(charString)) {
                            addContact = true;
                        }

                        if (addContact){
                            filteredList.add(row);
                        }
                    }

                    contactListFiltered = filteredList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = contactListFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                //contactListFiltered = (ArrayList<Contact>) results.values;
                notifyDataSetChanged();
            }
        };
    }

    //ViewHolders
    private class ContactDefaultHolder extends RecyclerView.ViewHolder {
        ImageView profilePic;
        TextView profileName;

        ContactDefaultHolder(View v) {
            super(v);

            profilePic = v.findViewById(R.id.profilePic);
            profileName = v.findViewById(R.id.profileName);
        }

        void bind(String userName, String id) {
            profileName.setText(userName);

            profilePic.setImageBitmap(getProfilePic(context,id));
            profilePic.setClipToOutline(true);
        }
    }

    private class ContactLetterHolder extends RecyclerView.ViewHolder {
        ImageView profilePic;
        TextView profileName,txtLetterLabel;

        ContactLetterHolder(View v) {
            super(v);

            profilePic = v.findViewById(R.id.profilePic);
            profileName = v.findViewById(R.id.profileName);
            txtLetterLabel = v.findViewById(R.id.txtLetterLabel);
        }

        void bind(String userName, String firstLetter, String id) {
            profileName.setText(userName);
            txtLetterLabel.setText(firstLetter);

            profilePic.setImageBitmap(getProfilePic(context,id));
            profilePic.setClipToOutline(true);
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return contactListFiltered.size();
    }

    private Bitmap getProfilePic(Context context, String contactId) {
        Bitmap photo = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.image_circle);

        try {
            InputStream inputStream = null;
            if (contactId != null) {
                inputStream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(),
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.valueOf(contactId)));
            }

            if (inputStream != null) {
                photo = BitmapFactory.decodeStream(inputStream);
            }

            if (inputStream != null) {
                inputStream.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return photo;
    }

    List<LabelData> getContactNumber(int position){
        List<LabelData> numbers = contactListFiltered.get(position).getPhone();
        List<LabelData> emails = contactListFiltered.get(position).getEmail();

        List<LabelData> numbersAndEmails = new ArrayList<>();
        numbersAndEmails.addAll(numbers);
        numbersAndEmails.addAll(emails);

        return numbersAndEmails;
    }

    String getContactName(int position){
        return contactListFiltered.get(position).getName();
    }
}
