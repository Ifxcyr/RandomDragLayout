## 任意拖布局 （仿QQ空间的列表Header效果）
### 博客详情： 敬请期待。。。

### 使用方式:
#### 添加依赖：
```
implementation 'com.wuyr:randomdraglayout:1.0.0'
```

### APIs:
|Method|Description|
|------|-----------|
|setAlphaAnimationDuration(long duration)|设置透明渐变动画时长 **默认: 200L**|
|setFlingDuration(long duration)|设置位移动画时长 **默认: 800L**|
|setScrollAvailabilityRatio(float ratio)|设置惯性移动的利用率 **范围: 0~1 默认: 0.8F**|
|setOnDragListener(OnDragListener onDragListener)|监听拖动 **参数: 绝对X, 绝对Y, 绝对旋转角度**|
|setOnStateChangeListener(OnStateChangeListener listener)|监听状态变化 **状态:**<br/>STATE_NORMAL (普通状态)<br/>STATE_DRAGGING (正在拖拽中)<br/>STATE_FLINGING (惯性移动中（有滑动速率）)<br/>STATE_FLEEING (手指松开后，动画移动中（无速率）)<br/>STATE_OUT_OF_SCREEN (已经移动到屏幕外面)<br/>STATE_GONE (在屏幕内慢慢消失掉（透明渐变）)|
|boolean reset()|重置状态 (重新初始化)|
|int getTargetOrientation()|获取当前位移动画前进的方向 **方向:**<br/>ORIENTATION_LEFT (向左移动)<br/>ORIENTATION_RIGHT (向右移动)<br/>ORIENTATION_TOP (向上移动)<br/>ORIENTATION_BOTTOM (向下移动)|
|RectF getBounds()|获取映射后的Bitmap边界 (即：包括了旋转之后的宽高)|
|int getState()|获取当前状态 **状态: 见上**|

## Demo下载: 敬请期待。。。
## Demo源码地址: 敬请期待。。。
## 效果图:
### 敬请期待。。。