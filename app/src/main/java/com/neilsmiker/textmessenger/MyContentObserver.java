package com.neilsmiker.textmessenger;

import android.database.ContentObserver;
import android.os.Handler;

class MyContentObserver extends ContentObserver {
    private ContentObserverCallbacks contentObserverCallbacks;

    MyContentObserver(Handler h) {
        super(h);
    }

    @Override
    public boolean deliverSelfNotifications() {
        return true;
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);

        contentObserverCallbacks.updateMessageFeed();
    }

    void setCallbacks(ContentObserverCallbacks callbacks) {
        contentObserverCallbacks = callbacks;
    }

    public interface ContentObserverCallbacks {
        void updateMessageFeed();
    }
}

