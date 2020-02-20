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
import android.widget.ImageButton;
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

public class RecipientsRecyclerAdapter extends RecyclerView.Adapter<RecipientsRecyclerAdapter.ViewHolder> {
    private List<LabelData> listRecipients;
    private Context context;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView txtRecipientName;
        public ImageButton btnRemoveRecipient;

        public ViewHolder(View v) {
            super(v);
            txtRecipientName = v.findViewById(R.id.txtRecipientName);
            btnRemoveRecipient = v.findViewById(R.id.btnRemoveRecipient);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public RecipientsRecyclerAdapter(List<LabelData> listRecipients, Context context) {
        this.listRecipients = listRecipients;
        this.context = context;
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public RecipientsRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Inflate the custom layout
        View conversationView = inflater.inflate(R.layout.item_recipient, parent, false);

        // Return a new holder instance
        return new ViewHolder(conversationView);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(RecipientsRecyclerAdapter.ViewHolder holder, final int position) {
        // Get the data model based on position
        LabelData contact = listRecipients.get(position);
        String contactName = contact.getValue();

        // Set item views based on your views and data model
        TextView txtRecipientName = holder.txtRecipientName;
        txtRecipientName.setText(contactName);

        ImageButton btnRemoveRecipient = holder.btnRemoveRecipient;
        btnRemoveRecipient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listRecipients.remove(position);
                notifyDataSetChanged();
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return listRecipients.size();
    }
}
