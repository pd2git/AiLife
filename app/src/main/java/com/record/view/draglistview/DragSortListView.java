package com.record.view.draglistview;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.AbsListView.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.ListAdapter;
import android.widget.ListView;
import com.alibaba.fastjson.asm.Opcodes;
import com.record.myLife.R;
import com.sun.mail.imap.IMAPStore;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import static android.graphics.Canvas.ALL_SAVE_FLAG;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.UNSPECIFIED;

public class DragSortListView extends ListView {
    private static final int DRAGGING = 4;
    public static final int DRAG_NEG_X = 2;
    public static final int DRAG_NEG_Y = 8;
    public static final int DRAG_POS_X = 1;
    public static final int DRAG_POS_Y = 4;
    private static final int DROPPING = 2;
    private static final int IDLE = 0;
    private static final int NO_CANCEL = 0;
    private static final int ON_INTERCEPT_TOUCH_EVENT = 2;
    private static final int ON_TOUCH_EVENT = 1;
    private static final int REMOVING = 1;
    private static final int STOPPED = 3;
    private static final int sCacheSize = 3;
    private AdapterWrapper mAdapterWrapper;
    private boolean mAnimate = false;
    private boolean mBlockLayoutRequests = false;
    private MotionEvent mCancelEvent;
    private int mCancelMethod = 0;
    private HeightCache mChildHeightCache = new HeightCache(3);
    private float mCurrFloatAlpha = 1.0f;
    private int mDownScrollStartY;
    private float mDownScrollStartYF;
    private int mDragDeltaX;
    private int mDragDeltaY;
    private float mDragDownScrollHeight;
    private float mDragDownScrollStartFrac = 0.33333334f;
    private boolean mDragEnabled = true;
    private int mDragFlags = 0;
    private DragListener mDragListener;
    private DragScroller mDragScroller;
    private DragSortTracker mDragSortTracker;
    private int mDragStartY;
    private int mDragState = 0;
    private float mDragUpScrollHeight;
    private float mDragUpScrollStartFrac = 0.33333334f;
    private DropAnimator mDropAnimator;
    private DropListener mDropListener;
    private int mFirstExpPos;
    private float mFloatAlpha = 1.0f;
    private Point mFloatLoc = new Point();
    private int mFloatPos;
    private View mFloatView;
    private int mFloatViewHeight;
    private int mFloatViewHeightHalf;
    private boolean mFloatViewInvalidated = false;
    private FloatViewManager mFloatViewManager = null;
    private int mFloatViewMid;
    private boolean mFloatViewOnMeasured = false;
    private boolean mIgnoreTouchEvent = false;
    private boolean mInTouchEvent = false;
    private int mItemHeightCollapsed = 1;
    private boolean mLastCallWasIntercept = false;
    private int mLastX;
    private int mLastY;
    private LiftAnimator mLiftAnimator;
    private boolean mListViewIntercepted = false;
    private float mMaxScrollSpeed = 0.5f;
    private DataSetObserver mObserver;
    private int mOffsetX;
    private int mOffsetY;
    private RemoveAnimator mRemoveAnimator;
    private RemoveListener mRemoveListener;
    private float mRemoveVelocityX = 0.0f;
    private View[] mSampleViewTypes = new View[1];
    private DragScrollProfile mScrollProfile = new DragScrollProfile() {
        public float getSpeed(float w, long t) {
            return DragSortListView.this.mMaxScrollSpeed * w;
        }
    };
    private int mSecondExpPos;
    private float mSlideFrac = 0.0f;
    private float mSlideRegionFrac = 0.25f;
    private int mSrcPos;
    private Point mTouchLoc = new Point();
    private boolean mTrackDragSort = false;
    private int mUpScrollStartY;
    private float mUpScrollStartYF;
    private boolean mUseRemoveVelocity;
    private int mWidthMeasureSpec = 0;
    private int mX;
    private int mY;

    private class AdapterWrapper extends BaseAdapter {
        private ListAdapter mAdapter;

        public AdapterWrapper(ListAdapter adapter) {
            this.mAdapter = adapter;
//            this.mAdapter.registerDataSetObserver(new DataSetObserver(DragSortListView.this) {
            this.mAdapter.registerDataSetObserver(new DataSetObserver() {
                public void onChanged() {
                    AdapterWrapper.this.notifyDataSetChanged();
                }

                public void onInvalidated() {
                    AdapterWrapper.this.notifyDataSetInvalidated();
                }
            });
        }

        public ListAdapter getAdapter() {
            return this.mAdapter;
        }

        public long getItemId(int position) {
            return this.mAdapter.getItemId(position);
        }

        public Object getItem(int position) {
            return this.mAdapter.getItem(position);
        }

        public int getCount() {
            return this.mAdapter.getCount();
        }

        public boolean areAllItemsEnabled() {
            return this.mAdapter.areAllItemsEnabled();
        }

        public boolean isEnabled(int position) {
            return this.mAdapter.isEnabled(position);
        }

        public int getItemViewType(int position) {
            return this.mAdapter.getItemViewType(position);
        }

        public int getViewTypeCount() {
            return this.mAdapter.getViewTypeCount();
        }

        public boolean hasStableIds() {
            return this.mAdapter.hasStableIds();
        }

        public boolean isEmpty() {
            return this.mAdapter.isEmpty();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            DragSortItemView v;
            View child;
            if (convertView != null) {
                v = (DragSortItemView) convertView;
                View oldChild = v.getChildAt(0);
                child = this.mAdapter.getView(position, oldChild, DragSortListView.this);
                if (child != oldChild) {
                    if (oldChild != null) {
                        v.removeViewAt(0);
                    }
                    v.addView(child);
                }
            } else {
                child = this.mAdapter.getView(position, null, DragSortListView.this);
                if (child instanceof Checkable) {
                    v = new DragSortItemViewCheckable(DragSortListView.this.getContext());
                } else {
                    v = new DragSortItemView(DragSortListView.this.getContext());
                }
                v.setLayoutParams(new LayoutParams(-1, -2));
                v.addView(child);
            }
            DragSortListView.this.adjustItem(DragSortListView.this.getHeaderViewsCount() + position, v, true);
            return v;
        }
    }

    public interface DragListener {
        void drag(int i, int i2);
    }

    public interface DragScrollProfile {
        float getSpeed(float f, long j);
    }

    private class DragScroller implements Runnable {
        public static final int DOWN = 1;
        public static final int STOP = -1;
        public static final int UP = 0;
        private float dt;
        private int dy;
        private boolean mAbort;
        private long mCurrTime;
        private int mFirstFooter;
        private int mLastHeader;
        private long mPrevTime;
        private float mScrollSpeed;
        private boolean mScrolling = false;
        private int scrollDir;
        private long tStart;

        public boolean isScrolling() {
            return this.mScrolling;
        }

        public int getScrollDir() {
            return this.mScrolling ? this.scrollDir : -1;
        }

        public void startScrolling(int dir) {
            if (!this.mScrolling) {
                this.mAbort = false;
                this.mScrolling = true;
                this.tStart = SystemClock.uptimeMillis();
                this.mPrevTime = this.tStart;
                this.scrollDir = dir;
                DragSortListView.this.post(this);
            }
        }

        public void stopScrolling(boolean now) {
            if (now) {
                DragSortListView.this.removeCallbacks(this);
                this.mScrolling = false;
                return;
            }
            this.mAbort = true;
        }

