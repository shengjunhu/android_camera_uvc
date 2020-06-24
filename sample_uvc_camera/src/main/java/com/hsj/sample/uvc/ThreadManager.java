package com.hsj.sample.uvc;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author:hsj
 * @Date:2019-07-23 16:50
 * @Class:ThreadManager
 * @Desc:线程管理器
 */
public final class ThreadManager {

    private ThreadManager() {
    }

    /**
     * UI线程/主线程
     */
    private static Handler mMainHandler;
    private static final Object MAIN_HANDLER_LOCK = new Object();

    /**
     * 取得UI线程Handler
     *
     * @return
     */
    public static Handler getMainHandler() {
        if (mMainHandler == null) {
            synchronized (MAIN_HANDLER_LOCK) {
                if (mMainHandler == null) {
                    mMainHandler = new Handler(Looper.getMainLooper());
                }
            }
        }
        return mMainHandler;
    }

//=====================================Executor=====================================================

    /**
     * 多任务线程池[例如网络操作]
     */
    private static final ExecutorService EXECUTOR;
    //队列限制长度，防止队列任务过多发生OOM
    private static final int QUEUE_SIZE;
    private static LinkedBlockingQueue<Runnable> QUEUE;

    /**
     * 初始化线程池
     */
    static {
        QUEUE_SIZE = 1000;
        QUEUE = new LinkedBlockingQueue<>(QUEUE_SIZE);
        //Android端通常可直接设置int corePoolSize = 3,int maximumPoolSize = 5
        EXECUTOR = new ThreadPoolExecutor(1, 2, 3, TimeUnit.SECONDS, QUEUE);
    }

    /**
     * 线程池任务
     *
     * @param run
     * @return
     */
    public static Future<?> executeOnThreadPool(@NonNull Runnable run) {
        return EXECUTOR.submit(run);
    }

    /**
     * 线程池任务
     *
     * @param run
     * @param isPostAtFrontOfQueue
     * @return
     */
    public static boolean executeOnThreadPool(@NonNull Runnable run, boolean isPostAtFrontOfQueue) {
        if (isPostAtFrontOfQueue) {
            EXECUTOR.execute(run);
            return true;
        } else {
            if (QUEUE.size() < QUEUE_SIZE) {
                return QUEUE.offer(run);
            } else {
                return false;
            }
        }
    }

    /**
     * 清空线程池中所有任务
     */
    public static boolean removeThreadPoolTask(@NonNull Runnable run) {
        return QUEUE.remove(run);
    }

    /**
     * 清空线程池中所有任务
     */
    public static void clearThreadPoolTask() {
        QUEUE.clear();
    }

//=====================================File Thread==================================================

    /**
     * 文件操作线程[任务是阻塞的]
     */
    private static Handler FILE_THREAD_HANDLER;
    private static HandlerThread FILE_THREAD;

    /**
     * 在文件操作线程执行任务
     *
     * @param run
     * @return
     */
    public static boolean executeOnFileThread(@NonNull Runnable run) {
        return  getSubThreadHandler().post(run);
    }

    /**
     * 获取文件线程
     *
     * @return
     */
    public static Thread getFileThread() {
        if (FILE_THREAD == null) {
            getFileThreadHandler();
        }
        return FILE_THREAD;
    }

    /**
     * 获取文件操作线程Handler
     *
     * @return
     */
    public static Handler getFileThreadHandler() {
        if (FILE_THREAD_HANDLER == null) {
            synchronized (ThreadManager.class) {
                FILE_THREAD = new HandlerThread("FILE_THREAD");
                FILE_THREAD.start();
                FILE_THREAD_HANDLER = new Handler(FILE_THREAD.getLooper());
            }
        }
        return FILE_THREAD_HANDLER;
    }

    /**
     * 获取文件操作线程Handler
     *
     * @return
     */
    public static Looper getFileThreadLooper() {
        return getFileThreadHandler().getLooper();
    }

    /**
     * 清空副线程任务
     */
    public static void clearFileTask(@NonNull Runnable run) {
        if (FILE_THREAD_HANDLER != null) {
            FILE_THREAD_HANDLER.removeCallbacks(run);
        }
    }

    /**
     * shutdown副线程[非必要情况不建议关闭]
     */
    public static void shutdownFileThread() {
        if (FILE_THREAD_HANDLER != null) {
            FILE_THREAD_HANDLER.getLooper().quitSafely();
        }
        if (FILE_THREAD != null) {
            FILE_THREAD.quitSafely();
        }
        FILE_THREAD_HANDLER = null;
        FILE_THREAD = null;
    }

//=====================================SUB Thread===================================================

    /**
     * 副线程执行比较块的任务
     */
    private static Handler SUB_THREAD_HANDLER;
    private static HandlerThread SUB_THREAD;

    /**
     * 在副线程执行任务
     *
     * @param run
     * @return
     */
    public static boolean executeOnSubThread(@NonNull Runnable run) {
        return getSubThreadHandler().post(run);
    }

    /**
     * 获取副线程
     *
     * @return
     */
    public static Thread getSubThread() {
        if (SUB_THREAD == null) {
            getSubThreadHandler();
        }
        return SUB_THREAD;
    }

    /**
     * 获取副线程Handler
     *
     * @return
     */
    public static Handler getSubThreadHandler() {
        if (SUB_THREAD_HANDLER == null) {
            synchronized (ThreadManager.class) {
                SUB_THREAD = new HandlerThread("SUB_THREAD");
                SUB_THREAD.start();
                SUB_THREAD_HANDLER = new Handler(SUB_THREAD.getLooper());
            }
        }
        return SUB_THREAD_HANDLER;
    }

    /**
     * 获取副线程Handler
     *
     * @return
     */
    public static Looper getSubThreadLooper() {
        return getSubThreadHandler().getLooper();
    }

    /**
     * 清空副线程任务
     */
    public static void clearSubTask(@NonNull Runnable run) {
        if (SUB_THREAD_HANDLER != null) {
            SUB_THREAD_HANDLER.removeCallbacks(run);
        }
    }

    /**
     * shutdown副线程[非必要情况不建议关闭]
     */
    public static void shutdownSubThread() {
        if (SUB_THREAD_HANDLER != null) {
            SUB_THREAD_HANDLER.getLooper().quitSafely();
        }
        if (SUB_THREAD != null) {
            SUB_THREAD.quitSafely();
        }
        SUB_THREAD_HANDLER = null;
        SUB_THREAD = null;
    }

}

