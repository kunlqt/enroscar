package com.stanfy.views.list;

import android.annotation.SuppressLint;
import android.database.DataSetObserver;
import android.support.v4.widget.StaggeredGridView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.WrapperListAdapter;

import com.stanfy.views.R;
import com.stanfy.views.gallery.AdapterView;

/**
 * Adapter that shows load more footer.
 */
@SuppressLint("FieldGetter")
public class LoadmoreAdapter extends BaseAdapter implements WrapperListAdapter, Filterable {

  /** Main adapter. */
  final FetchableListAdapter core;

  /** Footer that indicates the loading process. */
  private final View loadView;
  /** True if we need to display 'load more' footer. */
  private boolean loadFlag = false;
  /** Load tag. */
  private Object loadTag;

  /** Footer layout ID. */
  private int footerLayoutId = R.layout.footer_loading;

  /** Core adapter observer. */
  private final DataSetObserver coreObserver = new DataSetObserver() {
    @Override
    public void onChanged() {
      setLoadFlag(false, false);
      notifyDataSetChanged();
    }
    @Override
    public void onInvalidated() {
      notifyDataSetInvalidated();
    }
  };

  public LoadmoreAdapter(final LayoutInflater inflater, final FetchableListAdapter core) {
    this.core = core;
    this.loadView = createLoadView(inflater);
    core.registerDataSetObserver(coreObserver);
  }

  public void setFooterLayoutId(final int footerLayoutId) {
    this.footerLayoutId = footerLayoutId;
  }

  public void setLoadTag(final Object loadTag) {
    this.loadTag = loadTag;
  }

  public void setLoadFlag(final boolean loadFlag) {
    setLoadFlag(loadFlag, true);
  }
  public void setLoadFlag(final boolean loadFlag, final boolean notify) {
    final boolean prev = this.loadFlag;
    this.loadFlag = loadFlag;
    if (notify && prev != loadFlag) {
      notifyDataSetChanged();
    }
  }

  protected View createLoadView(final LayoutInflater inflater) {
    final View result = inflater.inflate(footerLayoutId, null);
    final ListView.LayoutParams params = new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, ListView.LayoutParams.WRAP_CONTENT);
    result.setLayoutParams(params);
    return result;
  }

  protected boolean isLoadFooterPosition(final int position) {
    return loadFlag && position == core.getCount();
  }

  protected int transformPositionForCore(final int position) { return position; }

  @Override
  public boolean areAllItemsEnabled() { return false; }

  @Override
  public boolean isEnabled(final int position) {
    if (isLoadFooterPosition(position)) { return true; }
    final boolean allEnabled = core.areAllItemsEnabled();
    if (allEnabled) { return true; }
    return core.isEnabled(transformPositionForCore(position));
  }

  @Override
  public int getCount() {
    int count = core.getCount();
    if (loadFlag) { ++count; }
    return count;
  }
  @Override
  public boolean isEmpty() {
    return !loadFlag && core.isEmpty();
  }

  @Override
  public Object getItem(final int position) {
    return isLoadFooterPosition(position)
        ? loadTag
            : core.getItem(transformPositionForCore(position));
  }

  @Override
  public long getItemId(final int position) {
    return isLoadFooterPosition(position)
        ? -1
            : core.getItemId(transformPositionForCore(position));
  }

  @Override
  public boolean hasStableIds() { return core.hasStableIds(); }

  @Override
  public int getViewTypeCount() { return core.getViewTypeCount(); }

  @Override
  public int getItemViewType(final int position) {
    return isLoadFooterPosition(position)
        ? AdapterView.ITEM_VIEW_TYPE_IGNORE
            : core.getItemViewType(transformPositionForCore(position));
  }

  @Override
  public View getView(final int position, final View convertView, final ViewGroup parent) {
    return isLoadFooterPosition(position)
        ? configureLoadView(loadView, parent)
            : core.getView(position, convertView, parent);
  }

  protected View configureLoadView(final View loadView, final ViewGroup parent) {
    if (parent instanceof StaggeredGridView && loadView != null && !(loadView.getLayoutParams() instanceof StaggeredGridView.LayoutParams)) {
      final StaggeredGridView.LayoutParams lp = new StaggeredGridView.LayoutParams(loadView.getLayoutParams());
      lp.span = StaggeredGridView.LayoutParams.SPAN_MAX;
      loadView.setLayoutParams(lp);
    }
    return loadView;
  }

  @Override
  public Filter getFilter() {
    if (core instanceof Filterable) {
      return ((Filterable) core).getFilter();
    }
    return null;
  }

  @Override
  public FetchableListAdapter getWrappedAdapter() { return core; }

}