        public void run() {
            if (this.mAbort) {
                this.mScrolling = false;
                return;
            }
            int movePos;
            int first = DragSortListView.this.getFirstVisiblePosition();
            int last = DragSortListView.this.getLastVisiblePosition();
            int count = DragSortListView.this.getCount();
            int padTop = DragSortListView.this.getPaddingTop();
            int listHeight = (DragSortListView.this.getHeight() - padTop) - DragSortListView.this.getPaddingBottom();
            int minY = Math.min(DragSortListView.this.mY, DragSortListView.this.mFloatViewMid + DragSortListView.this.mFloatViewHeightHalf);
            int maxY = Math.max(DragSortListView.this.mY, DragSortListView.this.mFloatViewMid - DragSortListView.this.mFloatViewHeightHalf);
            View v;
            if (this.scrollDir == 0) {
                v = DragSortListView.this.getChildAt(0);
                if (v == null) {
                    this.mScrolling = false;
                    return;
                } else if (first == 0 && v.getTop() == padTop) {
                    this.mScrolling = false;
                    return;
                } else {
                    this.mScrollSpeed = DragSortListView.this.mScrollProfile.getSpeed((DragSortListView.this.mUpScrollStartYF - ((float) maxY)) / DragSortListView.this.mDragUpScrollHeight, this.mPrevTime);
                }
            } else {
                v = DragSortListView.this.getChildAt(last - first);
                if (v == null) {
                    this.mScrolling = false;
                    return;
                } else if (last != count - 1 || v.getBottom() > listHeight + padTop) {
                    this.mScrollSpeed = -DragSortListView.this.mScrollProfile.getSpeed((((float) minY) - DragSortListView.this.mDownScrollStartYF) / DragSortListView.this.mDragDownScrollHeight, this.mPrevTime);
                } else {
                    this.mScrolling = false;
                    return;
                }
            }
            this.mCurrTime = SystemClock.uptimeMillis();
            this.dt = (float) (this.mCurrTime - this.mPrevTime);
            this.dy = Math.round(this.mScrollSpeed * this.dt);
            if (this.dy >= 0) {
                this.dy = Math.min(listHeight, this.dy);
                movePos = first;
            } else {
                this.dy = Math.max(-listHeight, this.dy);
                movePos = last;
            }
            View moveItem = DragSortListView.this.getChildAt(movePos - first);
            int top = moveItem.getTop() + this.dy;
            if (movePos == 0 && top > padTop) {
                top = padTop;
            }
            DragSortListView.this.mBlockLayoutRequests = true;
            DragSortListView.this.setSelectionFromTop(movePos, top - padTop);
            DragSortListView.this.layoutChildren();
            DragSortListView.this.invalidate();
            DragSortListView.this.mBlockLayoutRequests = false;
            DragSortListView.this.doDragFloatView(movePos, moveItem, false);
            this.mPrevTime = this.mCurrTime;
            DragSortListView.this.post(this);
        }
    }

    private class DragSortTracker {
        StringBuilder mBuilder = new StringBuilder();
        File mFile = new File(Environment.getExternalStorageDirectory(), "dslv_state.txt");
        private int mNumFlushes = 0;
        private int mNumInBuffer = 0;
        private boolean mTracking = false;

        public DragSortTracker() {
            if (!this.mFile.exists()) {
                try {
                    this.mFile.createNewFile();
                    Log.d("mobeta", "file created");
                } catch (IOException e) {
                    Log.w("mobeta", "Could not create dslv_state.txt");
                    Log.d("mobeta", e.getMessage());
                }
            }
        }

        public void startTracking() {
            this.mBuilder.append("<DSLVStates>\n");
            this.mNumFlushes = 0;
            this.mTracking = true;
        }

        public void appendState() {
            if (this.mTracking) {
                int i;
                this.mBuilder.append("<DSLVState>\n");
                int children = DragSortListView.this.getChildCount();
                int first = DragSortListView.this.getFirstVisiblePosition();
                this.mBuilder.append("    <Positions>");
                for (i = 0; i < children; i++) {
                    this.mBuilder.append(first + i).append(",");
                }
                this.mBuilder.append("</Positions>\n");
                this.mBuilder.append("    <Tops>");
                for (i = 0; i < children; i++) {
                    this.mBuilder.append(DragSortListView.this.getChildAt(i).getTop()).append(",");
                }
                this.mBuilder.append("</Tops>\n");
                this.mBuilder.append("    <Bottoms>");
                for (i = 0; i < children; i++) {
                    this.mBuilder.append(DragSortListView.this.getChildAt(i).getBottom()).append(",");
                }
                this.mBuilder.append("</Bottoms>\n");
                this.mBuilder.append("    <FirstExpPos>").append(DragSortListView.this.mFirstExpPos).append("</FirstExpPos>\n");
                this.mBuilder.append("    <FirstExpBlankHeight>").append(DragSortListView.this.getItemHeight(DragSortListView.this.mFirstExpPos) - DragSortListView.this.getChildHeight(DragSortListView.this.mFirstExpPos)).append("</FirstExpBlankHeight>\n");
                this.mBuilder.append("    <SecondExpPos>").append(DragSortListView.this.mSecondExpPos).append("</SecondExpPos>\n");
                this.mBuilder.append("    <SecondExpBlankHeight>").append(DragSortListView.this.getItemHeight(DragSortListView.this.mSecondExpPos) - DragSortListView.this.getChildHeight(DragSortListView.this.mSecondExpPos)).append("</SecondExpBlankHeight>\n");
                this.mBuilder.append("    <SrcPos>").append(DragSortListView.this.mSrcPos).append("</SrcPos>\n");
                this.mBuilder.append("    <SrcHeight>").append(DragSortListView.this.mFloatViewHeight + DragSortListView.this.getDividerHeight()).append("</SrcHeight>\n");
                this.mBuilder.append("    <ViewHeight>").append(DragSortListView.this.getHeight()).append("</ViewHeight>\n");
                this.mBuilder.append("    <LastY>").append(DragSortListView.this.mLastY).append("</LastY>\n");
                this.mBuilder.append("    <FloatY>").append(DragSortListView.this.mFloatViewMid).append("</FloatY>\n");
                this.mBuilder.append("    <ShuffleEdges>");
                for (i = 0; i < children; i++) {
                    this.mBuilder.append(DragSortListView.this.getShuffleEdge(first + i, DragSortListView.this.getChildAt(i).getTop())).append(",");
                }
                this.mBuilder.append("</ShuffleEdges>\n");
                this.mBuilder.append("</DSLVState>\n");
                this.mNumInBuffer++;
                if (this.mNumInBuffer > IMAPStore.RESPONSE) {
                    flush();
                    this.mNumInBuffer = 0;
                }
            }
        }

        public void flush() {
            if (this.mTracking) {
                boolean append = true;
                try {
                    if (this.mNumFlushes == 0) {
                        append = false;
                    }
                    FileWriter writer = new FileWriter(this.mFile, append);
                    writer.write(this.mBuilder.toString());
                    this.mBuilder.delete(0, this.mBuilder.length());
                    writer.flush();
                    writer.close();
                    this.mNumFlushes++;
                } catch (IOException e) {
                }
            }
        }

        public void stopTracking() {
            if (this.mTracking) {
                this.mBuilder.append("</DSLVStates>\n");
                flush();
                this.mTracking = false;
            }
        }
    }

    public interface DropListener {
        void drop(int i, int i2);
    }

    public interface FloatViewManager {
        View onCreateFloatView(int i);

        void onDestroyFloatView(View view);

        void onDragFloatView(View view, Point point, Point point2);
    }

    private class HeightCache {
        private SparseIntArray mMap;
        private int mMaxSize;
        private ArrayList<Integer> mOrder;

        public HeightCache(int size) {
            this.mMap = new SparseIntArray(size);
            this.mOrder = new ArrayList(size);
            this.mMaxSize = size;
        }

        public void add(int position, int height) {
            int currHeight = this.mMap.get(position, -1);
            if (currHeight != height) {
                if (currHeight != -1) {
                    this.mOrder.remove(Integer.valueOf(position));
                } else if (this.mMap.size() == this.mMaxSize) {
                    this.mMap.delete(((Integer) this.mOrder.remove(0)).intValue());
                }
                this.mMap.put(position, height);
                this.mOrder.add(Integer.valueOf(position));
            }
        }

        public int get(int position) {
            return this.mMap.get(position, -1);
        }

        public void clear() {
            this.mMap.clear();
            this.mOrder.clear();
        }
    }

    public interface RemoveListener {
        void remove(int i);
    }

    private class SmoothAnimator implements Runnable {
        private float mA;
        private float mAlpha;
        private float mB = (this.mAlpha / ((this.mAlpha - 1.0f) * 2.0f));
        private float mC = (1.0f / (1.0f - this.mAlpha));
        private boolean mCanceled;
        private float mD;
        private float mDurationF;
        protected long mStartTime;

