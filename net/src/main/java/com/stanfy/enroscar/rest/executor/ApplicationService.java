package com.stanfy.enroscar.rest.executor;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.stanfy.enroscar.net.operation.RequestDescription;
import com.stanfy.enroscar.utils.Time;

/**
 * Base application service which provides API and location methods interfaces.
 * @author Roman Mazur (Stanfy - http://www.stanfy.com)
 */
// TODO move this service to a separate 'tasks' project
public class ApplicationService extends Service {

  /** Logging tag. */
  protected static final String TAG = "AppService";
  /** Debug flag. */
  protected static final boolean DEBUG = DebugFlags.DEBUG;

  /** Check for stop message. */
  private static final int MSG_CHECK_FOR_STOP = 1;
  /** Check for stop delay. */
  private static final long DELAY_CHECK_FOR_STOP = Time.SECONDS / 2;

  /** Send request action. */
  public static final String ACTION_SEND_REQUEST = ApiMethods.class.getName() + "#SEND_REQUEST";

  /** Intent extra parameter name: request description. */
  public static final String EXTRA_REQUEST_DESCRIPTION = "request_description";
  /** Intent extra parameter name: request description. */
  public static final String EXTRA_REQUEST_DESCRIPTION_BUNDLE = "request_description_bundle";

  /** Handler instance. */
  private Handler handler;

  /** Recent start ID. */
  private int recentStartId = -1;

  /** API methods. */
  private ApiMethods apiMethods;

  /** Usage flags. */
  private AtomicBoolean apiMethodsUse = new AtomicBoolean(false), locationMethodsUse = new AtomicBoolean(false);

  /** @return API methods implementation */
  protected ApiMethods createApiMethods() { return new ApiMethods(this); }

  @Override
  public void onCreate() {
    super.onCreate();
    handler = new InternalHandler(this);
    if (DEBUG) { Log.d(TAG, "Service created"); }
  }

  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    if (DEBUG) { Log.d(TAG, "Start command"); }
    handler.removeMessages(MSG_CHECK_FOR_STOP);
    recentStartId = startId;

    if (intent != null) {
      if (ACTION_SEND_REQUEST.equals(intent.getAction())) {

        RequestDescription requestDescription = null;
        if (intent.hasExtra(EXTRA_REQUEST_DESCRIPTION)) {
          requestDescription = intent.getParcelableExtra(EXTRA_REQUEST_DESCRIPTION);
        } else if (intent.hasExtra(EXTRA_REQUEST_DESCRIPTION_BUNDLE)) {
          Bundle b = intent.getBundleExtra(EXTRA_REQUEST_DESCRIPTION_BUNDLE);
          if (b != null) {
            requestDescription = b.getParcelable(EXTRA_REQUEST_DESCRIPTION);
          }
        }

        if (requestDescription != null) {
          ensureApiMethods();
          apiMethods.performRequest(requestDescription);
        }
      }
    }

    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    if (apiMethods != null) {
      apiMethods.destroy();
    }
    if (DEBUG) { Log.d(TAG, "Service destroyed"); }
    super.onDestroy();
  }

  @Override
  public IBinder onBind(final Intent intent) {
    final String action = intent.getAction();
    if (DEBUG) { Log.d(TAG, "Binding to " + action); }
    if (action == null) { return null; }

    if (action.equals(ApiMethods.class.getName())) {
      apiMethodsUse.set(true);
      ensureApiMethods();
      return new ApiMethodsBinder(apiMethods);
    }

    return null;
  }

  @Override
  public void onRebind(final Intent intent) {
    final String action = intent.getAction();
    if (action == null) { return; }
    if (DEBUG) { Log.d(TAG, "Rebinding to " + action); }
    if (action.equals(ApiMethods.class.getName())) {
      apiMethodsUse.set(true);
    }
  }

  @Override
  public boolean onUnbind(final Intent intent) {
    final String action = intent.getAction();
    if (action == null) { return false; }
    if (DEBUG) { Log.d(TAG, "Unbind from " + action); }
    if (apiMethods != null && action.equals(ApiMethods.class.getName())) {
      apiMethodsUse.set(false);
      checkForStop();
    }
    return true;
  }

  /**
   * Perform check for stop with default {@link ApplicationService#DELAY_CHECK_FOR_STOP} delay.
   * This check is automatically performed each time someone unbinds from service or request finished.
   */
  protected void checkForStop() {
    checkForStop(DELAY_CHECK_FOR_STOP);
  }
  /**
   * Perform check for stop with given delay.
   *
   * @param delay time in milliseconds
   */
  protected void checkForStop(final long delay) {
    if (DEBUG) { Log.d(TAG, "Schedule check for stop"); }
    handler.removeMessages(MSG_CHECK_FOR_STOP);
    handler.sendEmptyMessageDelayed(MSG_CHECK_FOR_STOP, delay);
  }

  /**
   * Stop the service.
   */
  protected void doStop() {
    boolean reallyStopping = stopSelfResult(recentStartId);
    if (DEBUG && !reallyStopping) {
      Log.d(TAG, "Not stopped. Got another start command");
    }
  }

  /**
   * @return instance of {@link ApiMethods}
   */
  protected ApiMethods getApiMethods() { return apiMethods; }

  private void ensureApiMethods() {
    if (apiMethods == null) {
      apiMethods = createApiMethods();
    }
  }

  /** API methods binder. */
  public static class ApiMethodsBinder extends Binder {
    /** API methods. */
    private final ApiMethods apiMethods;

    public ApiMethodsBinder(final ApiMethods apiMethods) {
      this.apiMethods = apiMethods;
    }

    public ApiMethods getApiMethods() { return apiMethods; }
  }

  /** Internal handler. */
  protected static class InternalHandler extends Handler {

    /** Service instance. */
    private final WeakReference<ApplicationService> serviceRef;

    /**
     * @param service service instance
     */
    public InternalHandler(final ApplicationService service) {
      this.serviceRef = new WeakReference<ApplicationService>(service);
    }

    @Override
    public void handleMessage(final Message msg) {
      final ApplicationService service = serviceRef.get();
      if (service == null) { return; }

      switch (msg.what) {
      case MSG_CHECK_FOR_STOP:
        // here we decide whether to stop the service
        if (DEBUG) { Log.d(TAG, "Check for stop"); }
        final boolean hasUsers = service.apiMethodsUse.get() || service.locationMethodsUse.get();
        if (hasUsers) {
          if (DEBUG) { Log.d(TAG, "We have users"); }
        } else {
          if (service.apiMethods != null && service.apiMethods.isWorking()) {
            if (DEBUG) { Log.d(TAG, "Api is working"); }
          } else {
            if (DEBUG) { Log.d(TAG, "Stopping"); }
            service.doStop();
          }
        }
        break;
      default:
      }
    }

  }

}
