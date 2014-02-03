package com.stanfy.enroscar.views;

import android.content.Context;
import android.support.v4.widget.StaggeredGridView;
import android.support.v4.widget.StaggeredGridView.LayoutParams;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.stanfy.enroscar.content.ResponseData;

import com.stanfy.enroscar.ui.R;

/**
 * Helper class for handling states.
 * @author Roman Mazur (Stanfy - http://stanfy.com)
 */
public class StateHelper {

  /** Element view states. */
  public static final int STATE_NORMAL = 0, STATE_EMPTY = 1, STATE_MESSAGE = 2, STATE_LOADING = 3;

  /** Default loading. */
  private static final StateViewCreator[] DEFAULT_STATES = new StateViewCreator[STATE_LOADING + 1];
  static {
    DEFAULT_STATES[STATE_LOADING] = new DefaultLoadingStateViewCreator();
    final DefaultMessageStateViewCreator messageCreator = new DefaultMessageStateViewCreator();
    DEFAULT_STATES[STATE_EMPTY] = messageCreator;
    DEFAULT_STATES[STATE_MESSAGE] = messageCreator;
  }

  public static void setDefaultStateViewCreator(final int state, final StateViewCreator creator) {
    DEFAULT_STATES[state] = creator;
  }

  /** Special views. */
  private StateViewCreator[] viewCreators;

  protected StateViewCreator[] constructCreatorsArray() {
    final StateViewCreator[] result = new StateViewCreator[DEFAULT_STATES.length];
    for (int i = 0; i < result.length; i++) {
      final StateViewCreator prototype = DEFAULT_STATES[i];
      if (prototype != null) {
        result[i] = prototype.copy();
      }
    }
    return result;
  }

  public void setStateViewCreator(final int state, final StateViewCreator creator) {
    final StateViewCreator[] viewCreators = getViewCreators();
    if (state != STATE_NORMAL && state > 0 && state < viewCreators.length) {
      viewCreators[state] = creator;
    }
  }

  private StateViewCreator[] getViewCreators() {
    if (viewCreators == null) {
      viewCreators = constructCreatorsArray();
    }

    return viewCreators;
  }

  public StateViewCreator getStateViewCreator(final int state) {
    final StateViewCreator[] viewCreators = getViewCreators();
    return state > 0 && state < viewCreators.length ? viewCreators[state] : null;
  }

  public View getCustomStateView(final int state, final Context context, final Object lastDataObject, final ViewGroup parent) {
    final StateViewCreator creator = getStateViewCreator(state);
    if (creator == null) { return null; }
    return creator.getView(context, lastDataObject, parent);
  }

  public boolean hasState(final int state) { return getStateViewCreator(state) != null; }

  /**
   * State view creator.
   */
  public abstract static class StateViewCreator implements Cloneable {

    /** View instance. */
    private View view;

    /** True if state view wants to match parent width/height. */
    private boolean matchParentWidth, matchParentHeight;

    protected abstract View createView(final Context context, final ViewGroup parent);
    protected abstract void bindView(final Context context, final View view, final Object lastResponseData, final ViewGroup parent);

    View getView(final Context context, final Object lastResponseData, final ViewGroup parent) {
      if (view == null) {
        view = createView(context, parent);
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
          matchParentWidth = lp.width == LayoutParams.MATCH_PARENT;
          matchParentHeight = lp.height == LayoutParams.MATCH_PARENT;
        }
      }
      configureStateViewWidth(parent);
      configureStateViewHeight(parent);
      bindView(context, view, lastResponseData, parent);
      return view;
    }

    public StateViewCreator copy() {
      try {
        return (StateViewCreator)clone();
      } catch (final CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }

    protected void configureStateViewWidth(final ViewGroup parent) {
      final View stateView = view;
      if (stateView == null) { return; }

      final ViewGroup.LayoutParams lp = stateView.getLayoutParams();
      if (lp == null) { return; }

      final boolean widthSet = lp.width != LayoutParams.MATCH_PARENT;
      /*
       * Do not change layout params if the state view
       * has custom width and did not want initially to match parent.
       */
      if (widthSet && !matchParentWidth) { return; }


      if (parent instanceof StaggeredGridView) {
        // The only way to stretch a child horizontally in SGV
        final StaggeredGridView.LayoutParams params = (LayoutParams) lp;
        params.span = StaggeredGridView.LayoutParams.SPAN_MAX;
      } else {
        int w = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (w <= 0) { w = ViewGroup.LayoutParams.MATCH_PARENT; }
        lp.width = w;
      }

      stateView.setLayoutParams(lp);
    }

    protected void configureStateViewHeight(final ViewGroup parent) {
      final View stateView = view;
      if (stateView == null) { return; }

      final ViewGroup.LayoutParams lp = stateView.getLayoutParams();
      if (lp == null) { return; }

      final boolean heightSet = lp.height != LayoutParams.MATCH_PARENT;
      /*
       * Do not change layout params if the state view
       * has custom width and did not want initially to match parent.
       */
      if (heightSet && !matchParentHeight) { return; }

      int h = 0;
      if (parent instanceof ListView) {
        final ListView listView = (ListView) parent;

        // check for one child only that wants to be as tall as we are
        final int childCount = listView.getChildCount();

        final int headersCount = listView.getHeaderViewsCount();

        final int dHeight = listView.getDividerHeight();
        h = listView.getHeight() - listView.getPaddingTop() - listView.getPaddingBottom();

        for (int i = 0; i < headersCount; i++) {
          final View header = listView.getChildAt(i);
          if (header != null) {
            h -= header.getHeight() + dHeight;
          }
        }
        final int footersCount = listView.getFooterViewsCount();
        for (int i = 0; i < footersCount; i++) {
          final View footer = listView.getChildAt(childCount - footersCount - 1);
          if (footer != null) {
            h -= footer.getHeight() + dHeight;
          }
        }
      } else {
        h = parent.getHeight() - parent.getPaddingTop() - parent.getPaddingBottom();
      }

      if (h <= 0) { h = ViewGroup.LayoutParams.MATCH_PARENT; }

      lp.height = h;
      stateView.setLayoutParams(lp);
    }

  }

  /** Default loading state. */
  public static class DefaultLoadingStateViewCreator extends StateViewCreator {
    @Override
    protected View createView(final Context context, final ViewGroup parent) {
      return LayoutInflater.from(context).inflate(R.layout.progress_panel, parent, false);
    }
    @Override
    protected void bindView(final Context context, final View view, final Object lastResponseData, final ViewGroup parent) {
      // nothing
    }
  }

  /** Default message state. */
  public static class DefaultMessageStateViewCreator extends StateViewCreator {
    @Override
    protected View createView(final Context context, final ViewGroup parent) {
      return LayoutInflater.from(context).inflate(R.layout.message_panel, parent, false);
    }
    @Override
    protected void bindView(final Context context, final View view, final Object lastResponseData, final ViewGroup parent) {
      if (lastResponseData instanceof ResponseData) {
        ((TextView)view).setText(((ResponseData<?>) lastResponseData).getMessage());
      }
    }
  }

}
