package com.eaglesakura.android.thread.async;

import com.eaglesakura.android.thread.ui.UIHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncTaskController {
    final long KEEPALIVE_TIME_MS;

    /**
     * 並列実行されるタスクキュー
     * 並列度合いはスレッド数に依存する
     */
    final ThreadPoolExecutor mThreads;

    /**
     * スレッドの参照数
     */
    final AtomicInteger mThreadRefs;

    /**
     * 実行待ちのキュー
     */
    final List<AsyncTaskResult<?>> mTaskQueue = Collections.synchronizedList(new LinkedList<AsyncTaskResult<?>>());

    /**
     * 実行中
     */
    final List<AsyncTaskResult<?>> mRunningTasks = Collections.synchronizedList(new LinkedList<AsyncTaskResult<?>>());

    /**
     * コントローラを解放済みであればtrue
     */
    boolean mDisposed;

    /**
     * 全てのリスナをキャンセルする
     */
    boolean mCancelListeners = false;

    ITaskHandler mTaskHandler = new ITaskHandler() {
        @Override
        public void request(final Runnable runner) {
            UIHandler.postUIorRun(new Runnable() {
                @Override
                public void run() {
                    runner.run();
                }
            });
        }
    };

    /**
     * スレッド数を指定してコントローラを生成する
     * <p/>
     * 標準では15秒のスレッドキープを行う
     */
    public AsyncTaskController(int threads) {
        this(threads, 1000 * 15);
    }

    /**
     * スレッド数とキープ時間を指定してコントローラを生成する
     */
    public AsyncTaskController(int threads, long keepAliveTimeMs) {
        this.KEEPALIVE_TIME_MS = keepAliveTimeMs;
        this.mThreads = new ThreadPoolExecutor(1, threads, KEEPALIVE_TIME_MS, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        mThreadRefs = new AtomicInteger(1);
    }

    /**
     * スレッドを共有してコントローラを生成する
     *
     * @param sharedThreads 共有対象のコントローラ
     */
    public AsyncTaskController(AsyncTaskController sharedThreads) {
        KEEPALIVE_TIME_MS = sharedThreads.KEEPALIVE_TIME_MS;
        mThreads = sharedThreads.mThreads;
        mThreadRefs = sharedThreads.mThreadRefs;
        mThreadRefs.incrementAndGet();
    }

    /**
     * スレッド操作へのアクセサ
     */
    public Executor getExecutor() {
        return mThreads;
    }

    /**
     * ハンドリングクラスを指定する
     */
    public void setTaskHandler(ITaskHandler taskHandler) {
        this.mTaskHandler = taskHandler;
    }

    /**
     * リスな呼び出しがキャンセルされていたらtrue
     */
    public boolean isCancelListeners() {
        return mCancelListeners;
    }

    /**
     * リスナの呼び出しをキャンセルする
     */
    public void cancelListeners() {
        mCancelListeners = true;
    }

    /**
     * 現在の実行待ちキューを取得する
     */
    public List<AsyncTaskResult<?>> getTaskQueue() {
        return new ArrayList<>(mTaskQueue);
    }

    /**
     * 現在実行中のタスクを取得する
     */
    public List<AsyncTaskResult<?>> getRunningTasks() {
        return new ArrayList<>(mRunningTasks);
    }

    private synchronized <T> AsyncTaskResult<T> pushTask(boolean front, IAsyncTask<T> task) {
        AsyncTaskResult<T> result = new AsyncTaskResult<>(this);
        result.mTask = task;

        // タスクを追加する
        if (!mDisposed) {

            if (front) {
                mTaskQueue.add(0, result);
            } else {
                mTaskQueue.add(result);
            }

            mThreads.execute(runner);
        }
        return result;
    }

    /**
     * タスクを末尾に追加する
     */
    public <T> AsyncTaskResult<T> pushBack(IAsyncTask<T> task) {
        return pushTask(false, task);
    }

    /**
     * @param task
     * @return
     */
    public AsyncTaskResult<AsyncTaskController> pushBack(final Runnable task) {
        return pushBack(new IAsyncTask<AsyncTaskController>() {
            @Override
            public AsyncTaskController doInBackground(AsyncTaskResult<AsyncTaskController> result) throws Exception {
                task.run();
                return AsyncTaskController.this;
            }
        });
    }

    /**
     * タスクを先頭に追加する
     */
    public <T> AsyncTaskResult<T> pushFront(IAsyncTask<T> task) {
        return pushTask(true, task);
    }

    /**
     * タスクを末尾に追加する
     */
    public AsyncTaskResult<AsyncTaskController> pushFront(final Runnable task) {
        return pushFront(new IAsyncTask<AsyncTaskController>() {
            @Override
            public AsyncTaskController doInBackground(AsyncTaskResult<AsyncTaskController> result) throws Exception {
                task.run();
                return AsyncTaskController.this;
            }
        });
    }

    /**
     * すべての未実行タスクをして資源を解放する
     */
    public void dispose() {
        mDisposed = true;
        mTaskQueue.clear();

        // スレッドの参照がなくなったらシャットダウンする
        if (mThreadRefs.decrementAndGet() == 0) {
            mThreads.shutdown();
        }
    }

    /**
     * タスクを実行する
     */
    private void executeTask() {
        if (mTaskQueue.isEmpty()) {
            return;
        }

        AsyncTaskResult<?> taskResult = mTaskQueue.remove(0);
        mRunningTasks.add(taskResult);   // 実行中に登録
        {
            taskResult.execute();
        }
        mRunningTasks.remove(taskResult);    // 実行中から削除
    }

    final Runnable runner = new Runnable() {
        @Override
        public void run() {
            executeTask();
        }
    };
}
