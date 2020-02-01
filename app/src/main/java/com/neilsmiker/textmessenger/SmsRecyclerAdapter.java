package com.neilsmiker.textmessenger;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SmsRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Sms> listMessages;
    private Context context;
    private int width;
    private int timestampWidth = 0;
    private SimpleDateFormat hourDateFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    //private SimpleDateFormat hour24DateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dayDateFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault());

    //View Types
    private static final int VIEW_TYPE_USER_DEFAULT = 0;
    private static final int VIEW_TYPE_OTHER_DEFAULT = 1;
    private static final int VIEW_TYPE_USER_DAYBREAK = 2;
    private static final int VIEW_TYPE_OTHER_DAYBREAK = 3;

    // Provide a suitable constructor (depends on the kind of dataset)
    SmsRecyclerAdapter(List<Sms> listMessages, Context context, int width) {
        this.listMessages = listMessages;
        this.context = context;
        this.width = width;
    }

    @Override
    public int getItemViewType(int position) {
        boolean showDayDivider = false;
        if (position < listMessages.size() - 1){
            long lastTimestamp = Long.parseLong(Objects.requireNonNull(listMessages.get(position + 1)).getTime());
            long currentTimestamp = Long.parseLong(Objects.requireNonNull(listMessages.get(position)).getTime());
            String lastDay = dayDateFormat.format(lastTimestamp);
            String currentDay = dayDateFormat.format(currentTimestamp);

            if (lastDay != null && !lastDay.equals(currentDay)) {
                showDayDivider = true;
            }
        } else {
            showDayDivider = true;
        }

        if (listMessages.get(position).getFolderName().equals("sent")){
            return showDayDivider ? VIEW_TYPE_USER_DAYBREAK : VIEW_TYPE_USER_DEFAULT;
        } else {
            return showDayDivider ? VIEW_TYPE_OTHER_DAYBREAK : VIEW_TYPE_OTHER_DEFAULT;
        }
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType){
            case VIEW_TYPE_OTHER_DEFAULT:
                View otherMsgView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_other_default, parent, false);
                return new OtherDefaultHolder(otherMsgView);
            case VIEW_TYPE_USER_DAYBREAK:
                View userDayMsgView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_user_daybreak, parent, false);
                return new UserDaybreakHolder(userDayMsgView);
            case VIEW_TYPE_OTHER_DAYBREAK:
                View otherDayMsgView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_other_daybreak, parent, false);
                return new OtherDaybreakHolder(otherDayMsgView);

            //VIEW_TYPE_USER_DEFAULT
            default:
                View defaultView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_user_default, parent, false);
                return new UserDefaultHolder(defaultView);
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        Sms message = listMessages.get(position);

        String userName = message.getDisplayName();
        String folder = message.getFolderName();

        String timestamp = message.getTime();
        Date date = new Date(Long.parseLong(timestamp));
        String messageDate = hourDateFormat.format(date);
        String currentDay = dayDateFormat.format(date);

        String messageText = message.getMsg();

        //boolean showDayDivider = false;
        int nameVisibility = View.VISIBLE;
        int timestampVisibility = View.VISIBLE;

