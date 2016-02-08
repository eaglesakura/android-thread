package com.eaglesakura.android.thread.async;

import com.eaglesakura.android.thread.ui.UIHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * タスクを必要に応じてキャッシュ可能なハンドラ。
 * onPause中に実行を一旦止めることができる。
 */
public class CachedTaskHandler implements ITaskHandler {
    boolean mPending = true;

    List<Runnable> mRequests = new ArrayList<>();

    public CachedTaskHandler() {
    }

    /**
     * ハンドラの実行を一時停止する
     */
    public void onPause() {
        mPending = true;
    }

    /**
     * ハンドラの実行を開始する。
     * pendingに登録されているタスクがある場合、このタイミングで一括実行される。s
     */
    public void onResume() {
        mPending = false;
        UIHandler.postUI(mRunnerImpl);
    }

    @Override
    public void request(Runnable runner) {
        synchronized (mRequests) {
            mRequests.add(runner);
        }
        UIHandler.postUIorRun(mRunnerImpl);
    }

    /**
     * 実際のUIスレッド実行を行う
     */
    private Runnable mRunnerImpl = new Runnable() {
        @Override
        public void run() {
            if (mPending) {
                return;
            }

            List<Runnable> tasks;
            synchronized (mRequests) {
                tasks = new ArrayList<>(mRequests);
                mRequests.clear();
            }

            for (Runnable runner : tasks) {
                runner.run();
            }
        }
    };
}
