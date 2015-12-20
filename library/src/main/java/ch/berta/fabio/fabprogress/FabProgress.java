/*
 * Copyright (c) 2015 Fabio Berta and Jorge Castillo Pérez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.berta.fabio.fabprogress;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Provides a {@link FloatingActionButton} that allows to display an indeterminate progress circle
 * around it self to indicate a running process. Once the process finished the user can start the
 * final animation that makes the circle determinant.
 * <p/>
 * Subclass of {@link FloatingActionButton}.
 */
public class FabProgress extends FloatingActionButton {

    // These values must match those in the FABs attrs declaration
    private static final int SIZE_MINI = 1;
    private static final int SIZE_NORMAL = 0;

    private static final String STATE_SUPER = "STATE_SUPER";
    private static final String STATE_ANIM = "STATE_ANIM";
    private static final String STATE_COMPLETE = "STATE_COMPLETE";
    private static final int NO_ANIM = 0;
    private static final int ANIM_SHOWING = 1;
    private static final int FINAL_ANIM_SHOWING = 2;

    private static final int MINIMUM_SWEEP_ANGLE = 20;
    private static final int MAXIMUM_SWEEP_ANGLE = 300;
    private static final int ROTATE_ANIMATOR_DURATION = 2000;
    private static final int SWEEP_ANIM_DURATION = 1000;
    private static final int COMPLETE_ANIM_DURATION = SWEEP_ANIM_DURATION * 2;
    private static final int COMPLETE_ROTATE_DURATION = COMPLETE_ANIM_DURATION * 6;
    private static final int REUSABLE_RESET_DELAY = 2000;
    private static final int ICON_CHANGE_ANIM_DURATION = 50;
    private static final DecelerateInterpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();
    private static final FastOutSlowInInterpolator FAST_OUT_SLOW_IN_INTERPOLATOR = new FastOutSlowInInterpolator();
    private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
    private final RectF mArcBounds = new RectF();
    private final Rect mShadowPadding = new Rect();
    private final Paint mPaint = new Paint();
    private int mAnimState;
    private boolean mIsComplete;
    private Drawable mFabIcon;
    private int mFabSize;
    @ColorInt
    private int mAccentColor;
    @ColorInt
    private int mArcColor;
    private int mArcWidth;
    private boolean mUseRoundedStroke;
    private Drawable mCompleteIcon;
    private boolean mIsReusable;
    private ValueAnimator mRotateAnim;
    private ValueAnimator mGrowAnim;
    private ValueAnimator mShrinkAnim;
    private ValueAnimator mCompleteAnim;
    private float mCurrentSweepAngle;
    private float mCurrentRotationAngleOffset;
    private float mCurrentRotationAngle;
    private boolean mAnimationIsPlaying;
    private boolean mIsGrowing;
    private boolean mShowCompleteAnimOnNextCycle;
    private ProgressFinalAnimationListener mProgressFinalAnimationListener;

    public FabProgress(@NonNull Context context) {
        super(context);
    }

