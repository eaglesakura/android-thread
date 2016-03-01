package com.eaglesakura.android.thread.async;

import com.eaglesakura.android.thread.async.error.TaskCanceledException;
import com.eaglesakura.android.thread.async.error.TaskException;
import com.eaglesakura.android.thread.async.error.TaskFailedException;
import com.eaglesakura.android.thread.async.error.TaskTimeoutException;
import com.eaglesakura.util.LogUtil;

public class AsyncTaskResult<T> {
    private final AsyncTaskController mController;

    AsyncTaskResult(AsyncTaskController pipeline) {
        this.mController = pipeline;
    }

    /**
     * 実行対象のタスク
     */
    IAsyncTask<T> mTask;

    T mResult;

    Exception mError;

    /**
     * リスナ
     */
    TaskListener<T> mListener;

    /**
     * キャンセル状態であればtrue
     */
    private boolean mCanceledTask;

    /**
     * リスナがキャンセル状態であればtrue
     */
    private boolean mCanceledListener;

    private CancelSignal mCancelSignal;

    private final Object mAwaitLock = new Object();

    private final Object mResultLock = new Object();

    /**
     * 実行をキャンセルする
     */
    public void cancel() {
        this.mCanceledTask = true;
    }

    /**
     * リスナの実行をキャンセルする
     */
    public void cancelListener() {
        mCanceledListener = true;
    }

    /**
     * タスク実行がキャンセルされていればtrue
     * cancel()されているか、CancelSignal.isCancelがtrueの場合キャンセルとなる
     */
    public boolean isCanceledTask() {
        if (mCancelSignal != null && mCancelSignal.isCanceled()) {
            return true;
        }
        return mCanceledTask;
    }

    /**
     * リスな呼び出しがキャンセルされていればtrue
     */
    public boolean isCanceledListener() {
        return mCanceledListener || mController.isCancelListeners();
    }

    /**
     * キャンセルチェック用のコールバックを指定する
     */
    public void setCancelSignal(CancelSignal cancelSignal) {
        this.mCancelSignal = cancelSignal;
    }

    /**
     * リスナを設定する
     * 排他仕様のため、1インスタンスだけが有効となる。
     */
    public AsyncTaskResult<T> setListener(Listener<T> listener) {
        return setListener((TaskListener<T>) listener);
    }

    /**
     * リスナを設定する
     * 排他仕様のため、1インスタンスだけが有効となる。
     */
    public AsyncTaskResult<T> setListener(CompletedListener<T> listener) {
        return setListener((TaskListener<T>) listener);
    }

    /**
     * リスナを設定する
     * 排他仕様のため、1インスタンスだけが有効となる。
     */
    public AsyncTaskResult<T> setListener(FailedListener<T> listener) {
        return setListener((TaskListener<T>) listener);
    }

    /**
     * リスナを設定する
     * 排他仕様のため、1インスタンスだけが有効となる。
     */
    public AsyncTaskResult<T> setListener(FinalizeListener<T> listener) {
        return setListener((TaskListener<T>) listener);
    }

    private AsyncTaskResult<T> setListener(TaskListener<T> listener) {
        synchronized (mResultLock) {
            // 既にタスクが完了してしまっている場合はリスナをコールさせてローカルに残さない
            if (isTaskFinished()) {
                handleListener(listener);
            } else {
                this.mListener = listener;
            }
        }
        return this;
    }

    /**
     * タスクの実行待ちを行う
     */
    public T await(long timeoutMs) throws TaskException {
        synchronized (mAwaitLock) {
            if (!isTaskFinished()) {
                try {
                    mAwaitLock.wait(timeoutMs);
                } catch (Exception e) {
                    LogUtil.log(e);
                }

                // 処理がタイムアウトした
                if (!isTaskFinished()) {
                    cancel(); // タイムアウトしたら強制キャンセルさせる
                    throw new TaskTimeoutException();
                }
            }
        }

        throwIfError();
        return mResult;
    }

