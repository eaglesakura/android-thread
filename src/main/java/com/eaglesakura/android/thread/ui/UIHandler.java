package com.eaglesakura.android.thread.ui;

import com.eaglesakura.android.util.AndroidThreadUtil;
import com.eaglesakura.util.LogUtil;

import android.os.Handler;
import android.os.Looper;

/**
 * UIスレッド専用のハンドラ
 */
public class UIHandler extends Handler {

    public UIHandler() {
        super(Looper.getMainLooper());
    }

    private static UIHandler instance = null;

    /**
     * 唯一のインスタンスを取得する。
     *
     * @return UIHandlerインスタンス
     */
    public static UIHandler getInstance() {
        if (instance == null) {
            instance = new UIHandler();
        }
        return instance;
    }

    /**
     * UIスレッドで実行を行わせる。
     */
    public static void postUI(Runnable runnable) {
        getInstance().post(runnable);
    }

    /**
     * UIThreadにPostするか、UIThreadの場合はその場で実行する
     */
    public static void postUIorRun(Runnable runnable) {
        if (AndroidThreadUtil.isUIThread()) {
            runnable.run();
        } else {
            postUI(runnable);
        }
    }

    /**
     * 指定したディレイをかけてPOSTする
     */
    public static void postDelayedUI(Runnable runnable, long delay) {
        getInstance().postDelayed(runnable, delay);
    }

    /**
     * 古いタスクをremoveしてから再度postする
     * @param runnable
     * @param delay
     */
    public static void rePostDelayedUI(Runnable runnable, long delay) {
        UIHandler instance = getInstance();
        instance.removeCallbacks(runnable);
        instance.postDelayed(runnable, delay);
    }

    /**
     * UIスレッドにPOSTし、実行終了を待つ
     */
    public static void postWithWait(final Runnable runnable, long timeoutMs) {
        if (AndroidThreadUtil.isUIThread()) {
            runnable.run();
        } else {
            final Object sync = new Object();

            UIHandler.postUI(new Runnable() {
                @Override
                public void run() {
                    runnable.run();
                    synchronized (sync) {
                        sync.notifyAll();
                    }
                }
            });

            synchronized (sync) {
                try {
                    sync.wait(timeoutMs);
                } catch (InterruptedException e) {
                    LogUtil.log(e);
                }
            }
        }
    }

}
