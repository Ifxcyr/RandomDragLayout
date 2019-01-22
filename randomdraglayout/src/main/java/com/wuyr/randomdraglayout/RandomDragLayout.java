package com.wuyr.randomdraglayout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.os.Handler;
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
@SuppressWarnings("unused")
public class RandomDragLayout extends ViewGroup {

    /**
     * 手指松开后（无滑动速率）, View向屏幕左边移动
     */
    public static final int ORIENTATION_LEFT = 0;
    /**
     * 同上，此为向右
     */
    public static final int ORIENTATION_RIGHT = 1;
    /**
     * 同上，此为向上
     */
    public static final int ORIENTATION_TOP = 2;
    /**
     * 同上，此为向下
     */
    public static final int ORIENTATION_BOTTOM = 3;
    /**
     * 普通状态
     */
    public static final int STATE_NORMAL = 0;
    /**
     * 正在拖拽中
     */
    public static final int STATE_DRAGGING = 1;
    /**
     * 惯性移动中（有滑动速率）
     */
    public static final int STATE_FLINGING = 2;
    /**
     * 手指松开后，向屏幕边缘移动中（无滑动速率）
     */
    public static final int STATE_FLEEING = 3;
    /**
     * 该View已经移动到屏幕外面
     */
    public static final int STATE_OUT_OF_SCREEN = 4;
    /**
     * 该View在屏幕内播放透明渐变动画完毕（消失掉）
     */
    public static final int STATE_GONE = 5;

