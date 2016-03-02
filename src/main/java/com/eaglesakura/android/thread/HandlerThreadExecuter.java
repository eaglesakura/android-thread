package com.eaglesakura.android.thread;

import com.eaglesakura.android.thread.ui.UIHandler;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

/**
 * タスクをキャッシュし、指定したハンドラで一括実行する
 */
public class HandlerThreadExecuter {

    List<Runnable> runners = new ArrayList<>();

    Handler handler = UIHandler.getInstance();

    public HandlerThreadExecuter() {
    }

    public void add(Runnable runnable) {
        synchronized (runners) {
            runners.add(runnable);
        }
    }

    /**
     * 実行スレッドを指定する
     */
    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public Handler getHandler() {
        return handler;
    }

    /**
     * キューを実行する
     */
    public void execute() {
        synchronized (runners) {
            if (runners.isEmpty()) {
                return;
            }
        }

        handler.post(new Runnable() {

            @Override
            public void run() {
                synchronized (runners) {
                    for (Runnable r : runners) {
                        r.run();
                    }
                    runners.clear();
                }
            }
        });
    }
}
