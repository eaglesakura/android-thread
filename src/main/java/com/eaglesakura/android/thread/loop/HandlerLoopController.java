package com.eaglesakura.android.thread.loop;

import android.os.Handler;

import com.eaglesakura.android.thread.async.AsyncHandler;
import com.eaglesakura.android.thread.ui.UIHandler;
import com.eaglesakura.util.Timer;

/**
 * 指定のハンドラでループ処理を行うUtilクラス
 */
public abstract class HandlerLoopController {
    final private Handler mHandler;

    /**
     * フレームレート
     * 1未満の場合は2秒に１回等の処理を行うが、正確性は問わない
     */
    private double mFrameRate = 60;

    /**
     * ループが継続されていればtrue
     */
    private boolean mLooping = false;

    /**
     * 解放済みの場合はtrue
     */
    private boolean mDisposed = false;

    final protected Object lock = new Object();

    /**
     * ループ時間管理用タイマー
     */
    private Timer mTimer = new Timer();

    /**
     * 前のフレームからの経過時間
     */
    private double mDeltaTime = 1.0;

    public HandlerLoopController(Handler handler) {
        if (handler != null) {
            this.mHandler = handler;
        } else {
            this.mHandler = AsyncHandler.createInstance("HandlerLoopController");
        }
    }

    /**
     * フレームレートの設定
     */
    public void setFrameRate(double frameRate) {
        this.mFrameRate = frameRate;
    }

    public double getFrameRate() {
        return mFrameRate;
    }

    /**
     * 処理を開始する
     */
    public void connect() {
        mLooping = true;
        mHandler.removeCallbacks(loopRunner);
        mHandler.post(loopRunner);
    }

    /**
     * 処理を終了する
     */
    public void disconnect() {
        mLooping = false;
        mHandler.removeCallbacks(loopRunner);
    }

    /**
     * 開放処理を行う
     */
    public void dispose() {
        if (mHandler != UIHandler.getInstance()) {
            mHandler.getLooper().quit();
        }
    }

    /**
     * このハンドラに処理を行わせる
     */
    public void post(Runnable runnable) {
        mHandler.post(runnable);
    }

    /**
     * 前のフレームからのデルタ時間を取得する
     */
    public double getDeltaTime() {
        return mDeltaTime;
    }

    /**
     * 使用しているハンドラを取得する
     */
    public Handler getHandler() {
        return mHandler;
    }

    /**
     * 更新を行う
     */
    protected abstract void onUpdate();


    private Runnable loopRunner = new Runnable() {
        @Override
        public void run() {
            final long FRAME_TIME = (long) (1000.0 / mFrameRate); // 1フレームの許容時間
            // デルタ時間を計算
            long deltaMs = mTimer.end();
            if (deltaMs > 0) {
                mDeltaTime = (double) deltaMs / 1000.0f;
            } else {
                mDeltaTime = (double) FRAME_TIME / 1000.0f;
            }
            mTimer.start();
            // 更新を行わせる
            onUpdate();

            final long UPDATE_TIME = mTimer.end();
            if (mLooping) {
                mHandler.postDelayed(this, Math.max(1, FRAME_TIME - UPDATE_TIME));   // 1フレームにかけた時間を差し引いてpostする
            }
        }
    };
}