/*        if (position > 0){
            long lastTimestamp = Long.parseLong(Objects.requireNonNull(listMessages.get(position - 1)).getTime());
            String lastDay = dayDateFormat.format(lastTimestamp);

            if (lastDay != null && !lastDay.equals(currentDay)) {
                showDayDivider = true;
            }
        } else {
            showDayDivider = true;
        }*/

        if (position > 0) {
            String nextUserName = Objects.requireNonNull(listMessages.get(position - 1)).getDisplayName();
            long nextTimestamp = Long.parseLong(Objects.requireNonNull(listMessages.get(position - 1)).getTime());
            String nextDay = dayDateFormat.format(nextTimestamp);

            if (folder.equals("inbox")) {
                if (nextUserName != null && nextUserName.equals(userName)) {
                    nameVisibility = View.GONE;
                    if (nextTimestamp - Long.parseLong(Objects.requireNonNull(timestamp)) < 120000
                            && nextDay.equals(currentDay)) {
                        timestampVisibility = View.INVISIBLE;
                    } else if (nextDay.equals(currentDay)) {
                        nameVisibility = View.INVISIBLE;
                    }
                    if (nextTimestamp - Long.parseLong(Objects.requireNonNull(timestamp)) < 120000) {
                        timestampVisibility = View.INVISIBLE;
                    } else {
                        nameVisibility = View.INVISIBLE;
                    }
                }
            } else {
                String nextFolderName = Objects.requireNonNull(listMessages.get(position - 1)).getFolderName();
                if (nextFolderName != null && nextFolderName.equals(folder)) {
                    nameVisibility = View.GONE;
                    if (nextTimestamp - Long.parseLong(Objects.requireNonNull(timestamp)) < 120000
                            && nextDay.equals(currentDay)) {
                        timestampVisibility = View.INVISIBLE;
                    } else if (nextDay.equals(currentDay)) {
                        nameVisibility = View.INVISIBLE;
                    }
                } else {
                    nameVisibility = View.INVISIBLE;
                }
            }
        }

        //Assign the holder:
        switch (holder.getItemViewType()){
            case VIEW_TYPE_USER_DEFAULT:
                ((UserDefaultHolder) holder).bind(message,messageDate,messageText,timestampVisibility,nameVisibility);
                break;
            case VIEW_TYPE_OTHER_DEFAULT:
                ((OtherDefaultHolder) holder).bind(message,userName,messageDate,messageText,timestampVisibility,nameVisibility);
                break;
            case VIEW_TYPE_USER_DAYBREAK:
                ((UserDaybreakHolder) holder).bind(message,messageDate,currentDay,messageText,timestampVisibility,nameVisibility);
                break;
            case VIEW_TYPE_OTHER_DAYBREAK:
                ((OtherDaybreakHolder) holder).bind(message,userName,messageDate,currentDay,messageText,timestampVisibility,nameVisibility);
                break;
        }
    }

    //ViewHolders
    private class UserDefaultHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView messageTextView,authorTextView,txtTimestamp,dayTimestamp;
        LinearLayout textBubble;
        ConstraintLayout dayTimestampLayout;

        UserDefaultHolder(View v) {
            super(v);

            photoImageView = v.findViewById(R.id.photoImageView);
            messageTextView = v.findViewById(R.id.messageTextView);
            authorTextView = v.findViewById(R.id.nameTextView);
            txtTimestamp = v.findViewById(R.id.txtTimestamp);
            textBubble = v.findViewById(R.id.textBubble);
            dayTimestamp = v.findViewById(R.id.dayTimestamp);
            dayTimestampLayout = v.findViewById(R.id.dayTimestampLayout);
        }

        void bind(Sms message, String messageDate, String messageText, int timestampVisibility, int nameVisibility) {
            txtTimestamp.setText(messageDate);
            messageTextView.setText(messageText);
            setInitialMaxWidth(txtTimestamp,messageTextView);

            authorTextView.setVisibility(nameVisibility);

            //Set the max width of the text bubbles based on the screen width and the size of the timestamp. There's probably
            // a much more efficient way to do this! Checking the timeStampWidth == 0 helps but a more efficient method is required.
            if (timestampWidth == 0) {
                ViewTreeObserver vto = txtTimestamp.getViewTreeObserver();
                vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        timestampWidth = txtTimestamp.getWidth() + txtTimestamp.getPaddingEnd() + txtTimestamp.getPaddingStart();
                        messageTextView.setMaxWidth(width - timestampWidth);
                        txtTimestamp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
            } else {
                messageTextView.setMaxWidth(width - timestampWidth);
            }

            txtTimestamp.setVisibility(timestampVisibility);

            selectListItem(message,messageTextView,textBubble);
        }
    }

    private class UserDaybreakHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView messageTextView,authorTextView,txtTimestamp,dayTimestamp;
        LinearLayout textBubble;
        ConstraintLayout dayTimestampLayout;

        UserDaybreakHolder(View v) {
            super(v);

            photoImageView = v.findViewById(R.id.photoImageView);
            messageTextView = v.findViewById(R.id.messageTextView);
            authorTextView = v.findViewById(R.id.nameTextView);
            txtTimestamp = v.findViewById(R.id.txtTimestamp);
            textBubble = v.findViewById(R.id.textBubble);
            dayTimestamp = v.findViewById(R.id.dayTimestamp);
            dayTimestampLayout = v.findViewById(R.id.dayTimestampLayout);
        }

        void bind(Sms message, String messageDate, String dayDate, String messageText, int timestampVisibility, int nameVisibility) {
            txtTimestamp.setText(messageDate);
            messageTextView.setText(messageText);
            setInitialMaxWidth(txtTimestamp,messageTextView);

            authorTextView.setVisibility(nameVisibility);

            //Set the max width of the text bubbles based on the screen width and the size of the timestamp. There's probably
            // a much more efficient way to do this! Checking the timeStampWidth == 0 helps but a more efficient method is required.
            if (timestampWidth == 0) {
                ViewTreeObserver vto = txtTimestamp.getViewTreeObserver();
                vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        timestampWidth = txtTimestamp.getWidth() + txtTimestamp.getPaddingEnd() + txtTimestamp.getPaddingStart();
                        messageTextView.setMaxWidth(width - timestampWidth);
                        txtTimestamp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
            } else {
                messageTextView.setMaxWidth(width - timestampWidth);
            }

            txtTimestamp.setVisibility(timestampVisibility);

            dayTimestamp.setText(dayDate);

            selectListItem(message,messageTextView,textBubble);
        }
    }

    private class OtherDefaultHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView messageTextView,authorTextView,txtTimestamp,dayTimestamp;
        LinearLayout textBubble;
        ConstraintLayout dayTimestampLayout;

        OtherDefaultHolder(View v) {
            super(v);

            photoImageView = v.findViewById(R.id.photoImageView);
            messageTextView = v.findViewById(R.id.messageTextView);
            authorTextView = v.findViewById(R.id.nameTextView);
            txtTimestamp = v.findViewById(R.id.txtTimestamp);
            textBubble = v.findViewById(R.id.textBubble);
            dayTimestamp = v.findViewById(R.id.dayTimestamp);
            dayTimestampLayout = v.findViewById(R.id.dayTimestampLayout);
        }

        void bind(Sms message, String userName, String messageDate, String messageText, int timestampVisibility, int nameVisibility) {
            txtTimestamp.setText(messageDate);
            messageTextView.setText(messageText);
            setInitialMaxWidth(txtTimestamp,messageTextView);

            authorTextView.setVisibility(nameVisibility);
            authorTextView.setText(userName);

            //Set the max width of the text bubbles based on the screen width and the size of the timestamp. There's probably
            // a much more efficient way to do this! Checking the timeStampWidth == 0 helps but a more efficient method is required.
            if (timestampWidth == 0) {
                ViewTreeObserver vto = txtTimestamp.getViewTreeObserver();
                vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        timestampWidth = txtTimestamp.getWidth() + txtTimestamp.getPaddingEnd() + txtTimestamp.getPaddingStart();
                        messageTextView.setMaxWidth(width - timestampWidth);
                        txtTimestamp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
            } else {
                messageTextView.setMaxWidth(width - timestampWidth);
            }

            txtTimestamp.setVisibility(timestampVisibility);

            selectListItem(message,messageTextView,textBubble);
        }
    }

    private class OtherDaybreakHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView messageTextView,authorTextView,txtTimestamp,dayTimestamp;
        LinearLayout textBubble;
        ConstraintLayout dayTimestampLayout;

        OtherDaybreakHolder(View v) {
            super(v);

            photoImageView = v.findViewById(R.id.photoImageView);
            messageTextView = v.findViewById(R.id.messageTextView);
            authorTextView = v.findViewById(R.id.nameTextView);
            txtTimestamp = v.findViewById(R.id.txtTimestamp);
            textBubble = v.findViewById(R.id.textBubble);
            dayTimestamp = v.findViewById(R.id.dayTimestamp);
            dayTimestampLayout = v.findViewById(R.id.dayTimestampLayout);
        }

        void bind(Sms message, String userName, String messageDate, String dayDate, String messageText, int timestampVisibility, int nameVisibility) {
            txtTimestamp.setText(messageDate);
            messageTextView.setText(messageText);
            setInitialMaxWidth(txtTimestamp,messageTextView);

            authorTextView.setVisibility(nameVisibility);
            authorTextView.setText(userName);

            //Set the max width of the text bubbles based on the screen width and the size of the timestamp. There's probably
            // a much more efficient way to do this! Checking the timeStampWidth == 0 helps but a more efficient method is required.
            if (timestampWidth == 0) {
                ViewTreeObserver vto = txtTimestamp.getViewTreeObserver();
                vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        timestampWidth = txtTimestamp.getWidth() + txtTimestamp.getPaddingEnd() + txtTimestamp.getPaddingStart();
                        messageTextView.setMaxWidth(width - timestampWidth);
                        txtTimestamp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
            } else {
                messageTextView.setMaxWidth(width - timestampWidth);
            }

            txtTimestamp.setVisibility(timestampVisibility);

            dayTimestamp.setText(dayDate);

            selectListItem(message,messageTextView,textBubble);
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return listMessages.size();
    }

    private void selectListItem(Sms message, TextView tv, LinearLayout textBubble){
        boolean isUser = message.getFolderName().equals("sent");
        if (!message.isSelected()){
            if (isUser) {
                textBubble.setBackground(context.getDrawable(R.drawable.text_bubble_user));
            } else {
                textBubble.setBackground(context.getDrawable(R.drawable.text_bubble_other));
            }
            tv.setTextColor(Color.parseColor("#000000"));
        } else {
            if (isUser) {
                textBubble.setBackground(context.getDrawable(R.drawable.text_bubble_user_selected));
            } else {
                textBubble.setBackground(context.getDrawable(R.drawable.text_bubble_other_selected));
            }
            tv.setTextColor(context.getResources().getColor(R.color.colorTitle));
        }
    }

    private void setInitialMaxWidth(final TextView txtTimestamp, final TextView messageTextView){
        //Set the max width of the text bubbles based on the screen width and the size of the timestamp. There's probably
        // a much more efficient way to do this! Checking the timeStampWidth == 0 helps but a more efficient method is required.
        if (timestampWidth == 0) {
            ViewTreeObserver vto = txtTimestamp.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    timestampWidth = txtTimestamp.getWidth() + txtTimestamp.getPaddingEnd() + txtTimestamp.getPaddingStart();
                    messageTextView.setMaxWidth(width - timestampWidth);
                    txtTimestamp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        } else {
            messageTextView.setMaxWidth(width - timestampWidth);
        }
    }

    void setMaxWidth(int width){
        this.width = width;
        notifyDataSetChanged();
    }
}