    public FabProgress(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs, 0);
    }

    public FabProgress(@NonNull Context context, @NonNull AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs, defStyleAttr);
    }

    private void init(@NonNull Context context, @NonNull AttributeSet attrs, int defStyleAttr) {
        mArcWidth = getResources().getDimensionPixelSize(R.dimen.fp_progress_arc_stroke_width);
        mFabIcon = getDrawable();
        mAccentColor = fetchAccentColor();

        TypedArray attr = context.obtainStyledAttributes(attrs, R.styleable.FabProgress, defStyleAttr, 0);
        try {
            mArcColor = attr.getColor(R.styleable.FabProgress_fp_arcColor,
                    ContextCompat.getColor(context, R.color.green_500));
            mUseRoundedStroke = attr.getBoolean(R.styleable.FabProgress_fp_roundedStroke, false);
            mCompleteIcon = attr.getDrawable(R.styleable.FabProgress_fp_finalIcon);
            if (mCompleteIcon == null) {
                mCompleteIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_done_white_24dp);
            }
            mIsReusable = attr.getBoolean(R.styleable.FabProgress_fp_reusable, false);
        } finally {
            attr.recycle();
        }

        if (!Utils.isRunningLollipopAndHigher()) {
            setFakeShadowPadding(context, attrs, defStyleAttr);
        }

        setupPaint();
        setupAnimations();
    }

    /**
     * We are using private resources here in order to get the fab size. This is hacky but the
     * alternative would be that the user needs to specify the fab size two times in the XML.
     * TODO: only needed as long as we support api <21, because of the space the shadow drawable takes up
     *
     * @param context      the context to obtain the style attributes
     * @param attrs        the attribute set
     * @param defStyleAttr the style attr
     */
    @SuppressLint("PrivateResource")
    private void setFakeShadowPadding(@NonNull Context context, @NonNull AttributeSet attrs,
                                      int defStyleAttr) {
        TypedArray attrFab = context.obtainStyledAttributes(attrs, R.styleable.FloatingActionButton,
                defStyleAttr, 0);
        try {
            final Resources res = getResources();
            int size = attrFab.getInt(R.styleable.FloatingActionButton_fabSize, SIZE_NORMAL);
            if (size == SIZE_MINI) {
                mFabSize = res.getDimensionPixelSize(R.dimen.fp_fab_size_mini);
            } else {
                mFabSize = res.getDimensionPixelSize(R.dimen.fp_fab_size_normal);
            }
        } finally {
            attrFab.recycle();
        }

        final int maxContentSize = (int) getResources().getDimension(R.dimen.fp_fab_content_size);
        final int contentPadding = (mFabSize - maxContentSize) / 2;
        mShadowPadding.left = getPaddingLeft() - contentPadding;
        mShadowPadding.top = getPaddingTop() - contentPadding;
        mShadowPadding.right = getPaddingEnd() - contentPadding;
        mShadowPadding.bottom = getPaddingBottom() - contentPadding;
    }

    private int fetchAccentColor() {
        final TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(R.attr.colorAccent, value, true);
        return value.data;
    }

    private void setupPaint() {
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mArcWidth);
        mPaint.setStrokeCap(mUseRoundedStroke ? Paint.Cap.ROUND : Paint.Cap.BUTT);
        mPaint.setColor(mArcColor);
    }

    private void setupAnimations() {
        setupRotateAnimation();
        setupGrowAnimation();
        setupShrinkAnimation();
        setupCompleteAnimation();
    }

    private void setupRotateAnimation() {
        mRotateAnim = ValueAnimator.ofFloat(0f, 360f);
        mRotateAnim.setInterpolator(LINEAR_INTERPOLATOR);
        mRotateAnim.setDuration(ROTATE_ANIMATOR_DURATION);
        mRotateAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float angle = getAnimatedFraction(animation) * 360f;
                updateCurrentRotationAngle(angle);
            }
        });
        mRotateAnim.setRepeatCount(ValueAnimator.INFINITE);
        mRotateAnim.setRepeatMode(ValueAnimator.RESTART);
    }

    private void updateCurrentRotationAngle(float currentRotationAngle) {
        mCurrentRotationAngle = currentRotationAngle;
        invalidate();
    }

    private void setupGrowAnimation() {
        mGrowAnim = ValueAnimator.ofFloat(MINIMUM_SWEEP_ANGLE, MAXIMUM_SWEEP_ANGLE);
        mGrowAnim.setInterpolator(DECELERATE_INTERPOLATOR);
        mGrowAnim.setDuration(SWEEP_ANIM_DURATION);
        mGrowAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatedFraction = getAnimatedFraction(animation);
                float angle = MINIMUM_SWEEP_ANGLE + animatedFraction * (MAXIMUM_SWEEP_ANGLE - MINIMUM_SWEEP_ANGLE);
                updateCurrentSweepAngle(angle);
            }
        });
        mGrowAnim.addListener(new Animator.AnimatorListener() {
            boolean cancelled;

            @Override
            public void onAnimationStart(Animator animation) {
                cancelled = false;
                mIsGrowing = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    setShrinking();
                    mShrinkAnim.start();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    private void setShrinking() {
        mIsGrowing = false;
        mCurrentRotationAngleOffset = mCurrentRotationAngleOffset + (360 - MAXIMUM_SWEEP_ANGLE);
    }

    private void setupShrinkAnimation() {
        mShrinkAnim = ValueAnimator.ofFloat(MAXIMUM_SWEEP_ANGLE, MINIMUM_SWEEP_ANGLE);
        mShrinkAnim.setInterpolator(DECELERATE_INTERPOLATOR);
        mShrinkAnim.setDuration(SWEEP_ANIM_DURATION);
        mShrinkAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatedFraction = getAnimatedFraction(animation);
                updateCurrentSweepAngle(MAXIMUM_SWEEP_ANGLE -
                        animatedFraction * (MAXIMUM_SWEEP_ANGLE - MINIMUM_SWEEP_ANGLE));
            }
        });
        mShrinkAnim.addListener(new Animator.AnimatorListener() {
            boolean cancelled;

            @Override
            public void onAnimationStart(Animator animation) {
                cancelled = false;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    setGrowing();
                    if (mShowCompleteAnimOnNextCycle) {
                        mShowCompleteAnimOnNextCycle = false;
                        mCompleteAnim.start();
                    } else {
                        mGrowAnim.start();
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    private void setGrowing() {
        mIsGrowing = true;
        mCurrentRotationAngleOffset += MINIMUM_SWEEP_ANGLE;
    }

    private void setupCompleteAnimation() {
        mCompleteAnim = ValueAnimator.ofFloat(MAXIMUM_SWEEP_ANGLE, MINIMUM_SWEEP_ANGLE);
        mCompleteAnim.setInterpolator(DECELERATE_INTERPOLATOR);
        mCompleteAnim.setDuration(COMPLETE_ANIM_DURATION);
        mCompleteAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatedFraction = getAnimatedFraction(animation);
                float angle = MINIMUM_SWEEP_ANGLE + animatedFraction * 360;
                updateCurrentSweepAngle(angle);
            }
        });
        mCompleteAnim.addListener(new Animator.AnimatorListener() {
            boolean cancelled;

            @Override
            public void onAnimationStart(Animator animation) {
                cancelled = false;
                mIsGrowing = true;
                mRotateAnim.setInterpolator(DECELERATE_INTERPOLATOR);
                mRotateAnim.setDuration(COMPLETE_ROTATE_DURATION);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    stopProgress();
                }

                mCompleteAnim.removeListener(this);
                onArcFinalAnimationComplete();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    private float getAnimatedFraction(@NonNull ValueAnimator animator) {
        float fraction = ((float) animator.getCurrentPlayTime()) / animator.getDuration();
        fraction = Math.min(fraction, 1f);
        fraction = animator.getInterpolator().getInterpolation(fraction);
        return fraction;
    }

    private void updateCurrentSweepAngle(float currentSweepAngle) {
        mCurrentSweepAngle = currentSweepAngle;
        invalidate();
    }

    private void resetArcProperties() {
        mCurrentSweepAngle = 0;
        mCurrentRotationAngle = 0;
        mCurrentRotationAngleOffset = 0;
    }

    private void stopAnimators() {
        mRotateAnim.cancel();
        mGrowAnim.cancel();
        mShrinkAnim.cancel();
        mCompleteAnim.cancel();
    }

    private void onArcFinalAnimationComplete() {
        mIsComplete = true;
        fadeOut(true, false);
    }

    private void fadeOut(boolean animate, final boolean reverse) {
        if (animate) {
            animate()
                    .setDuration(ICON_CHANGE_ANIM_DURATION)
                    .alpha(0)
                    .scaleX(0)
                    .scaleY(0)
                    .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);

                            if (reverse) {
                                setImageDrawable(mFabIcon);
                                setBackgroundTintList(ColorStateList.valueOf(mAccentColor));
                            } else {
                                setImageDrawable(mCompleteIcon);
                                setBackgroundTintList(ColorStateList.valueOf(mArcColor));
                            }

                            fadeIn(reverse);
                        }
                    })
                    .start();
        } else if (reverse) {
            setImageDrawable(mFabIcon);
            setBackgroundTintList(ColorStateList.valueOf(mAccentColor));
        } else {
            setImageDrawable(mCompleteIcon);
            setBackgroundTintList(ColorStateList.valueOf(mArcColor));
        }
    }

    private void fadeIn(final boolean reverse) {
        animate()
                .setDuration(ICON_CHANGE_ANIM_DURATION)
                .alpha(1)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);

                        if (!reverse) {
                            if (mProgressFinalAnimationListener != null) {
                                mProgressFinalAnimationListener.onProgressFinalAnimationComplete();
                            }

                            if (mIsReusable) {
                                postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        resetProgress();
                                    }
                                }, REUSABLE_RESET_DELAY);
                            }
                        }
                    }
                })
                .start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (Utils.isRunningLollipopAndHigher()) {
            mArcBounds.left = 0;
            mArcBounds.top = 0;
            mArcBounds.right = w;
            mArcBounds.bottom = h;
        } else {
            mArcBounds.left = mShadowPadding.left;
            mArcBounds.top = mShadowPadding.top;
            mArcBounds.right = mShadowPadding.left + mFabSize;
            mArcBounds.bottom = mShadowPadding.bottom + mFabSize;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float startAngle = mCurrentRotationAngle - mCurrentRotationAngleOffset;
        float sweepAngle = mCurrentSweepAngle;
        if (!mIsGrowing) {
            startAngle = startAngle + (360 - sweepAngle);
        }

        canvas.drawArc(mArcBounds, startAngle, sweepAngle, false, mPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // disable touch events when complete view is shown
        return mIsComplete || super.onTouchEvent(event);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(STATE_SUPER, super.onSaveInstanceState());
        bundle.putBoolean(STATE_COMPLETE, mIsComplete);
        bundle.putInt(STATE_ANIM, mAnimState);

        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;

            mIsComplete = bundle.getBoolean(STATE_COMPLETE);
            if (mIsComplete) {
                fadeOut(false, false);
            } else {
                mAnimState = bundle.getInt(STATE_ANIM);
                switch (mAnimState) {
                    case ANIM_SHOWING:
                        startProgress();
                        break;
                    case FINAL_ANIM_SHOWING:
                        startProgress();
                        startProgressFinalAnimation();
                        break;
                }
            }

            state = bundle.getParcelable(STATE_SUPER);
        }

        super.onRestoreInstanceState(state);
    }

    /**
     * Sets the callback for when the final animation is complete.
     *
     * @param listener the listener that gets called when the final animation is complete
     */
    public void setProgressFinalAnimationListener(@NonNull ProgressFinalAnimationListener listener) {
        mProgressFinalAnimationListener = listener;
    }

    /**
     * Starts the indeterminate spinning progress circle.
     */
    public void startProgress() {
        mAnimState = ANIM_SHOWING;

        mAnimationIsPlaying = true;
        resetArcProperties();
        mRotateAnim.start();
        mGrowAnim.start();
        invalidate();
    }

    /**
     * Stops the indeterminate spinning progress circle.
     */
    public void stopProgress() {
        mAnimState = NO_ANIM;

        mAnimationIsPlaying = false;
        stopAnimators();
        resetArcProperties();
        invalidate();
    }

    /**
     * Resets the {@link FloatingActionButton} to its original icon and background and makes it
     * clickable again.
     */
    private void resetProgress() {
        mIsComplete = false;

        stopProgress();
        setupAnimations();
        fadeOut(true, true);
    }

    /**
     * Starts the final animation, i.e. makes the spinning progress circle determinate.
     */
    public void startProgressFinalAnimation() {
        if (!mAnimationIsPlaying || mCompleteAnim.isRunning()) {
            return;
        }

        mAnimState = FINAL_ANIM_SHOWING;
        mShowCompleteAnimOnNextCycle = true;
    }
}
