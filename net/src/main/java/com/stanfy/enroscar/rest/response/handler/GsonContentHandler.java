package com.stanfy.enroscar.rest.response.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stanfy.enroscar.beans.BeansContainer;
import com.stanfy.enroscar.beans.EnroscarBean;
import com.stanfy.enroscar.rest.EntityTypeToken;

/**
 * Implementation of {@link java.net.ContentHandler} that uses
 * <a href="http://code.google.com/p/google-gson/">Gson library</a>.
 * @author Roman Mazur (Stanfy - http://stanfy.com)
 */
@EnroscarBean(value = GsonContentHandler.BEAN_NAME, contextDependent = true)
public class GsonContentHandler extends BaseContentHandler {

  /** Response handler. */
  public static final String BEAN_NAME = "GsonResponseHandler";

  /** Gson instance. */
  private Gson gson;

  public GsonContentHandler(final Context context) {
    super(context);
  }
  
  /**
   * @return Gson instance for parsing JSON
   */
  protected Gson createGson() {
    return new GsonBuilder().setDateFormat(DEFAULT_DATE_FORMAT).create();
  }

  @Override
  protected Object getContent(final URLConnection connection, final InputStream source, final EntityTypeToken modelType) throws IOException {
    if (gson == null) {
      throw new IllegalStateException("Gson object is not created");
    }
    return gson.fromJson(new InputStreamReader(source, getCharset()), getModelType(modelType));
  }

  @Override
  public void onInitializationFinished(final BeansContainer beansContainer) {
    super.onInitializationFinished(beansContainer);
    this.gson = createGson();
  }

}
