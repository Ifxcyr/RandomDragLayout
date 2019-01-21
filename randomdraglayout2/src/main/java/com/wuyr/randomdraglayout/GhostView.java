package com.wuyr.randomdraglayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

import static com.wuyr.randomdraglayout.RandomDragLayout.ORIENTATION_BOTTOM;
import static com.wuyr.randomdraglayout.RandomDragLayout.ORIENTATION_LEFT;
import static com.wuyr.randomdraglayout.RandomDragLayout.ORIENTATION_RIGHT;
import static com.wuyr.randomdraglayout.RandomDragLayout.ORIENTATION_TOP;

/**
 * @author wuyr
 * @github https://github.com/wuyr/RandomDragLayout
 * @since 2019-01-17 上午11:40
 */
@SuppressLint("ViewConstructor")
class GhostView extends View {

    private int mTargetOrientation = -1;
    private Bitmap mBitmap;
    private float mDownX, mDownY, mDownRawX;
    private float mBitmapCenterX, mBitmapCenterY;
    private float mCurrentRawX, mCurrentRawY;
    private float mStartAngle;
    private float mCurrentAngle;
    private boolean isFlinging;
    private boolean isLeanLeft;
    private boolean isClockwise;
    private Matrix mMatrix;
    private RectF mBitmapRect;
    private OnOutOfScreenListener mOnOutOfScreenListener;

