package com.stanfy.enroscar.goro;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Internal queues.
 */
interface Queues {

  /**
   * Set main executor used to really perform tasks.
   * @param mainExecutor executor that tasks are delegated to
   */
  void setDelegateExecutor(Executor mainExecutor);

  /**
   * @param queueName queue name
   * @return executor that performs all the tasks in a given queue
   */
  Executor getExecutor(String queueName);

  /** Default implementation. */
  class Impl implements Queues {

    /** Thread pool parameter. */
    private static final int CORE_POOL_SIZE = 5,
        MAXIMUM_POOL_SIZE = 32,
        KEEP_ALIVE = 7,
        MAX_QUEUE_LENGTH = 100;

    /** Default threads pool. */
    private static Executor defaultThreadPoolExecutor;

    /** Executors map. */
    private final HashMap<String, Executor> executorsMap = new HashMap<>();

    /** Used threads pool. */
    private Executor delegateExecutor;

    private static Executor getDefaultThreadPoolExecutor() {
      if (defaultThreadPoolExecutor == null) {
        Executor executor = getAsyncTaskThreadPool();
        if (executor == null) {
          final AtomicInteger threadCounter = new AtomicInteger();
          //noinspection NullableProblems
          ThreadFactory tFactory = new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
              return new Thread(r, "Goro Thread #" + threadCounter.incrementAndGet());
            }
          };
          final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(MAX_QUEUE_LENGTH);
          executor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
              KEEP_ALIVE, TimeUnit.SECONDS, queue, tFactory);
        }
        defaultThreadPoolExecutor = executor;
      }
      return defaultThreadPoolExecutor;
    }

    @SuppressLint("NewApi")
    private static Executor getAsyncTaskThreadPool() {
      return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
          ? AsyncTask.THREAD_POOL_EXECUTOR
          : null;
    }

    @Override
    public void setDelegateExecutor(final Executor mainExecutor) {
      if (mainExecutor == null) {
        throw new IllegalArgumentException("Null threads pool");
      }
      synchronized (executorsMap) {
        if (!executorsMap.isEmpty()) {
          throw new IllegalStateException("Delegate executor cannot be changed after any queue is created");
        }
        this.delegateExecutor = mainExecutor;
      }
    }

    @Override
    public Executor getExecutor(final String queueName) {
      synchronized (executorsMap) {
        if (delegateExecutor == null) {
          delegateExecutor = getDefaultThreadPoolExecutor();
        }

        if (queueName == null) {
          return delegateExecutor;
        }

        Executor exec = executorsMap.get(queueName);
        if (exec == null) {
          exec = new TaskQueueExecutor(delegateExecutor);
          executorsMap.put(queueName, exec);
        }
        return exec;
      }
    }

  }

  /** Executor for the task queue. */
  final class TaskQueueExecutor implements Executor {
    /** Delegate executor. */
    final Executor delegate;
    /** Tasks queue. */
    final LinkedList<Runnable> tasks = new LinkedList<>();
    /** Active task. */
    Runnable activeTask;

    public TaskQueueExecutor(final Executor delegate)  {
      this.delegate = delegate;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public synchronized void execute(final Runnable r) {
      tasks.offer(new Runnable() {
        @Override
        public void run() {
          try {
            r.run();
          } finally {
            scheduleNext();
          }
        }
      });
      if (activeTask == null) {
        scheduleNext();
      }
    }

    synchronized void scheduleNext() {
      activeTask = tasks.poll();
      if (activeTask != null) {
        delegate.execute(activeTask);
      }
    }

  }

}