    private int mState;//当前状态
    private ViewGroup mRootView;//DecorView
    private View mChild;//唯一的子View
    private int mTouchSlop;//触发滑动的最小距离
    private boolean isBeingDragged;//是否已经开始了拖动
    private boolean isGhostViewShown;//GhostView是否已经添加
    private boolean isGhostViewLostControl;//GhostView是否脱离手指
    private boolean isAlphaAnimationRunning;//透明渐变动画是否正在播放
    private float mScrollAvailabilityRatio;
    private float mLastX, mLastY;
    private long mFlingDuration;
    private long mAlphaDuration;
    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;
    private GhostView mGhostView;
    private Bitmap mBitmap;//子View的Bitmap（用来位移，旋转）
    private Canvas mCanvas;
    private ValueAnimator mAnimator;
    private TypeEvaluator<PointF> mEvaluator;
    private OnStateChangeListener mOnStateChangeListener;
    private OnDragListener mOnDragListener;
    private Handler mHandler;
    private Runnable mChildRefreshTask;//子View重绘任务
    private long mChildRefreshPeriod;//间隔时长

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
        mHandler = new Handler();
        //非AndroidStudio预览
        if (!isInEditMode()) {
            //获取activity的根视图,用来添加GhostView
            mRootView = (ViewGroup) getActivity().getWindow().getDecorView();
        }
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mScroller = new Scroller(context);
        mVelocityTracker = VelocityTracker.obtain();
        mScrollAvailabilityRatio = .8F;
        mFlingDuration = 800L;
        mAlphaDuration = 200L;
        initEvaluator();
    }

    /**
     * 重置状态
     *
     * @return 重置成功返回 true，反之
     */
    public boolean reset() {
        if (isBeingDragged || mAnimator != null && mAnimator.isRunning()
                || isAlphaAnimationRunning || !mScroller.isFinished()) {
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
        removeRefreshTask();
        updateState(STATE_NORMAL);
        return true;
    }

    /**
     * 设置惯性移动的利用率
     *
     * @param ratio 范围: 0~1
     */
    public void setScrollAvailabilityRatio(float ratio) {
        mScrollAvailabilityRatio = ratio;
    }

    /**
     * 设置位移动画时长
     */
    public void setFlingDuration(long duration) {
        mFlingDuration = duration;
    }

    /**
     * 设置透明渐变动画时长
     */
    public void setAlphaAnimationDuration(long duration) {
        mAlphaDuration = duration;
    }

    /**
     * 监听状态变化
     */
    public void setOnStateChangeListener(OnStateChangeListener listener) {
        mOnStateChangeListener = listener;
    }

    /**
     * 监听拖动
     */
    public void setOnDragListener(OnDragListener onDragListener) {
        mOnDragListener = onDragListener;
        if (mGhostView != null) {
            mGhostView.setOnDragListener(onDragListener);
        }
    }

    /**
     * 设置子View的重绘周期 默认：0 (不重绘)
     * 一般是内容会不断更新的View才需要设置此参数，静态的View无需设置
     *
     * @param period 间隔时长 建议: 不低于16
     */
    public void setChildRefreshPeriod(long period) {
        removeRefreshTask();
        if (period > 0) {
            mChildRefreshPeriod = period;
            mChildRefreshTask = new Runnable() {
                @Override
                public void run() {
                    if (mGhostView != null) {
                        mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        mChild.draw(mCanvas);
                        mChild.invalidate();
                        postRefreshTask();
                    }
                }
            };
        }
    }

    /**
     * 获取当前位移动画前进的方向，包括：上，下，左，右
     *
     * @return {@link #ORIENTATION_LEFT} or 无状态: -1
     */
    public int getTargetOrientation() {
        return mGhostView == null ? -1 : mGhostView.getTargetOrientation();
    }

    /**
     * 获取当前状态，包框：普通，拖拽中，惯性移动中，非惯性移动中，超出屏幕，消失
     *
     * @return {@link #STATE_NORMAL}
     */
    public int getState() {
        return mState;
    }

    /**
     * 获取map后的bitmap边界 (即：包括了旋转变化后的宽高)
     */
    public RectF getBounds() {
        return mGhostView.getBounds();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            //更新画布尺寸
            mCanvas = new Canvas(mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
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
            if (!isGhostViewLostControl && !isAlphaAnimationRunning && mGhostView != null) {
                mGhostView.updateOffset(x - mLastX, y - mLastY);
                updateState(STATE_DRAGGING);
            }
        } else {
            mChild.draw(mCanvas);
            mChild.setVisibility(INVISIBLE);
            initializeGhostView();
            mRootView.addView(mGhostView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

            MarginLayoutParams layoutParams = (MarginLayoutParams) mChild.getLayoutParams();
            event.offsetLocation(-layoutParams.leftMargin, -layoutParams.topMargin);

            mGhostView.onDown(event, mBitmap);
            isGhostViewShown = true;
            postRefreshTask();
        }
    }

    /**
     * 处理 ACTION_UP 事件
     */
    private void handleActionUp() {
        //如果位移或透明渐变动画正在播放则不处理
        if (!isGhostViewLostControl && !isAlphaAnimationRunning && mGhostView != null) {
            isBeingDragged = false;
            isGhostViewLostControl = true;
            mVelocityTracker.computeCurrentVelocity(500);
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
        updateState(STATE_FLEEING);
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
                updateState(STATE_OUT_OF_SCREEN);
            }
        });
        if (mOnDragListener != null) {
            mGhostView.setOnDragListener(mOnDragListener);
        }
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
     * 判断前两个参数是否至少一个大于第三个参数
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
        updateState(STATE_FLINGING);
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
        if (mChild == null) {
            throw new IllegalStateException("RandomDragLayout at least one child is needed!");
        }
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
     * 重写父类addView方法，仅允许拥有一个直接子View
     */
    @Override
    public void addView(View child, int index, LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("RandomDragLayout can only contain 1 child!");
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
                //防止报：Attempt to read from field 'int android.view.View.mViewFlags' on a null object reference
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (mGhostView != null) {
                            //如果是超出了屏幕，则不播放渐变动画，直接移除
                            if (mState == STATE_OUT_OF_SCREEN) {
                                mRootView.removeView(mGhostView);
                                mGhostView = null;
                                isAlphaAnimationRunning = false;
                                removeRefreshTask();
                            } else {
                                startAlphaAnimation();
                            }
                            isGhostViewLostControl = false;
                        }
                    }
                });
            }
            //惯性移动完毕，重置偏移量
            mLastScrollOffsetX = 0;
            mLastScrollOffsetY = 0;
        }
    }

    /***
     * 播放透明渐变动画，然后移除GhostView
     */
    private void startAlphaAnimation() {
        if (mGhostView != null) {
            isAlphaAnimationRunning = true;
            mGhostView.animate().alpha(0).setDuration(mAlphaDuration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mRootView.removeView(mGhostView);
                    mGhostView = null;
                    isAlphaAnimationRunning = false;
                    updateState(STATE_GONE);
                    removeRefreshTask();
                }
            }).start();
        }
    }

    /**
     * 更新状态并回调监听器
     *
     * @param newState 新的状态
     */
    private void updateState(int newState) {
        if (mState != newState) {
            mState = newState;
            if (mOnStateChangeListener != null) {
                mOnStateChangeListener.onStateChanged(newState);
            }
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

    private void removeRefreshTask() {
        if (mChildRefreshTask != null) {
            mHandler.removeCallbacks(mChildRefreshTask);
        }
    }

    private void postRefreshTask() {
        if (mChildRefreshTask != null) {
            mHandler.postDelayed(mChildRefreshTask, mChildRefreshPeriod);
        }
    }

    /**
     * 自定义Evaluator，使ValueAnimator支持PointF
     */
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

    public interface OnStateChangeListener {
        /**
         * 状态更新回调
         *
         * @param newState 新的状态
         */
        void onStateChanged(int newState);
    }

    public interface OnDragListener {
        /**
         * 拖动更新时回调
         *
         * @param x       触摸点在X轴上的绝对坐标
         * @param y       触摸点在Y轴上的绝对坐标
         * @param degrees GhostView的绝对旋转角度 (0~360)
         */
        void onUpdate(float x, float y, float degrees);
    }
}