    GhostView(Context context, OnOutOfScreenListener listener) {
        super(context);
        mOnOutOfScreenListener = listener;
        mMatrix = new Matrix();
        mBitmapRect = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null) {

            float l = mCurrentRawX - mDownX, t = mCurrentRawY - mDownY;
            float r = l + mBitmap.getWidth();
            float b = t + mBitmap.getHeight();

            mBitmapRect.set(l, t, r, b);

            mMatrix.setRotate(mCurrentAngle, mCurrentRawX, mCurrentRawY);
            mMatrix.mapRect(mBitmapRect);

            canvas.setMatrix(mMatrix);
            canvas.drawBitmap(mBitmap, l, t, null);

            if (checkIsContentOutOfScreen()) {
                if (mOnOutOfScreenListener != null) {
                    mOnOutOfScreenListener.onOutOfScreen(this);
                }
            }
        }
    }

    /**
     * 当此方法被调用时，表示已经开始了拖动
     *
     * @param event  触摸事件
     * @param bitmap View所对应的Bitmap
     */
    void onDown(MotionEvent event, Bitmap bitmap) {

        mCurrentRawX = mDownRawX = event.getRawX();
        mCurrentRawY = event.getRawY();
        mDownX = event.getX();
        mDownY = event.getY();

        float l = mCurrentRawX - mDownX, t = mCurrentRawY - mDownY;
        mBitmapCenterX = l + bitmap.getWidth() / 2F;
        mBitmapCenterY = t + bitmap.getHeight() / 2F;

        mStartAngle = computeClockwiseAngle(mBitmapCenterX, mBitmapCenterY, mCurrentRawX, mCurrentRawY);

        float halfWidth = bitmap.getWidth() / 2F;
        isLeanLeft = mDownX < halfWidth;

        mBitmap = bitmap;
        invalidate();
    }

    /**
     * 更新Bitmap的坐标和旋转角度
     *
     * @param offsetX X轴上新的位置（相对）
     * @param offsetY Y轴上新的位置（相对）
     */
    void updateOffset(float offsetX, float offsetY) {
        mCurrentRawX += offsetX;
        mCurrentRawY += offsetY;
        if (isFlinging) {
            mBitmapCenterX = mBitmapRect.centerX();
            mBitmapCenterY = mBitmapRect.centerY();
            mDownRawX = mCurrentRawX;
        }
        mCurrentAngle = computeClockwiseAngle(mBitmapCenterX, mBitmapCenterY, mDownRawX, mCurrentRawY) - mStartAngle;
        invalidate();
    }

    /**
     * 播放位移动画时的帧更新回调
     *
     * @param location 新的位置（绝对）
     */
    void onAnimationUpdate(PointF location) {
        if (mBitmap != null) {
            float moveOffset = getMoveOffset(location);
            //90代表滑动距离=(View宽或View高)时的旋转角度
            float angleOffset = Math.abs(moveOffset / Math.max(mBitmap.getWidth(), mBitmap.getHeight()) * 90F);
            //延续之前的旋转方向：如果之前是顺时针转，那就继续顺时针转，反之
            mCurrentAngle += isClockwise ? angleOffset : -angleOffset;
            mCurrentRawX = location.x;
            mCurrentRawY = location.y;

            invalidate();
        }
    }

    /**
     * 标记已经开始惯性移动
     */
    void setFlinging() {
        isFlinging = true;
    }

    /**
     * 获取当前位移动画前进的方向
     * @return {@see RandomDragLayout.ORIENTATION} or 无状态: -1
     */
    int getTargetOrientation(){
        return mTargetOrientation;
    }

    /**
     * 获取位移动画的起点
     *
     * @return 起点位置
     */
    PointF getAnimationStartPoint() {
        updateRotateOrientation();
        return new PointF(mCurrentRawX, mCurrentRawY);
    }

    /**
     * 获取位移动画的终点
     *
     * @return 终点位置
     */
    PointF getAnimationEndPoint() {
        float halfWidth = mBitmapCenterX;
        float halfHeight = mBitmapCenterY;
        float leftPercent = 1F - mCurrentRawX / halfWidth;
        float rightPercent = (mCurrentRawX - halfWidth) / halfWidth;
        float topPercent = 1F - mCurrentRawY / halfHeight;
        float bottomPercent = (mCurrentRawY - halfHeight) / halfHeight;
        float max = Math.max(Math.max(leftPercent, rightPercent), Math.max(topPercent, bottomPercent));
        //反正一移动出屏幕就会移除View并中断动画，并且我们需要在任何地方的移动速度都不变，所以我们的距离可以指定为屏幕高度 + View高度
        int maxBitmapLength = (int) Math.max(mBitmapRect.width(), mBitmapRect.height());
        if (maxBitmapLength == 0) {
            return new PointF();
        }
        float distance = Math.max(getWidth(), getHeight()) + maxBitmapLength;
        int offset = -maxBitmapLength + new Random().nextInt(maxBitmapLength * 2);
        float toX, toY;
        if (max == leftPercent) {
            toX = -distance;
            toY = offset;
            mTargetOrientation = ORIENTATION_LEFT;
        } else if (max == rightPercent) {
            toX = mCurrentRawX + distance;
            toY = offset;
            mTargetOrientation = ORIENTATION_RIGHT;
        } else if (max == topPercent) {
            toX = offset;
            toY = -distance;
            mTargetOrientation = ORIENTATION_TOP;
        } else {
            toX = offset;
            toY = mCurrentRawY + distance;
            mTargetOrientation = ORIENTATION_BOTTOM;
        }
        return new PointF(toX, toY);
    }

    /**
     * 根据当前动画前进的方向来计算偏移量
     *
     * @param location 新的位置（绝对）
     * @return 偏移量（相对）
     */
    private float getMoveOffset(PointF location) {
        return mTargetOrientation == ORIENTATION_LEFT ||
                mTargetOrientation == ORIENTATION_RIGHT ?
                location.x - mCurrentRawX : location.y - mCurrentRawY;
    }

    /**
     * 检查Bitmap是否完全draw在屏幕之外
     */
    private boolean checkIsContentOutOfScreen() {
        return mBitmapRect.bottom < 0
                || mBitmapRect.top > getBottom()
                || mBitmapRect.right < 0
                || mBitmapRect.left > getRight();
    }

    /**
     * 更新旋转方向
     */
    private void updateRotateOrientation() {
        isClockwise = isLeanLeft ? mCurrentRawY < mBitmapCenterY : mCurrentRawY > mBitmapCenterY;
    }

    /**
     * 计算两个坐标点的顺时针角度，以第一个坐标点为圆心
     *
     * @param startX 起始点X轴的值
     * @param startY 起始点Y轴的值
     * @param endX   结束点X轴的值
     * @param endY   结束点Y轴的值
     * @return 以起始点为圆心计算的顺时针角度
     */
    private float computeClockwiseAngle(float startX, float startY, float endX, float endY) {
        int appendAngle = computeNeedAppendAngle(startX, startY, endX, endY);
        float lineA = Math.abs(endX - startX);
        float lineB = Math.abs(endY - startY);
        float lineC = (float) Math.sqrt(Math.pow(lineA, 2) + Math.pow(lineB, 2));
        float angle;
        if (appendAngle == 0 || appendAngle == 180) {
            angle = (float) Math.toDegrees(Math.acos(lineA / lineC));
        } else {
            angle = (float) Math.toDegrees(Math.acos(lineB / lineC));
        }
        return angle + appendAngle;
    }

    /**
     * 根据两点的位置来判断从起始点到结束点连线后的象限，并返回对应的角度
     *
     * @param startX 起始点X轴的值
     * @param startY 起始点Y轴的值
     * @param endX   结束点X轴的值
     * @param endY   结束点Y轴的值
     * @return 对应象限的顺时针基础角度
     */
    private int computeNeedAppendAngle(float startX, float startY, float endX, float endY) {
        return (endX > startX) ? (endY > startY ? 0 : 270) : (endY > startY ? 90 : 180);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    /**
     * 释放资源
     */
    private void release() {
        mBitmap = null;
        mMatrix = null;
        mBitmapRect = null;
        mOnOutOfScreenListener = null;
    }

    interface OnOutOfScreenListener {
        /**
         * 当Canvas的内容全部draw在View的边界外面时回调此方法
         *
         * @param view 发生事件所对应的View
         */
        void onOutOfScreen(GhostView view);
    }
}