    /**
     * タスクが成功、もしくは失敗・キャンセルしていたらtrue
     */
    public boolean isTaskFinished() {
        return mResult != null || mError != null || isCanceledTask();
    }

    /**
     * タスクを実行しているコントローラを取得する
     */
    public AsyncTaskController getController() {
        return mController;
    }

    /**
     * 実行を行う
     */
    final void execute() {
        T result = null;
        Exception error = null;

        try {
            if (isCanceledTask()) {
                // 既にキャンセルされている
                throw new TaskCanceledException();
            }

            result = mTask.doInBackground(this);
        } catch (Exception e) {
            error = e;
        }

        synchronized (mResultLock) {
            this.mResult = result;
            this.mError = error;
            handleListener(this.mListener);
            this.mListener = null;
        }

        synchronized (mAwaitLock) {
            mAwaitLock.notifyAll();
        }
    }

    /**
     * リスナのハンドリングを行う
     */
    void handleListener(final TaskListener<T> callListener) {
        if (callListener == null) {
            return;
        }
        mController.mTaskHandler.request(new Runnable() {
            @Override
            public void run() {
                // リスナが存在し、リスナーな呼び出しが許可されていれば呼び出す
                if (callListener != null && !isCanceledListener()) {
                    if (isCanceledTask()) {
                        // キャンセルされている
                        if (callListener instanceof FailedListener) {
                            ((FailedListener) callListener).onTaskCanceled(AsyncTaskResult.this);
                        }
                    } else if (mResult != null) {
                        // 成功した
                        if (callListener instanceof CompletedListener) {
                            ((CompletedListener) callListener).onTaskCompleted(AsyncTaskResult.this, mResult);
                        }
                    } else {
                        // 失敗した
                        if (callListener instanceof FailedListener) {
                            ((FailedListener) callListener).onTaskFailed(AsyncTaskResult.this, mError);
                        }
                    }

                    // 最後に呼び出す
                    if (callListener instanceof FinalizeListener) {
                        ((FinalizeListener) callListener).onTaskFinalize(AsyncTaskResult.this);
                    }
                }
            }
        });
    }

    /**
     * エラーが発生していたら例外を投げ、それ以外は何もしない
     */
    void throwIfError() throws TaskException {
        if (mError == null) {
            return;
        }
        throw new TaskFailedException(mError);
    }

    /**
     * タスクの戻り値を取得する。
     * タスク実行中や失敗時はnullである場合があるため注意する。
     */
    public T getResult() {
        return mResult;
    }

    /**
     * タスクの失敗値を取得する。
     * タスク成功時等はnullである。
     */
    public Exception getError() {
        return mError;
    }

    /**
     * キャンセルチェック用のコールバック
     * <p/>
     * cancel()メソッドを呼び出すか、このコールバックがisCanceled()==trueになった時点でキャンセル扱いとなる。
     */
    public interface CancelSignal {
        /**
         * キャンセルする場合はtrueを返す
         */
        boolean isCanceled();
    }

    public interface TaskListener<T> {
    }

    public interface CompletedListener<T> extends TaskListener<T> {
        /**
         * @param task
         * @param result
         */
        void onTaskCompleted(AsyncTaskResult<T> task, T result);
    }

    public interface FailedListener<T> extends TaskListener<T> {
        /**
         * タスクがキャンセルされた場合に呼び出される
         */
        void onTaskCanceled(AsyncTaskResult<T> task);

        /**
         * タスクが失敗した場合に呼び出される
         */
        void onTaskFailed(AsyncTaskResult<T> task, Exception error);
    }

    public interface FinalizeListener<T> extends TaskListener<T> {

        /**
         * タスクの完了時に必ず呼び出される
         */
        void onTaskFinalize(AsyncTaskResult<T> task);
    }

    /**
     * 全ての受け取りを行うリスナ
     */
    public interface Listener<T> extends CompletedListener<T>, FailedListener<T>, FinalizeListener<T> {
    }
}
