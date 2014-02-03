package com.stanfy.enroscar.net.operation;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.content.Loader;
import android.util.Log;

import com.stanfy.enroscar.beans.BeansManager;
import com.stanfy.enroscar.content.ResponseData;
import com.stanfy.enroscar.rest.EntityTypeToken;
import com.stanfy.enroscar.rest.RemoteServerApiConfiguration;
import com.stanfy.enroscar.net.operation.executor.RequestExecutor;
import com.stanfy.enroscar.rest.Utils;
import com.stanfy.enroscar.rest.loader.RequestBuilderLoader;
import com.stanfy.enroscar.rest.request.binary.AssetFdBinaryData;
import com.stanfy.enroscar.rest.request.binary.BitmapBinaryData;
import com.stanfy.enroscar.rest.request.binary.ContentUriBinaryData;
import com.stanfy.enroscar.rest.request.binary.EmptyBinaryData;

import java.io.File;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Base class for request builders.
 * @param <MT> model type
 * @author Roman Mazur - Stanfy (http://www.stanfy.com)
 */
public abstract class BaseRequestBuilder<MT> implements RequestBuilder<MT> {

  /** Invalid request identifier. */
  public static final int INVALID_REQUEST_IDENTIFIER = -1;

  /** Logging tag. */
  private static final String TAG = "RequestBuilder";

  /** Date format. */
  private final SimpleDateFormat dateFormat = new SimpleDateFormat(getDateTimeFormat(), Locale.US);

  /** Configuration. */
  private final RemoteServerApiConfiguration config;
  /** Result object. */
  private final RequestDescription result;

  /** Context. */
  private final Context context;

  /** Class of the expected model. */
  private final EntityTypeToken expectedModelType;

  /** Performer. */
  private RequestExecutor executor;

  public BaseRequestBuilder(final Context context) {
    this.config = BeansManager.get(context).getContainer().getBean(RemoteServerApiConfiguration.BEAN_NAME, RemoteServerApiConfiguration.class);
    if (this.config == null) {
      throw new IllegalStateException("RemoteServerApiConfiguration bean is not added to the container");
    }
    
    this.context = context.getApplicationContext();
    this.result = config.createRequestDescription();

    result.simpleParameters = new ParametersGroup();
    result.simpleParameters.name = "stub";
    result.contentLanguage = Locale.getDefault().getLanguage();

    this.expectedModelType = EntityTypeToken.fromClassParameter(getClass());
    result.modelType = this.expectedModelType;
  }

  /**
   * Override this method in order to provide custom dates format for {@link #parseDate(String)} and {@link #formatDate(Date)}.
   * @return date format
   */
  protected String getDateTimeFormat() { return "yyyy-MM-dd HH:mm:ss Z"; }

  /**
   * @param d date instance
   * @return formatted string (see {@link #getDateTimeFormat()})
   */
  protected String formatDate(final Date d) { return d != null ? dateFormat.format(d) : null; }
  /**
   * @param d date string
   * @return date instance parsed with {@link #getDateTimeFormat()} format
   */
  protected Date parseDate(final String d) {
    if (d == null) { return null; }
    try {
      return dateFormat.parse(d);
    } catch (final ParseException e) {
      Log.e(TAG, "Cannot parse date " + d, e);
      return null;
    }
  }

  /**
   * @param type type token describing the data type for the request
   */
  protected void setModelType(final Type type) {
    result.modelType = EntityTypeToken.fromEntityType(type);
  }

  @Override
  public BaseRequestBuilder<MT> setExecutor(final RequestExecutor executor) {
    this.executor = executor;
    return this;
  }

  /**
   * @param url URL to set
   */
  protected void setTargetUrl(final String url) {
    result.url = url;
  }

  /**
   * @param operationType operation type
   * @see OperationType
   */
  protected void setRequestOperationType(final int operationType) {
    result.operationType = operationType;
  }

  /**
   * @param name cache manager bean name
   */
  protected void setRequestCacheName(final String name) {
    result.cacheName = name;
  }
  /**
   * @param name content handler name
   */
  protected void setRequestContentHandler(final String name) {
    result.contentHandler = name;
  }

  /**
   * Set network stats tag. Converts string tag to integer one.
   * @param tag string tag
   */
  protected void setConvertedTrafficStatsTag(final String tag) {
    result.statsTag = Utils.getTrafficStatsTag(tag);
    if (config.isDebugRest()) {
      Log.d(TAG, "TrafficStats tag <" + tag + ">=" + Integer.toHexString(result.statsTag));
    }
  }
  
  /**
   * Setup binary content from the local file. Parameter name will be equal to {@link RequestDescription#BINARY_NAME_DEFAULT}.
   * @param data content URI
   * @param contentType content MIME-type
   */
  protected void addBinaryContent(final Uri data, final String contentType) {
    addBinaryContent(null, data, contentType);
  }

  /**
   * Setup binary content with the local file.
   * @param name parameter name
   * @param data content URI
   * @param contentType content MIME-type
   */
  protected void addBinaryContent(final String name, final Uri data, final String contentType) {
    String contentName = RequestDescription.BINARY_NAME_DEFAULT;
    if (ContentResolver.SCHEME_FILE.equals(data.getScheme())) {
      try {
        contentName = new File(new URI(data.toString())).getName();
      } catch (final URISyntaxException e) {
        Log.e(TAG, "Bad file URI: " + data, e);
      }
    }
    addBinaryContent(name, contentName, data, contentType);
  }

  /**
   * Setup binary content with the local file.
   * @param name parameter name
   * @param contentName content name
   * @param data content URI
   * @param contentType content MIME-type
   */
  protected void addBinaryContent(final String name, final String contentName, final Uri data, final String contentType) {
    final ContentUriBinaryData bdata = new ContentUriBinaryData();
    bdata.setName(name);
    bdata.setContentUri(data, contentName);
    bdata.setContentType(contentType);
    result.addBinaryData(bdata);
  }

  /**
   * Setup binary content with the bitmap.
   * @param name parameter name
   * @param bitmap bitmap object
   * @param fileName file name
   */
  protected void addBitmap(final String name, final Bitmap bitmap, final String fileName) {
    final BitmapBinaryData bdata = new BitmapBinaryData();
    bdata.setName(name);
    bdata.setContentName(fileName);
    bdata.setBitmap(bitmap);
    result.addBinaryData(bdata);
  }

  /**
   * Setup binary content with the file descriptor.
   * @param name parameter name
   * @param fd file descriptor
   * @param contentType content MIME-type
   * @param fileName file name
   */
  protected void addFileDescriptor(final String name, final AssetFileDescriptor fd, final String contentType, final String fileName) {
    final AssetFdBinaryData bdata = new AssetFdBinaryData();
    bdata.setFileDescriptor(fileName, fd);
    bdata.setName(name);
    bdata.setContentType(contentType);
    result.addBinaryData(bdata);
  }

  /**
   * @param name name for empty binary type
   */
  protected void addEmptyBinary(final String name) {
    final EmptyBinaryData bdata = new EmptyBinaryData();
    bdata.setName(name);
    result.addBinaryData(bdata);
  }

  /**
   * @param name parameter name
   * @param value parameter value
   * @return added parameter instance
   */
  protected ParameterValue addSimpleParameter(final String name, final long value) {
    return addSimpleParameter(name, String.valueOf(value));
  }
  /**
   * @param name parameter name
   * @param value parameter value
   * @return added parameter instance
   */
  protected ParameterValue addSimpleParameter(final String name, final int value) {
    return addSimpleParameter(name, String.valueOf(value));
  }
  /**
   * @param name parameter name
   * @param value parameter value
   * @return added parameter instance
   */
  protected ParameterValue addSimpleParameter(final String name, final boolean value) {
    return addSimpleParameter(name, value ? "1" : "0");
  }
  /**
   * @param name parameter name
   * @param value parameter value
   * @return added parameter instance
   */
  protected ParameterValue addSimpleParameter(final String name, final String value) {
    return result.simpleParameters.addSimpleParameter(name, value);
  }

  /**
   * @param p parameter to add to the request description
   */
  protected void addParameter(final Parameter p) {
    result.simpleParameters.addParameter(p);
  }

  /**
   * Remove parameter with the specified name from request description.
   * @param name parameter name
   * @return removed parameter instance, null if no parameter was found
   */
  protected Parameter removeParameter(final String name) {
    if (name == null) { throw new IllegalArgumentException("Parameter name cannot be null"); }
    final Iterator<Parameter> iter = result.simpleParameters.getChildren().iterator();
    while (iter.hasNext()) {
      final Parameter p = iter.next();
      if (name.equals(p.name)) {
        iter.remove();
        return p;
      }
    }
    return null;
  }

  /**
   * Add header to request description.
   * @param name header name
   * @param value header value
   * @return this for chaining
   */
  protected BaseRequestBuilder<MT> addHeader(final String name, final String value) {
    result.addHeader(name, value);
    return this;
  }

  /**
   * Remove header.
   * @param name header name
   */
  protected void removeHeader(final String name) {
    result.removeHeader(name);
  }

  /**
   * @param contentAnalyzer bean name of {@link com.stanfy.enroscar.rest.response.ContentAnalyzer} instance
   */
  protected void defineContentAnalyzer(final String contentAnalyzer) {
    result.setContentAnalyzer(contentAnalyzer);
  }

  /** @return request description object */
  protected RequestDescription getResult() { return result; }

  /** @return the context */
  @Override
  public Context getContext() { return context; }

  /**
   * Clear the builder.
   */
  public void clear() {
    final RequestDescription result = this.result;
    result.simpleParameters.children.clear();
    result.clearBinaryData();
    result.contentType = null;
    result.clearHeaders();
  }

  public BaseRequestBuilder<?> setParallel(final boolean value) {
    result.parallelMode = value;
    return this;
  }

  public BaseRequestBuilder<?> setTaskQueueName(final String taskQueue) {
    result.parallelMode = false;
    result.taskQueueName = taskQueue;
    return this;
  }

  @Override
  public EntityTypeToken getExpectedModelType() { return expectedModelType; }

  @Override
  public int execute() {
    if (result.url == null) {
      throw new IllegalStateException("URL is not specified!");
    }
    if (result.modelType == null) {
      throw new IllegalStateException("Model is not specified!");
    }

    if (result.contentHandler == null) {
      result.contentHandler = config.getDefaultContentHandlerName();
    }
    if (result.contentHandler == null) {
      throw new IllegalStateException("Content handler is not specified");
    }

    if (result.cacheName == null) {
      result.cacheName = config.getDefaultCacheBeanName();
    }

    result.setCanceled(false);

    if (executor != null) {
      return executor.performRequest(result);
    } else {
      Log.w(TAG, "Don't know how to perform operation " + result.getUrl());
      return INVALID_REQUEST_IDENTIFIER;
    }
  }

  /**
   * Create an appropriate loader instance.<br/>
   * Basic usage:<br/>
   * <pre>
   * public Loader onCreateLoader(int id, Bundle args) {
   *   return new RequestBuilder(this)
   *     .addParam("aaa", "bbb")
   *     .getLoader();
   * }
   * </pre>
   * @return loader instance that uses this request builder
   */
  @Override
  public Loader<ResponseData<MT>> getLoader() {
    return new RequestBuilderLoader<MT>(this);
  }

  /**
   * @param <T> list element type
   * @param <LT> list type
   * @return list request builder wrapper instance
   */
  protected <T, LT extends List<T>> ListRequestBuilderWrapper<LT, T> createLoadMoreListWrapper() {
    return new ListRequestBuilderWrapper<LT, T>(this) { };
  }

  public <T, LT extends List<T>> ListRequestBuilderWrapper<LT, T> asLoadMoreList(final String offset, final String limit) {
    final ListRequestBuilderWrapper<LT, T> wrapper = createLoadMoreListWrapper();
    if (offset != null) {
      wrapper.setOffsetParamName(offset);
    }
    if (limit != null) {
      wrapper.setLimitParamName(limit);
    }
    return wrapper;
  }
  public <T, LT extends List<T>> ListRequestBuilderWrapper<LT, T> asLoadMoreList() {
    return asLoadMoreList(ListRequestBuilderWrapper.PARAM_OFFSET, ListRequestBuilderWrapper.PARAM_LIMIT);
  }

}
