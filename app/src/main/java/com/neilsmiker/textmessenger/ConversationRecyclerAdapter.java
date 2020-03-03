package com.neilsmiker.textmessenger;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationRecyclerAdapter extends RecyclerView.Adapter<ConversationRecyclerAdapter.ViewHolder> {
    private List<Sms> listConversations;
    private Context context;
    private String currentDate;
    private SimpleDateFormat hourDateFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    //private SimpleDateFormat hour24DateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dayDateFormat = new SimpleDateFormat("M/d/yy", Locale.getDefault());

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        ImageView profilePic;
        TextView txtLastMessage;
        TextView txtProfileName;
        TextView txtTimestamp;
        TextView txtUnreadBadge;
        ConstraintLayout conversationLayout;

        ViewHolder(View v) {
            super(v);
            profilePic = v.findViewById(R.id.profilePic);
            txtLastMessage = v.findViewById(R.id.txtLastMessage);
            txtProfileName = v.findViewById(R.id.profileName);
            txtTimestamp = v.findViewById(R.id.txtTimestamp);
            txtUnreadBadge = v.findViewById(R.id.txtUnreadBadge);
            conversationLayout = v.findViewById(R.id.conversationLayout);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    ConversationRecyclerAdapter(List<Sms> listConversations, Context context) {
        this.listConversations = listConversations;
        this.context = context;
        currentDate = dayDateFormat.format(new Date(System.currentTimeMillis()));
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public ConversationRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Inflate the custom layout
        View conversationView = inflater.inflate(R.layout.item_conversation, parent, false);

        // Return a new holder instance
        return new ViewHolder(conversationView);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ConversationRecyclerAdapter.ViewHolder holder, int position) {
        // Get the data model based on position
        Sms message = listConversations.get(position);
        String userName = message.getDisplayName();

        // Set item views based on your views and data model
        ImageView profilePic = holder.profilePic;
        profilePic.setImageBitmap(getProfilePic(context,message.getAddress()));
        profilePic.setClipToOutline(true);

        TextView txtLastMessage = holder.txtLastMessage;
        TextView txtProfileName = holder.txtProfileName;
        TextView txtTimestamp = holder.txtTimestamp;
        TextView txtUnreadBadge = holder.txtUnreadBadge;
        ConstraintLayout conversationLayout = holder.conversationLayout;

        String timestamp = message.getTime();

        if (timestamp != null) {
            Date date = new Date(Long.parseLong(timestamp));
            String messageDate = dayDateFormat.format(date);
            if (messageDate.equals(currentDate)){
                messageDate = hourDateFormat.format(date);
            }
            txtTimestamp.setText(messageDate);
        }

        txtLastMessage.setVisibility(View.VISIBLE);
        txtLastMessage.setText(message.getMsg());

        txtProfileName.setText(userName);

        if (message.getNumberUnread().equals("0")){
            txtUnreadBadge.setVisibility(View.GONE);
        } else {
            txtUnreadBadge.setVisibility(View.VISIBLE);
            txtUnreadBadge.setText(message.getNumberUnread());
        }

        selectListItem(message,txtProfileName,txtLastMessage,txtTimestamp,conversationLayout);

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return listConversations.size();
    }

    private Bitmap getProfilePic(Context context, String number) {
        ContentResolver contentResolver = context.getContentResolver();
        String contactId = null;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));

        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup._ID};

        Cursor cursor =
                contentResolver.query(
                        uri,
                        projection,
                        null,
                        null,
                        null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID));
            }
            cursor.close();
        }

        Bitmap photo = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.image_circle);

        try {
            InputStream inputStream = null;
            if (contactId != null) {
                inputStream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(),
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.parseLong(contactId)));
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

    private void selectListItem(Sms message, TextView name, TextView lastMessage, TextView timestamp, ConstraintLayout conversationBubble){
        if (!message.isSelected()){
            conversationBubble.setBackground(context.getDrawable(R.drawable.conversation_bubble));
            name.setTextColor(Color.parseColor("#000000"));
            lastMessage.setTextColor(Color.parseColor("#000000"));
            timestamp.setTextColor(Color.parseColor("#808080"));
        } else {
            conversationBubble.setBackground(context.getDrawable(R.drawable.text_bubble_user_selected));
            name.setTextColor(context.getResources().getColor(R.color.colorTitle));
            lastMessage.setTextColor(context.getResources().getColor(R.color.colorTitle));
            timestamp.setTextColor(context.getResources().getColor(R.color.colorTitle));
        }
    }
}
