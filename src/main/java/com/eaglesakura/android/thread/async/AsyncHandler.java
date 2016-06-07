package com.eaglesakura.android.thread.async;

import com.eaglesakura.util.ThrowableRunnable;
import com.eaglesakura.util.ThrowableRunner;

import android.os.Handler;
import android.os.HandlerThread;

/**
 * 非同期実行用のハンドラ生成Util
 */
public class AsyncHandler extends Handler {
    final HandlerThread thread;

    public AsyncHandler(HandlerThread thread) {
        super(thread.getLooper());
        this.thread = thread;
    }

    /**
     * ハンドラとスレッドを廃棄する。
     * これの呼び出し以降、ハンドラは正常に動作しない。
     */
    public void dispose() {
        try {
            boolean handlerThread = isHandlerThread();
            thread.quit();
            if (!handlerThread) {
                thread.join();
            }
        } catch (Exception e) {

        }
    }

    public <ReturnType, ErrorType extends Throwable> ReturnType await(ThrowableRunnable<ReturnType, ErrorType> action) throws ErrorType {
        ThrowableRunner<ReturnType, ErrorType> runner = new ThrowableRunner<>(action);
        post(runner);
        return runner.await();
    }

    /**
     * 所属しているスレッドを取得する。
     */
    public Thread getThread() {
        return getLooper().getThread();
    }

    /**
     * ハンドラと同じスレッドの場合はtrue
     */
    public boolean isHandlerThread() {
        return Thread.currentThread().equals(getThread());
    }

    /**
     * ハンドラを生成する。
     */
    public static AsyncHandler createInstance(String name) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new AsyncHandler(thread);
    }
}