        public SmoothAnimator(float smoothness, int duration) {
            this.mAlpha = smoothness;
            this.mDurationF = (float) duration;
            float f = 1.0f / ((this.mAlpha * 2.0f) * (1.0f - this.mAlpha));
            this.mD = f;
            this.mA = f;
        }

        public float transform(float frac) {
            if (frac < this.mAlpha) {
                return (this.mA * frac) * frac;
            }
            if (frac < 1.0f - this.mAlpha) {
                return this.mB + (this.mC * frac);
            }
            return 1.0f - ((this.mD * (frac - 1.0f)) * (frac - 1.0f));
        }

        public void start() {
            this.mStartTime = SystemClock.uptimeMillis();
            this.mCanceled = false;
            onStart();
            DragSortListView.this.post(this);
        }

        public void cancel() {
            this.mCanceled = true;
        }

        public void onStart() {
        }

        public void onUpdate(float frac, float smoothFrac) {
        }

        public void onStop() {
        }

        public void run() {
            if (!this.mCanceled) {
                float fraction = ((float) (SystemClock.uptimeMillis() - this.mStartTime)) / this.mDurationF;
                if (fraction >= 1.0f) {
                    onUpdate(1.0f, 1.0f);
                    onStop();
                    return;
                }
                onUpdate(fraction, transform(fraction));
                DragSortListView.this.post(this);
            }
        }
    }

    public interface DragSortListener extends DropListener, DragListener, RemoveListener {
    }

    private class DropAnimator extends SmoothAnimator {
        private int mDropPos;
        private float mInitDeltaX;
        private float mInitDeltaY;
        private int srcPos;

        public DropAnimator(float smoothness, int duration) {
            super(smoothness, duration);
        }

        public void onStart() {
            this.mDropPos = DragSortListView.this.mFloatPos;
            this.srcPos = DragSortListView.this.mSrcPos;
            DragSortListView.this.mDragState = 2;
            this.mInitDeltaY = (float) (DragSortListView.this.mFloatLoc.y - getTargetY());
            this.mInitDeltaX = (float) (DragSortListView.this.mFloatLoc.x - DragSortListView.this.getPaddingLeft());
        }

        private int getTargetY() {
            int otherAdjust = (DragSortListView.this.mItemHeightCollapsed + DragSortListView.this.getDividerHeight()) / 2;
            View v = DragSortListView.this.getChildAt(this.mDropPos - DragSortListView.this.getFirstVisiblePosition());
            if (v == null) {
                cancel();
                return -1;
            } else if (this.mDropPos == this.srcPos) {
                return v.getTop();
            } else {
                if (this.mDropPos < this.srcPos) {
                    return v.getTop() - otherAdjust;
                }
                return (v.getBottom() + otherAdjust) - DragSortListView.this.mFloatViewHeight;
            }
        }

        public void onUpdate(float frac, float smoothFrac) {
            int targetY = getTargetY();
            float deltaX = (float) (DragSortListView.this.mFloatLoc.x - DragSortListView.this.getPaddingLeft());
            float f = 1.0f - smoothFrac;
            if (f < Math.abs(((float) (DragSortListView.this.mFloatLoc.y - targetY)) / this.mInitDeltaY) || f < Math.abs(deltaX / this.mInitDeltaX)) {
                DragSortListView.this.mFloatLoc.y = ((int) (this.mInitDeltaY * f)) + targetY;
                DragSortListView.this.mFloatLoc.x = DragSortListView.this.getPaddingLeft() + ((int) (this.mInitDeltaX * f));
                DragSortListView.this.doDragFloatView(true);
            }
        }

        public void onStop() {
            DragSortListView.this.dropFloatView();
        }
    }

    private class LiftAnimator extends SmoothAnimator {
        private float mFinalDragDeltaY;
        private float mInitDragDeltaY;

        public LiftAnimator(float smoothness, int duration) {
            super(smoothness, duration);
        }

        public void onStart() {
            this.mInitDragDeltaY = (float) DragSortListView.this.mDragDeltaY;
            this.mFinalDragDeltaY = (float) DragSortListView.this.mFloatViewHeightHalf;
        }

        public void onUpdate(float frac, float smoothFrac) {
            if (DragSortListView.this.mDragState != 4) {
                cancel();
                return;
            }
            DragSortListView.this.mDragDeltaY = (int) ((this.mFinalDragDeltaY * smoothFrac) + ((1.0f - smoothFrac) * this.mInitDragDeltaY));
            DragSortListView.this.mFloatLoc.y = DragSortListView.this.mY - DragSortListView.this.mDragDeltaY;
            DragSortListView.this.doDragFloatView(true);
        }
    }

    private class RemoveAnimator extends SmoothAnimator {
        private int mFirstChildHeight = -1;
        private int mFirstPos;
        private float mFirstStartBlank;
        private float mFloatLocX;
        private int mSecondChildHeight = -1;
        private int mSecondPos;
        private float mSecondStartBlank;
        private int srcPos;

        public RemoveAnimator(float smoothness, int duration) {
            super(smoothness, duration);
        }

        public void onStart() {
            int i = -1;
            this.mFirstChildHeight = -1;
            this.mSecondChildHeight = -1;
            this.mFirstPos = DragSortListView.this.mFirstExpPos;
            this.mSecondPos = DragSortListView.this.mSecondExpPos;
            this.srcPos = DragSortListView.this.mSrcPos;
            DragSortListView.this.mDragState = 1;
            this.mFloatLocX = (float) DragSortListView.this.mFloatLoc.x;
            if (DragSortListView.this.mUseRemoveVelocity) {
                float minVelocity = 2.0f * ((float) DragSortListView.this.getWidth());
                if (DragSortListView.this.mRemoveVelocityX == 0.0f) {
                    DragSortListView dragSortListView = DragSortListView.this;
                    if (this.mFloatLocX >= 0.0f) {
                        i = 1;
                    }
                    dragSortListView.mRemoveVelocityX = ((float) i) * minVelocity;
                    return;
                }
                minVelocity *= 2.0f;
                if (DragSortListView.this.mRemoveVelocityX < 0.0f && DragSortListView.this.mRemoveVelocityX > (-minVelocity)) {
                    DragSortListView.this.mRemoveVelocityX = -minVelocity;
                    return;
                } else if (DragSortListView.this.mRemoveVelocityX > 0.0f && DragSortListView.this.mRemoveVelocityX < minVelocity) {
                    DragSortListView.this.mRemoveVelocityX = minVelocity;
                    return;
                } else {
                    return;
                }
            }
            DragSortListView.this.destroyFloatView();
        }

        public void onUpdate(float frac, float smoothFrac) {
            int blank;
            ViewGroup.LayoutParams lp;
            float f = 1.0f - smoothFrac;
            int firstVis = DragSortListView.this.getFirstVisiblePosition();
            View item = DragSortListView.this.getChildAt(this.mFirstPos - firstVis);
            if (DragSortListView.this.mUseRemoveVelocity) {
                float dt = ((float) (SystemClock.uptimeMillis() - this.mStartTime)) / 1000.0f;
                if (dt != 0.0f) {
                    float dx = DragSortListView.this.mRemoveVelocityX * dt;
                    int w = DragSortListView.this.getWidth();
                    DragSortListView.this.mRemoveVelocityX = ((((float) (DragSortListView.this.mRemoveVelocityX > 0.0f ? 1 : -1)) * dt) * ((float) w)) + DragSortListView.this.mRemoveVelocityX;
                    this.mFloatLocX += dx;
                    DragSortListView.this.mFloatLoc.x = (int) this.mFloatLocX;
                    if (this.mFloatLocX < ((float) w) && this.mFloatLocX > ((float) (-w))) {
                        this.mStartTime = SystemClock.uptimeMillis();
                        DragSortListView.this.doDragFloatView(true);
                        return;
                    }
                }
                return;
            }
            if (item != null) {
                if (this.mFirstChildHeight == -1) {
                    this.mFirstChildHeight = DragSortListView.this.getChildHeight(this.mFirstPos, item, false);
                    this.mFirstStartBlank = (float) (item.getHeight() - this.mFirstChildHeight);
                }
                blank = Math.max((int) (this.mFirstStartBlank * f), 1);
                lp = item.getLayoutParams();
                lp.height = this.mFirstChildHeight + blank;
                item.setLayoutParams(lp);
            }
            if (this.mSecondPos != this.mFirstPos) {
                item = DragSortListView.this.getChildAt(this.mSecondPos - firstVis);
                if (item != null) {
                    if (this.mSecondChildHeight == -1) {
                        this.mSecondChildHeight = DragSortListView.this.getChildHeight(this.mSecondPos, item, false);
                        this.mSecondStartBlank = (float) (item.getHeight() - this.mSecondChildHeight);
                    }
                    blank = Math.max((int) (this.mSecondStartBlank * f), 1);
                    lp = item.getLayoutParams();
                    lp.height = this.mSecondChildHeight + blank;
                    item.setLayoutParams(lp);
                }
            }
        }

