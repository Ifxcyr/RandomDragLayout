package com.wuyr.randomdraglayout;

import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/**
 * @author wuyr
 * @github https://github.com/wuyr/RandomDragLayout
 * @since 2019-01-17 上午11:40
 */
public class RandomDragLayout extends ViewGroup {

    private ViewGroup mRootView;//DecorView
    private View mChild;//唯一的子View
    private int mTouchSlop;//触发滑动的最小距离
    private boolean isBeingDragged;//是否已经开始了拖动
    private boolean isGhostViewShown;//GhostView是否已经添加
    private boolean isGhostViewLostControl;//GhostView是否脱离手指
    private float mScrollAvailabilityRatio;
    private float mLastX, mLastY;
    private long mFlingDuration;
    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;
    private GhostView mGhostView;
    private Bitmap mBitmap;//子View的Bitmap（用来位移，旋转）
    private Canvas mCanvas;
    private ValueAnimator mAnimator;
    private TypeEvaluator<PointF> mEvaluator;

    public RandomDragLayout(Context context) {
        this(context, null);
    }

    public RandomDragLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RandomDragLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        //获取activity的根视图,用来添加GhostView
        mRootView = (ViewGroup) getActivity().getWindow().getDecorView();
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mScroller = new Scroller(context);
        mVelocityTracker = VelocityTracker.obtain();
        mScrollAvailabilityRatio = .5F;
        mFlingDuration = 800L;
        initEvaluator();
    }

    /**
     * 重置状态
     *
     * @return 重置成功返回 true，反之
     */
    public boolean reset() {
        if (isBeingDragged || mAnimator != null && mAnimator.isRunning() || !mScroller.isFinished()) {
            return false;
        }
        if (mGhostView != null) {
            mRootView.removeView(mGhostView);
            mGhostView = null;
        }
        mChild.setVisibility(VISIBLE);
        isGhostViewShown = false;
        isGhostViewLostControl = false;
        mAnimator = null;
        return true;
    }

    /**
     * 设置惯性移动的利用率
     */
    public void setScrollAvailabilityRatio(float ratio) {
        mScrollAvailabilityRatio = ratio;
    }

    /**
     * 设置位移动画时长
     */
    public void setFlingDuration(long duration){
        mFlingDuration = duration;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            //更新画布尺寸
            mCanvas = new Canvas(mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        mVelocityTracker.addMovement(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                handleActionMove(event, x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                handleActionUp();
                break;
            default:
                break;
        }
        mLastX = x;
        mLastY = y;
        return true;
    }

    /**
     * 处理 ACTION_MOVE 事件
     */
    private void handleActionMove(MotionEvent event, float x, float y) {
        if (isGhostViewShown) {
            //手指未松开才更新
            if (!isGhostViewLostControl && mGhostView != null) {
                mGhostView.updateOffset(x - mLastX, y - mLastY);
            }
        } else {
            draw(mCanvas);
            mChild.setVisibility(INVISIBLE);
            initializeGhostView();
            mRootView.addView(mGhostView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            mGhostView.onDown(event, mBitmap);
            isGhostViewShown = true;
        }
    }

    /**
     * 处理 ACTION_UP 事件
     */
    private void handleActionUp() {
        //如果位移动画正在播放或者
        if (!isGhostViewLostControl && mGhostView != null) {
            isBeingDragged = false;
            isGhostViewLostControl = true;
            mVelocityTracker.computeCurrentVelocity(1000);
            float xVelocity = mVelocityTracker.getXVelocity();
            float yVelocity = mVelocityTracker.getYVelocity();
            if (isOneBiggerThan(xVelocity, yVelocity, 1000)) {
                startFling(xVelocity, yVelocity);
            } else {
                startAnimator();
            }
        }
    }

    /**
     * 播放位移动画
     */
    private void startAnimator() {
        mAnimator = ValueAnimator.ofObject(mEvaluator, mGhostView.getAnimationStartPoint(),
                mGhostView.getAnimationEndPoint()).setDuration(mFlingDuration);

        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (mGhostView != null) {
                    mGhostView.onAnimationUpdate((PointF) animation.getAnimatedValue());
                }
            }
        });
        mAnimator.start();
    }

    /**
     * 初始化GhostView
     */
    private void initializeGhostView() {
        if (mGhostView != null) {
            mRootView.removeView(mGhostView);
        }
        mGhostView = new GhostView(getContext(), new GhostView.OnOutOfScreenListener() {
            @Override
            public void onOutOfScreen(GhostView view) {
                isGhostViewLostControl = true;
                abortAnimation();
                invalidate();
            }
        });
    }

    /**
     * 打断动画
     */
    private void abortAnimation() {
        mScroller.abortAnimation();
        if (mAnimator != null) {
            mAnimator.cancel();
        }
    }

    /**
     * @param value1
     * @param value2
     * @param target
     * @return
     */
    private boolean isOneBiggerThan(float value1, float value2, int target) {
        return Math.abs(value1) > target || Math.abs(value2) > target;
    }

    /**
     * 开始惯性移动
     */
    private void startFling(float xVelocity, float yVelocity) {
        mGhostView.setFlinging();
        mScroller.fling(0, 0, (int) xVelocity, (int) yVelocity,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        invalidate();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if ((event.getAction() == MotionEvent.ACTION_MOVE && isBeingDragged) || super.onInterceptTouchEvent(event)) {
            //如果已经开始了拖动，则继续占用此次事件
            requestDisallowInterceptTouchEvent(true);
            return true;
        }
        float x = event.getX(), y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float offsetX = x - mLastX;
                float offsetY = y - mLastY;
                //判断是否触发拖动事件
                if (isOneBiggerThan(offsetX, offsetY, mTouchSlop)) {
                    mLastX = x;
                    mLastY = y;
                    isBeingDragged = true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                isBeingDragged = false;
                break;
        }
        requestDisallowInterceptTouchEvent(isBeingDragged);
        return isBeingDragged;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChild(mChild, widthMeasureSpec, heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width, height;
        MarginLayoutParams layoutParams = (MarginLayoutParams) mChild.getLayoutParams();
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else {
            width = mChild.getMeasuredWidth() + layoutParams.leftMargin + layoutParams.rightMargin;
        }
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            height = mChild.getMeasuredHeight() + layoutParams.topMargin + layoutParams.bottomMargin;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        MarginLayoutParams layoutParams = (MarginLayoutParams) mChild.getLayoutParams();
        mChild.layout(getPaddingLeft() + layoutParams.leftMargin, getPaddingTop() + layoutParams.topMargin,
                mChild.getMeasuredWidth() - getPaddingRight() + layoutParams.leftMargin,
                mChild.getMeasuredHeight() - getPaddingBottom() + layoutParams.topMargin);
    }

    /**
     * 重写父类addView方法，仅允许一个子View
     */
    @Override
    public void addView(View child, int index, LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("DragRotateLayout can only contain 1 child!");
        }
        super.addView(child, index, params);
        mChild = child;
    }

    private float mLastScrollOffsetX, mLastScrollOffsetY;

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            float y = mScroller.getCurrY() * mScrollAvailabilityRatio;
            float x = mScroller.getCurrX() * mScrollAvailabilityRatio;
            mGhostView.updateOffset(x - mLastScrollOffsetX, y - mLastScrollOffsetY);
            mLastScrollOffsetX = x;
            mLastScrollOffsetY = y;
            invalidate();
        } else if (mScroller.isFinished()) {
            if (isGhostViewLostControl && mRootView != null) {
                isGhostViewLostControl = false;
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (mGhostView != null) {
                            // TODO: 19-1-17 播放透明动画后移除view
                            mRootView.removeView(mGhostView);
                            mGhostView = null;
                        }
                    }
                });
            }
            //惯性移动完毕，重置偏移量
            mLastScrollOffsetX = 0;
            mLastScrollOffsetY = 0;
        }
    }

    @Override
    public MarginLayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected MarginLayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    protected MarginLayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    private void initEvaluator() {
        mEvaluator = new TypeEvaluator<PointF>() {

            private final PointF temp = new PointF();

            @Override
            public PointF evaluate(float fraction, PointF startValue, PointF endValue) {
                float totalX = endValue.x - startValue.x;
                float totalY = endValue.y - startValue.y;
                float x = startValue.x + (totalX * fraction);
                float y = startValue.y + (totalY * fraction);
                temp.set(x, y);
                return temp;
            }
        };
    }

    /**
     * 根据View的Context来获取对应的Activity
     *
     * @return 该View所在的Activity
     */
    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        throw new RuntimeException("Activity not found!");
    }
}