        public void onStop() {
            DragSortListView.this.doRemoveItem();
        }
    }

    public DragSortListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        int removeAnimDuration = Opcodes.FCMPG;
        int dropAnimDuration = Opcodes.FCMPG;
        if (attrs != null) {
//            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.DragSortListView, 0, 0);
//            this.mItemHeightCollapsed = Math.max(1, a.getDimensionPixelSize(0, 1));
//            this.mTrackDragSort = a.getBoolean(5, false);
//            if (this.mTrackDragSort) {
//                this.mDragSortTracker = new DragSortTracker();
//            }
//            this.mFloatAlpha = a.getFloat(6, this.mFloatAlpha);
//            this.mCurrFloatAlpha = this.mFloatAlpha;
//            this.mDragEnabled = a.getBoolean(10, this.mDragEnabled);
//            this.mSlideRegionFrac = Math.max(0.0f, Math.min(1.0f, 1.0f - a.getFloat(7, 0.75f)));
//            this.mAnimate = this.mSlideRegionFrac > 0.0f;
//            setDragScrollStart(a.getFloat(1, this.mDragUpScrollStartFrac));
//            this.mMaxScrollSpeed = a.getFloat(2, this.mMaxScrollSpeed);
//            removeAnimDuration = a.getInt(8, removeAnimDuration);
//            dropAnimDuration = a.getInt(9, dropAnimDuration);
//            if (a.getBoolean(17, true)) {
//                boolean removeEnabled = a.getBoolean(12, false);
//                int removeMode = a.getInt(4, 1);
//                boolean sortEnabled = a.getBoolean(11, true);
//                int dragInitMode = a.getInt(13, 0);
//                int dragHandleId = a.getResourceId(14, 0);
//                int flingHandleId = a.getResourceId(15, 0);
//                int clickRemoveId = a.getResourceId(16, 0);
//                int bgColor = a.getColor(3, -16777216);
//                DragSortController controller = new DragSortController(this, dragHandleId, dragInitMode, removeMode, clickRemoveId, flingHandleId);
//                controller.setRemoveEnabled(removeEnabled);
//                controller.setSortEnabled(sortEnabled);
//                controller.setBackgroundColor(bgColor);
//                this.mFloatViewManager = controller;
//                setOnTouchListener(controller);
//            }
//            a.recycle();
        }
        this.mDragScroller = new DragScroller();
        if (removeAnimDuration > 0) {
            this.mRemoveAnimator = new RemoveAnimator(0.5f, removeAnimDuration);
        }
        if (dropAnimDuration > 0) {
            this.mDropAnimator = new DropAnimator(0.5f, dropAnimDuration);
        }
        this.mCancelEvent = MotionEvent.obtain(0, 0, 3, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0.0f, 0.0f, 0, 0);
        this.mObserver = new DataSetObserver() {
            private void cancel() {
                if (DragSortListView.this.mDragState == 4) {
                    DragSortListView.this.cancelDrag();
                }
            }

            public void onChanged() {
                cancel();
            }

            public void onInvalidated() {
                cancel();
            }
        };
    }

    public void setFloatAlpha(float alpha) {
        this.mCurrFloatAlpha = alpha;
    }

    public float getFloatAlpha() {
        return this.mCurrFloatAlpha;
    }

    public void setMaxScrollSpeed(float max) {
        this.mMaxScrollSpeed = max;
    }

    public void setAdapter(ListAdapter adapter) {
        if (adapter != null) {
            this.mAdapterWrapper = new AdapterWrapper(adapter);
            adapter.registerDataSetObserver(this.mObserver);
            if (adapter instanceof DropListener) {
                setDropListener((DropListener) adapter);
            }
            if (adapter instanceof DragListener) {
                setDragListener((DragListener) adapter);
            }
            if (adapter instanceof RemoveListener) {
                setRemoveListener((RemoveListener) adapter);
            }
        } else {
            this.mAdapterWrapper = null;
        }
        super.setAdapter(this.mAdapterWrapper);
    }

    public ListAdapter getInputAdapter() {
        if (this.mAdapterWrapper == null) {
            return null;
        }
        return this.mAdapterWrapper.getAdapter();
    }

    private void drawDivider(int expPosition, Canvas canvas) {
        Drawable divider = getDivider();
        int dividerHeight = getDividerHeight();
        if (divider != null && dividerHeight != 0) {
            ViewGroup expItem = (ViewGroup) getChildAt(expPosition - getFirstVisiblePosition());
            if (expItem != null) {
                int t;
                int b;
                int l = getPaddingLeft();
                int r = getWidth() - getPaddingRight();
                int childHeight = expItem.getChildAt(0).getHeight();
                if (expPosition > this.mSrcPos) {
                    t = expItem.getTop() + childHeight;
                    b = t + dividerHeight;
                } else {
                    b = expItem.getBottom() - childHeight;
                    t = b - dividerHeight;
                }
                canvas.save();
                canvas.clipRect(l, t, r, b);
                divider.setBounds(l, t, r, b);
                divider.draw(canvas);
                canvas.restore();
            }
        }
    }

    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (this.mDragState != 0) {
            if (this.mFirstExpPos != this.mSrcPos) {
                drawDivider(this.mFirstExpPos, canvas);
            }
            if (!(this.mSecondExpPos == this.mFirstExpPos || this.mSecondExpPos == this.mSrcPos)) {
                drawDivider(this.mSecondExpPos, canvas);
            }
        }
        if (this.mFloatView != null) {
            float alphaMod;
            int w = this.mFloatView.getWidth();
            int h = this.mFloatView.getHeight();
            int x = this.mFloatLoc.x;
            int width = getWidth();
            if (x < 0) {
                x = -x;
            }
            if (x < width) {
                alphaMod = ((float) (width - x)) / ((float) width);
                alphaMod *= alphaMod;
            } else {
                alphaMod = 0.0f;
            }
            int alpha = (int) ((255.0f * this.mCurrFloatAlpha) * alphaMod);
            canvas.save();
            canvas.translate((float) this.mFloatLoc.x, (float) this.mFloatLoc.y);
            canvas.clipRect(0, 0, w, h);
            canvas.saveLayerAlpha(0.0f, 0.0f, (float) w, (float) h, alpha, ALL_SAVE_FLAG);
            this.mFloatView.draw(canvas);
            canvas.restore();
            canvas.restore();
        }
    }

    private int getItemHeight(int position) {
        View v = getChildAt(position - getFirstVisiblePosition());
        if (v != null) {
            return v.getHeight();
        }
        return calcItemHeight(position, getChildHeight(position));
    }

    private void printPosData() {
        Log.d("mobeta", "mSrcPos=" + this.mSrcPos + " mFirstExpPos=" + this.mFirstExpPos + " mSecondExpPos=" + this.mSecondExpPos);
    }

    private int getShuffleEdge(int position, int top) {
        int numHeaders = getHeaderViewsCount();
        int numFooters = getFooterViewsCount();
        if (position <= numHeaders || position >= getCount() - numFooters) {
            return top;
        }
        int divHeight = getDividerHeight();
        int maxBlankHeight = this.mFloatViewHeight - this.mItemHeightCollapsed;
        int childHeight = getChildHeight(position);
        int itemHeight = getItemHeight(position);
        int otop = top;
        if (this.mSecondExpPos <= this.mSrcPos) {
            if (position == this.mSecondExpPos && this.mFirstExpPos != this.mSecondExpPos) {
                otop = position == this.mSrcPos ? (top + itemHeight) - this.mFloatViewHeight : (top + (itemHeight - childHeight)) - maxBlankHeight;
            } else if (position > this.mSecondExpPos && position <= this.mSrcPos) {
                otop = top - maxBlankHeight;
            }
        } else if (position > this.mSrcPos && position <= this.mFirstExpPos) {
            otop = top + maxBlankHeight;
        } else if (position == this.mSecondExpPos && this.mFirstExpPos != this.mSecondExpPos) {
            otop = top + (itemHeight - childHeight);
        }
        if (position <= this.mSrcPos) {
            return otop + (((this.mFloatViewHeight - divHeight) - getChildHeight(position - 1)) / 2);
        }
        return otop + (((childHeight - divHeight) - this.mFloatViewHeight) / 2);
    }

    private boolean updatePositions() {
        int first = getFirstVisiblePosition();
        int startPos = this.mFirstExpPos;
        View startView = getChildAt(startPos - first);
        if (startView == null) {
            startPos = first + (getChildCount() / 2);
            startView = getChildAt(startPos - first);
        }
        int startTop = startView.getTop();
        int itemHeight = startView.getHeight();
        int edge = getShuffleEdge(startPos, startTop);
        int lastEdge = edge;
        int divHeight = getDividerHeight();
        int itemPos = startPos;
        int itemTop = startTop;
        if (this.mFloatViewMid >= edge) {
            int count = getCount();
            while (itemPos < count) {
                if (itemPos != count - 1) {
                    itemTop += divHeight + itemHeight;
                    itemHeight = getItemHeight(itemPos + 1);
                    edge = getShuffleEdge(itemPos + 1, itemTop);
                    if (this.mFloatViewMid < edge) {
                        break;
                    }
                    lastEdge = edge;
                    itemPos++;
                } else {
                    edge = (itemTop + divHeight) + itemHeight;
                    break;
                }
            }
        }
        while (itemPos >= 0) {
            itemPos--;
            itemHeight = getItemHeight(itemPos);
            if (itemPos != 0) {
                itemTop -= itemHeight + divHeight;
                edge = getShuffleEdge(itemPos, itemTop);
                if (this.mFloatViewMid >= edge) {
                    break;
                }
                lastEdge = edge;
            } else {
                edge = (itemTop - divHeight) - itemHeight;
                break;
            }
        }
        int numHeaders = getHeaderViewsCount();
        int numFooters = getFooterViewsCount();
        boolean updated = false;
        int oldFirstExpPos = this.mFirstExpPos;
        int oldSecondExpPos = this.mSecondExpPos;
        float oldSlideFrac = this.mSlideFrac;
        if (this.mAnimate) {
            int edgeBottom;
            int edgeTop;
            int edgeToEdge = Math.abs(edge - lastEdge);
            if (this.mFloatViewMid < edge) {
                edgeBottom = edge;
                edgeTop = lastEdge;
            } else {
                edgeTop = edge;
                edgeBottom = lastEdge;
            }
            int slideRgnHeight = (int) ((0.5f * this.mSlideRegionFrac) * ((float) edgeToEdge));
            float slideRgnHeightF = (float) slideRgnHeight;
            int slideEdgeTop = edgeTop + slideRgnHeight;
            int slideEdgeBottom = edgeBottom - slideRgnHeight;
            if (this.mFloatViewMid < slideEdgeTop) {
                this.mFirstExpPos = itemPos - 1;
                this.mSecondExpPos = itemPos;
                this.mSlideFrac = (0.5f * ((float) (slideEdgeTop - this.mFloatViewMid))) / slideRgnHeightF;
            } else if (this.mFloatViewMid < slideEdgeBottom) {
                this.mFirstExpPos = itemPos;
                this.mSecondExpPos = itemPos;
            } else {
                this.mFirstExpPos = itemPos;
                this.mSecondExpPos = itemPos + 1;
                this.mSlideFrac = 0.5f * (1.0f + (((float) (edgeBottom - this.mFloatViewMid)) / slideRgnHeightF));
            }
        } else {
            this.mFirstExpPos = itemPos;
            this.mSecondExpPos = itemPos;
        }
        if (this.mFirstExpPos < numHeaders) {
            itemPos = numHeaders;
            this.mFirstExpPos = itemPos;
            this.mSecondExpPos = itemPos;
        } else {
            if (this.mSecondExpPos >= getCount() - numFooters) {
                itemPos = (getCount() - numFooters) - 1;
                this.mFirstExpPos = itemPos;
                this.mSecondExpPos = itemPos;
            }
        }
        if (!(this.mFirstExpPos == oldFirstExpPos && this.mSecondExpPos == oldSecondExpPos && this.mSlideFrac == oldSlideFrac)) {
            updated = true;
        }
        if (itemPos == this.mFloatPos) {
            return updated;
        }
        if (this.mDragListener != null) {
            this.mDragListener.drag(this.mFloatPos - numHeaders, itemPos - numHeaders);
        }
        this.mFloatPos = itemPos;
        return true;
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.mTrackDragSort) {
            this.mDragSortTracker.appendState();
        }
    }

    public void removeItem(int which) {
        this.mUseRemoveVelocity = false;
        removeItem(which, 0.0f);
    }

    public void removeItem(int which, float velocityX) {
        if (this.mDragState == 0 || this.mDragState == 4) {
            if (this.mDragState == 0) {
                this.mSrcPos = getHeaderViewsCount() + which;
                this.mFirstExpPos = this.mSrcPos;
                this.mSecondExpPos = this.mSrcPos;
                this.mFloatPos = this.mSrcPos;
                View v = getChildAt(this.mSrcPos - getFirstVisiblePosition());
                if (v != null) {
                    v.setVisibility(INVISIBLE);
                }
            }
            this.mDragState = 1;
            this.mRemoveVelocityX = velocityX;
            if (this.mInTouchEvent) {
                switch (this.mCancelMethod) {
                    case 1:
                        super.onTouchEvent(this.mCancelEvent);
                        break;
                    case 2:
                        super.onInterceptTouchEvent(this.mCancelEvent);
                        break;
                }
            }
            if (this.mRemoveAnimator != null) {
                this.mRemoveAnimator.start();
            } else {
                doRemoveItem(which);
            }
        }
    }

    public void moveItem(int from, int to) {
        if (this.mDropListener != null) {
            int count = getInputAdapter().getCount();
            if (from >= 0 && from < count && to >= 0 && to < count) {
                this.mDropListener.drop(from, to);
            }
        }
    }

    public void cancelDrag() {
        if (this.mDragState == 4) {
            this.mDragScroller.stopScrolling(true);
            destroyFloatView();
            clearPositions();
            adjustAllItems();
            if (this.mInTouchEvent) {
                this.mDragState = 3;
            } else {
                this.mDragState = 0;
            }
        }
    }

    private void clearPositions() {
        this.mSrcPos = -1;
        this.mFirstExpPos = -1;
        this.mSecondExpPos = -1;
        this.mFloatPos = -1;
    }

    private void dropFloatView() {
        this.mDragState = 2;
        if (this.mDropListener != null && this.mFloatPos >= 0 && this.mFloatPos < getCount()) {
            int numHeaders = getHeaderViewsCount();
            this.mDropListener.drop(this.mSrcPos - numHeaders, this.mFloatPos - numHeaders);
        }
        destroyFloatView();
        adjustOnReorder();
        clearPositions();
        adjustAllItems();
        if (this.mInTouchEvent) {
            this.mDragState = 3;
        } else {
            this.mDragState = 0;
        }
    }

    private void doRemoveItem() {
        doRemoveItem(this.mSrcPos - getHeaderViewsCount());
    }

    private void doRemoveItem(int which) {
        this.mDragState = 1;
        if (this.mRemoveListener != null) {
            this.mRemoveListener.remove(which);
        }
        destroyFloatView();
        adjustOnReorder();
        clearPositions();
        if (this.mInTouchEvent) {
            this.mDragState = 3;
        } else {
            this.mDragState = 0;
        }
    }

    private void adjustOnReorder() {
        int firstPos = getFirstVisiblePosition();
        if (this.mSrcPos < firstPos) {
            View v = getChildAt(0);
            int top = 0;
            if (v != null) {
                top = v.getTop();
            }
            setSelectionFromTop(firstPos - 1, top - getPaddingTop());
        }
    }

    public boolean stopDrag(boolean remove) {
        this.mUseRemoveVelocity = false;
        return stopDrag(remove, 0.0f);
    }

    public boolean stopDragWithVelocity(boolean remove, float velocityX) {
        this.mUseRemoveVelocity = true;
        return stopDrag(remove, velocityX);
    }

    public boolean stopDrag(boolean remove, float velocityX) {
        if (this.mFloatView == null) {
            return false;
        }
        this.mDragScroller.stopScrolling(true);
        if (remove) {
            removeItem(this.mSrcPos - getHeaderViewsCount(), velocityX);
        } else if (this.mDropAnimator != null) {
            this.mDropAnimator.start();
        } else {
            dropFloatView();
        }
        if (!this.mTrackDragSort) {
            return true;
        }
        this.mDragSortTracker.stopTracking();
        return true;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (this.mIgnoreTouchEvent) {
            this.mIgnoreTouchEvent = false;
            return false;
        } else if (!this.mDragEnabled) {
            return super.onTouchEvent(ev);
        } else {
            boolean more = false;
            boolean lastCallWasIntercept = this.mLastCallWasIntercept;
            this.mLastCallWasIntercept = false;
            if (!lastCallWasIntercept) {
                saveTouchCoords(ev);
            }
            if (this.mDragState == 4) {
                onDragTouchEvent(ev);
                return true;
            }
            if (this.mDragState == 0 && super.onTouchEvent(ev)) {
                more = true;
            }
            switch (ev.getAction() & 255) {
                case 1:
                case 3:
                    doActionUpOrCancel();
                    return more;
                default:
                    if (!more) {
                        return more;
                    }
                    this.mCancelMethod = 1;
                    return more;
            }
        }
    }

    private void doActionUpOrCancel() {
        this.mCancelMethod = 0;
        this.mInTouchEvent = false;
        if (this.mDragState == 3) {
            this.mDragState = 0;
        }
        this.mCurrFloatAlpha = this.mFloatAlpha;
        this.mListViewIntercepted = false;
        this.mChildHeightCache.clear();
    }

    private void saveTouchCoords(MotionEvent ev) {
        int action = ev.getAction() & 255;
        if (action != 0) {
            this.mLastX = this.mX;
            this.mLastY = this.mY;
        }
        this.mX = (int) ev.getX();
        this.mY = (int) ev.getY();
        if (action == 0) {
            this.mLastX = this.mX;
            this.mLastY = this.mY;
        }
        this.mOffsetX = ((int) ev.getRawX()) - this.mX;
        this.mOffsetY = ((int) ev.getRawY()) - this.mY;
    }

    public boolean listViewIntercepted() {
        return this.mListViewIntercepted;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!this.mDragEnabled) {
            return super.onInterceptTouchEvent(ev);
        }
        saveTouchCoords(ev);
        this.mLastCallWasIntercept = true;
        int action = ev.getAction() & 255;
        if (action == 0) {
            if (this.mDragState != 0) {
                this.mIgnoreTouchEvent = true;
                return true;
            }
            this.mInTouchEvent = true;
        }
        boolean intercept = false;
        if (this.mFloatView == null) {
            if (super.onInterceptTouchEvent(ev)) {
                this.mListViewIntercepted = true;
                intercept = true;
            }
            switch (action) {
                case 1:
                case 3:
                    doActionUpOrCancel();
                    break;
                default:
                    if (!intercept) {
                        this.mCancelMethod = 2;
                        break;
                    }
                    this.mCancelMethod = 1;
                    break;
            }
        }
        intercept = true;
        if (action != 1 && action != 3) {
            return intercept;
        }
        this.mInTouchEvent = false;
        return intercept;
    }

    public void setDragScrollStart(float heightFraction) {
        setDragScrollStarts(heightFraction, heightFraction);
    }

    public void setDragScrollStarts(float upperFrac, float lowerFrac) {
        if (lowerFrac > 0.5f) {
            this.mDragDownScrollStartFrac = 0.5f;
        } else {
            this.mDragDownScrollStartFrac = lowerFrac;
        }
        if (upperFrac > 0.5f) {
            this.mDragUpScrollStartFrac = 0.5f;
        } else {
            this.mDragUpScrollStartFrac = upperFrac;
        }
        if (getHeight() != 0) {
            updateScrollStarts();
        }
    }

    private void continueDrag(int x, int y) {
        this.mFloatLoc.x = x - this.mDragDeltaX;
        this.mFloatLoc.y = y - this.mDragDeltaY;
        doDragFloatView(true);
        int minY = Math.min(y, this.mFloatViewMid + this.mFloatViewHeightHalf);
        int maxY = Math.max(y, this.mFloatViewMid - this.mFloatViewHeightHalf);
        int currentScrollDir = this.mDragScroller.getScrollDir();
        if (minY > this.mLastY && minY > this.mDownScrollStartY && currentScrollDir != 1) {
            if (currentScrollDir != -1) {
                this.mDragScroller.stopScrolling(true);
            }
            this.mDragScroller.startScrolling(1);
        } else if (maxY < this.mLastY && maxY < this.mUpScrollStartY && currentScrollDir != 0) {
            if (currentScrollDir != -1) {
                this.mDragScroller.stopScrolling(true);
            }
            this.mDragScroller.startScrolling(0);
        } else if (maxY >= this.mUpScrollStartY && minY <= this.mDownScrollStartY && this.mDragScroller.isScrolling()) {
            this.mDragScroller.stopScrolling(true);
        }
    }

    private void updateScrollStarts() {
        int padTop = getPaddingTop();
        int listHeight = (getHeight() - padTop) - getPaddingBottom();
        float heightF = (float) listHeight;
        this.mUpScrollStartYF = ((float) padTop) + (this.mDragUpScrollStartFrac * heightF);
        this.mDownScrollStartYF = ((float) padTop) + ((1.0f - this.mDragDownScrollStartFrac) * heightF);
        this.mUpScrollStartY = (int) this.mUpScrollStartYF;
        this.mDownScrollStartY = (int) this.mDownScrollStartYF;
        this.mDragUpScrollHeight = this.mUpScrollStartYF - ((float) padTop);
        this.mDragDownScrollHeight = ((float) (padTop + listHeight)) - this.mDownScrollStartYF;
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateScrollStarts();
    }

    private void adjustAllItems() {
        int first = getFirstVisiblePosition();
        int last = getLastVisiblePosition();
        int begin = Math.max(0, getHeaderViewsCount() - first);
        int end = Math.min(last - first, ((getCount() - 1) - getFooterViewsCount()) - first);
        for (int i = begin; i <= end; i++) {
            View v = getChildAt(i);
            if (v != null) {
                adjustItem(first + i, v, false);
            }
        }
    }

    private void adjustItem(int position) {
        View v = getChildAt(position - getFirstVisiblePosition());
        if (v != null) {
            adjustItem(position, v, false);
        }
    }

    private void adjustItem(int position, View v, boolean invalidChildHeight) {
        int height;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (position == this.mSrcPos || position == this.mFirstExpPos || position == this.mSecondExpPos) {
            height = calcItemHeight(position, v, invalidChildHeight);
        } else {
            height = -2;
        }
        if (height != lp.height) {
            lp.height = height;
            v.setLayoutParams(lp);
        }
        if (position == this.mFirstExpPos || position == this.mSecondExpPos) {
            if (position < this.mSrcPos) {
                ((DragSortItemView) v).setGravity(80);
            } else if (position > this.mSrcPos) {
                ((DragSortItemView) v).setGravity(48);
            }
        }
        int oldVis = v.getVisibility();
        int vis = 0;
        if (position == this.mSrcPos && this.mFloatView != null) {
            vis = 4;
        }
        if (vis != oldVis) {
            v.setVisibility(vis);
        }
    }

    private int getChildHeight(int position) {
        if (position == this.mSrcPos) {
            return 0;
        }
        View v = getChildAt(position - getFirstVisiblePosition());
        if (v != null) {
            return getChildHeight(position, v, false);
        }
        int childHeight = this.mChildHeightCache.get(position);
        if (childHeight != -1) {
            return childHeight;
        }
        ListAdapter adapter = getAdapter();
        int type = adapter.getItemViewType(position);
        int typeCount = adapter.getViewTypeCount();
        if (typeCount != this.mSampleViewTypes.length) {
            this.mSampleViewTypes = new View[typeCount];
        }
        if (type < 0) {
            v = adapter.getView(position, null, this);
        } else if (this.mSampleViewTypes[type] == null) {
            v = adapter.getView(position, null, this);
            this.mSampleViewTypes[type] = v;
        } else {
            v = adapter.getView(position, this.mSampleViewTypes[type], this);
        }
        childHeight = getChildHeight(position, v, true);
        this.mChildHeightCache.add(position, childHeight);
        return childHeight;
    }

    private int getChildHeight(int position, View item, boolean invalidChildHeight) {
        if (position == this.mSrcPos) {
            return 0;
        }
        View child;
        if (position < getHeaderViewsCount() || position >= getCount() - getFooterViewsCount()) {
            child = item;
        } else {
            child = ((ViewGroup) item).getChildAt(0);
        }
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp != null && lp.height > 0) {
            return lp.height;
        }
        int childHeight = child.getHeight();
        if (childHeight != 0 && !invalidChildHeight) {
            return childHeight;
        }
        measureItem(child);
        return child.getMeasuredHeight();
    }

    private int calcItemHeight(int position, View item, boolean invalidChildHeight) {
        return calcItemHeight(position, getChildHeight(position, item, invalidChildHeight));
    }

    private int calcItemHeight(int position, int childHeight) {
        int divHeight = getDividerHeight();
        boolean isSliding = this.mAnimate && this.mFirstExpPos != this.mSecondExpPos;
        int maxNonSrcBlankHeight = this.mFloatViewHeight - this.mItemHeightCollapsed;
        int slideHeight = (int) (this.mSlideFrac * ((float) maxNonSrcBlankHeight));
        if (position == this.mSrcPos) {
            if (this.mSrcPos == this.mFirstExpPos) {
                if (isSliding) {
                    return slideHeight + this.mItemHeightCollapsed;
                }
                return this.mFloatViewHeight;
            } else if (this.mSrcPos == this.mSecondExpPos) {
                return this.mFloatViewHeight - slideHeight;
            } else {
                return this.mItemHeightCollapsed;
            }
        } else if (position == this.mFirstExpPos) {
            if (isSliding) {
                return childHeight + slideHeight;
            }
            return childHeight + maxNonSrcBlankHeight;
        } else if (position == this.mSecondExpPos) {
            return (childHeight + maxNonSrcBlankHeight) - slideHeight;
        } else {
            return childHeight;
        }
    }

    public void requestLayout() {
        if (!this.mBlockLayoutRequests) {
            super.requestLayout();
        }
    }

    private int adjustScroll(int movePos, View moveItem, int oldFirstExpPos, int oldSecondExpPos) {
        int childHeight = getChildHeight(movePos);
        int moveHeightBefore = moveItem.getHeight();
        int moveHeightAfter = calcItemHeight(movePos, childHeight);
        int moveBlankBefore = moveHeightBefore;
        int moveBlankAfter = moveHeightAfter;
        if (movePos != this.mSrcPos) {
            moveBlankBefore -= childHeight;
            moveBlankAfter -= childHeight;
        }
        int maxBlank = this.mFloatViewHeight;
        if (!(this.mSrcPos == this.mFirstExpPos || this.mSrcPos == this.mSecondExpPos)) {
            maxBlank -= this.mItemHeightCollapsed;
        }
        if (movePos <= oldFirstExpPos) {
            if (movePos > this.mFirstExpPos) {
                return 0 + (maxBlank - moveBlankAfter);
            }
            return 0;
        } else if (movePos == oldSecondExpPos) {
            if (movePos <= this.mFirstExpPos) {
                return 0 + (moveBlankBefore - maxBlank);
            }
            if (movePos == this.mSecondExpPos) {
                return 0 + (moveHeightBefore - moveHeightAfter);
            }
            return 0 + moveBlankBefore;
        } else if (movePos <= this.mFirstExpPos) {
            return 0 - maxBlank;
        } else {
            if (movePos == this.mSecondExpPos) {
                return 0 - moveBlankAfter;
            }
            return 0;
        }
    }

    private void measureItem(View item) {
        int hspec;
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (lp == null) {
            lp = new LayoutParams(-1, -2);
            item.setLayoutParams(lp);
        }
        int wspec = ViewGroup.getChildMeasureSpec(this.mWidthMeasureSpec, getListPaddingLeft() + getListPaddingRight(), lp.width);
        if (lp.height > 0) {
            hspec = MeasureSpec.makeMeasureSpec(lp.height, EXACTLY);
        } else {
            hspec = MeasureSpec.makeMeasureSpec(0, UNSPECIFIED);
        }
        item.measure(wspec, hspec);
    }

    private void measureFloatView() {
        if (this.mFloatView != null) {
            measureItem(this.mFloatView);
            this.mFloatViewHeight = this.mFloatView.getMeasuredHeight();
            this.mFloatViewHeightHalf = this.mFloatViewHeight / 2;
        }
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (this.mFloatView != null) {
            if (this.mFloatView.isLayoutRequested()) {
                measureFloatView();
            }
            this.mFloatViewOnMeasured = true;
        }
        this.mWidthMeasureSpec = widthMeasureSpec;
    }

    protected void layoutChildren() {
        super.layoutChildren();
        if (this.mFloatView != null) {
            if (this.mFloatView.isLayoutRequested() && !this.mFloatViewOnMeasured) {
                measureFloatView();
            }
            this.mFloatView.layout(0, 0, this.mFloatView.getMeasuredWidth(), this.mFloatView.getMeasuredHeight());
            this.mFloatViewOnMeasured = false;
        }
    }

    protected boolean onDragTouchEvent(MotionEvent ev) {
        int action = ev.getAction() & 255;
        switch (ev.getAction() & 255) {
            case 1:
                if (this.mDragState == 4) {
                    stopDrag(false);
                }
                doActionUpOrCancel();
                break;
            case 2:
                continueDrag((int) ev.getX(), (int) ev.getY());
                break;
            case 3:
                if (this.mDragState == 4) {
                    cancelDrag();
                }
                doActionUpOrCancel();
                break;
        }
        return true;
    }

    private void invalidateFloatView() {
        this.mFloatViewInvalidated = true;
    }

    public boolean startDrag(int position, int dragFlags, int deltaX, int deltaY) {
        if (!this.mInTouchEvent || this.mFloatViewManager == null) {
            return false;
        }
        View v = this.mFloatViewManager.onCreateFloatView(position);
        if (v != null) {
            return startDrag(position, v, dragFlags, deltaX, deltaY);
        }
        return false;
    }

    public boolean startDrag(int position, View floatView, int dragFlags, int deltaX, int deltaY) {
        if (this.mDragState != 0 || !this.mInTouchEvent || this.mFloatView != null || floatView == null || !this.mDragEnabled) {
            return false;
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        int pos = position + getHeaderViewsCount();
        this.mFirstExpPos = pos;
        this.mSecondExpPos = pos;
        this.mSrcPos = pos;
        this.mFloatPos = pos;
        this.mDragState = 4;
        this.mDragFlags = 0;
        this.mDragFlags |= dragFlags;
        this.mFloatView = floatView;
        measureFloatView();
        this.mDragDeltaX = deltaX;
        this.mDragDeltaY = deltaY;
        this.mDragStartY = this.mY;
        this.mFloatLoc.x = this.mX - this.mDragDeltaX;
        this.mFloatLoc.y = this.mY - this.mDragDeltaY;
        View srcItem = getChildAt(this.mSrcPos - getFirstVisiblePosition());
        if (srcItem != null) {
            srcItem.setVisibility(4);
        }
        if (this.mTrackDragSort) {
            this.mDragSortTracker.startTracking();
        }
        switch (this.mCancelMethod) {
            case 1:
                super.onTouchEvent(this.mCancelEvent);
                break;
            case 2:
                super.onInterceptTouchEvent(this.mCancelEvent);
                break;
        }
        requestLayout();
        if (this.mLiftAnimator == null) {
            return true;
        }
        this.mLiftAnimator.start();
        return true;
    }

    private void doDragFloatView(boolean forceInvalidate) {
        int movePos = getFirstVisiblePosition() + (getChildCount() / 2);
        View moveItem = getChildAt(getChildCount() / 2);
        if (moveItem != null) {
            doDragFloatView(movePos, moveItem, forceInvalidate);
        }
    }

    private void doDragFloatView(int movePos, View moveItem, boolean forceInvalidate) {
        this.mBlockLayoutRequests = true;
        updateFloatView();
        int oldFirstExpPos = this.mFirstExpPos;
        int oldSecondExpPos = this.mSecondExpPos;
        boolean updated = updatePositions();
        if (updated) {
            adjustAllItems();
            setSelectionFromTop(movePos, (moveItem.getTop() + adjustScroll(movePos, moveItem, oldFirstExpPos, oldSecondExpPos)) - getPaddingTop());
            layoutChildren();
        }
        if (updated || forceInvalidate) {
            invalidate();
        }
        this.mBlockLayoutRequests = false;
    }

    private void updateFloatView() {
        if (this.mFloatViewManager != null) {
            this.mTouchLoc.set(this.mX, this.mY);
            this.mFloatViewManager.onDragFloatView(this.mFloatView, this.mFloatLoc, this.mTouchLoc);
        }
        int floatX = this.mFloatLoc.x;
        int floatY = this.mFloatLoc.y;
        int padLeft = getPaddingLeft();
        if ((this.mDragFlags & 1) == 0 && floatX > padLeft) {
            this.mFloatLoc.x = padLeft;
        } else if ((this.mDragFlags & 2) == 0 && floatX < padLeft) {
            this.mFloatLoc.x = padLeft;
        }
        int numHeaders = getHeaderViewsCount();
        int numFooters = getFooterViewsCount();
        int firstPos = getFirstVisiblePosition();
        int lastPos = getLastVisiblePosition();
        int topLimit = getPaddingTop();
        if (firstPos < numHeaders) {
            topLimit = getChildAt((numHeaders - firstPos) - 1).getBottom();
        }
        if ((this.mDragFlags & 8) == 0 && firstPos <= this.mSrcPos) {
            topLimit = Math.max(getChildAt(this.mSrcPos - firstPos).getTop(), topLimit);
        }
        int bottomLimit = getHeight() - getPaddingBottom();
        if (lastPos >= (getCount() - numFooters) - 1) {
            bottomLimit = getChildAt(((getCount() - numFooters) - 1) - firstPos).getBottom();
        }
        if ((this.mDragFlags & 4) == 0 && lastPos >= this.mSrcPos) {
            bottomLimit = Math.min(getChildAt(this.mSrcPos - firstPos).getBottom(), bottomLimit);
        }
        if (floatY < topLimit) {
            this.mFloatLoc.y = topLimit;
        } else if (this.mFloatViewHeight + floatY > bottomLimit) {
            this.mFloatLoc.y = bottomLimit - this.mFloatViewHeight;
        }
        this.mFloatViewMid = this.mFloatLoc.y + this.mFloatViewHeightHalf;
    }

    private void destroyFloatView() {
        if (this.mFloatView != null) {
            this.mFloatView.setVisibility(GONE);
            if (this.mFloatViewManager != null) {
                this.mFloatViewManager.onDestroyFloatView(this.mFloatView);
            }
            this.mFloatView = null;
            invalidate();
        }
    }

    public void setFloatViewManager(FloatViewManager manager) {
        this.mFloatViewManager = manager;
    }

    public void setDragListener(DragListener l) {
        this.mDragListener = l;
    }

    public void setDragEnabled(boolean enabled) {
        this.mDragEnabled = enabled;
    }

    public boolean isDragEnabled() {
        return this.mDragEnabled;
    }

    public void setDropListener(DropListener l) {
        this.mDropListener = l;
    }

    public void setRemoveListener(RemoveListener l) {
        this.mRemoveListener = l;
    }

    public void setDragSortListener(DragSortListener l) {
        setDropListener(l);
        setDragListener(l);
        setRemoveListener(l);
    }

    public void setDragScrollProfile(DragScrollProfile ssp) {
        if (ssp != null) {
            this.mScrollProfile = ssp;
        }
    }

    public void moveCheckState(int from, int to) {
        SparseBooleanArray cip = getCheckedItemPositions();
        int rangeStart = from;
        int rangeEnd = to;
        if (to < from) {
            rangeStart = to;
            rangeEnd = from;
        }
        rangeEnd++;
        int[] runStart = new int[cip.size()];
        int[] runEnd = new int[cip.size()];
        int runCount = buildRunList(cip, rangeStart, rangeEnd, runStart, runEnd);
        if (runCount != 1 || runStart[0] != runEnd[0]) {
            int i;
            if (from < to) {
                for (i = 0; i != runCount; i++) {
                    setItemChecked(rotate(runStart[i], -1, rangeStart, rangeEnd), true);
                    setItemChecked(rotate(runEnd[i], -1, rangeStart, rangeEnd), false);
                }
                return;
            }
            for (i = 0; i != runCount; i++) {
                setItemChecked(runStart[i], false);
                setItemChecked(runEnd[i], true);
            }
        }
    }

    public void removeCheckState(int position) {
        SparseBooleanArray cip = getCheckedItemPositions();
        if (cip.size() != 0) {
            int[] runStart = new int[cip.size()];
            int[] runEnd = new int[cip.size()];
            int rangeStart = position;
            int rangeEnd = cip.keyAt(cip.size() - 1) + 1;
            int runCount = buildRunList(cip, rangeStart, rangeEnd, runStart, runEnd);
            int i = 0;
            while (i != runCount) {
                if (runStart[i] != position && (runEnd[i] >= runStart[i] || runEnd[i] <= position)) {
                    setItemChecked(rotate(runStart[i], -1, rangeStart, rangeEnd), true);
                }
                setItemChecked(rotate(runEnd[i], -1, rangeStart, rangeEnd), false);
                i++;
            }
        }
    }

    private static int buildRunList(SparseBooleanArray cip, int rangeStart, int rangeEnd, int[] runStart, int[] runEnd) {
        int runCount = 0;
        int i = findFirstSetIndex(cip, rangeStart, rangeEnd);
        if (i == -1) {
            return 0;
        }
        int currentRunStart = cip.keyAt(i);
        int currentRunEnd = currentRunStart + 1;
        while (true) {
            i++;
            if (i >= cip.size()) {
                break;
            }
            int position = cip.keyAt(i);
            if (position >= rangeEnd) {
                break;
            } else if (cip.valueAt(i)) {
                if (position == currentRunEnd) {
                    currentRunEnd++;
                } else {
                    runStart[runCount] = currentRunStart;
                    runEnd[runCount] = currentRunEnd;
                    runCount++;
                    currentRunStart = position;
                    currentRunEnd = position + 1;
                }
            }
        }
        if (currentRunEnd == rangeEnd) {
            currentRunEnd = rangeStart;
        }
        runStart[runCount] = currentRunStart;
        runEnd[runCount] = currentRunEnd;
        runCount++;
        if (runCount > 1 && runStart[0] == rangeStart && runEnd[runCount - 1] == rangeStart) {
            runStart[0] = runStart[runCount - 1];
            runCount--;
        }
        return runCount;
    }

    private static int rotate(int value, int offset, int lowerBound, int upperBound) {
        int windowSize = upperBound - lowerBound;
        value += offset;
        if (value < lowerBound) {
            return value + windowSize;
        }
        if (value >= upperBound) {
            return value - windowSize;
        }
        return value;
    }

    private static int findFirstSetIndex(SparseBooleanArray sba, int rangeStart, int rangeEnd) {
        int size = sba.size();
        int i = insertionIndexForKey(sba, rangeStart);
        while (i < size && sba.keyAt(i) < rangeEnd && !sba.valueAt(i)) {
            i++;
        }
        if (i == size || sba.keyAt(i) >= rangeEnd) {
            return -1;
        }
        return i;
    }

    private static int insertionIndexForKey(SparseBooleanArray sba, int key) {
        int low = 0;
        int high = sba.size();
        while (high - low > 0) {
            int middle = (low + high) >> 1;
            if (sba.keyAt(middle) < key) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }
}
