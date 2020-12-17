/*
 * Copyright 2020 Christopher Zaborsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unary.plasmafractalview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;

/**
 * A styleable image widget that generates a plasma cloud fractal effect using the random midpoint
 * displacement method.
 *
 * <p><strong>XML attributes</strong></p>
 * <p>The following optional attributes can be used to change the look and feel of the view:</p>
 * <pre>
 *   app:brightness="float"             // Brightness from -1 to +1
 *   app:fractal="string"               // Plasma fractal class to load "com.example.Classname"
 *   app:scaleType="center|fill|matrix" // Default type is "center"
 * </pre>
 * <p>See {@link R.styleable#PlasmaFractalView PlasmaFractalView Attributes}, {@link R.styleable#View View Attributes}</p>
 */
public class PlasmaFractalView extends View implements Runnable {

    private static final float VIEW_WIDTH = 256; // dp
    private static final float VIEW_HEIGHT = 256; // dp
    private static final float BRIGHTNESS = 0.0f;
    @StringRes
    private static final int PLASMA_FRACTAL = R.string.plasmafractalview_color_fractal;
    @ScaleType
    private static final int SCALE_TYPE = ScaleType.CENTER;

    private Thread mThread;
    private boolean mSurfaceReady;
    private float mBrightness;
    private AbstractPlasmaFractal mPlasmaFractal;
    @ScaleType
    private int mScaleType;
    private Paint mPaint;
    private Matrix mImageMatrix;
    private volatile Bitmap mImageBitmap;
    private OnFractalUpdateListener mOnFractalUpdateListener;

    /**
     * Annotation for the ScaleType typedef. The enumeration values are shared with a styleable XML
     * attribute of the same name.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ScaleType.CENTER, ScaleType.FILL, ScaleType.MATRIX})
    public @interface ScaleType {
        /**
         * Center crop for the scale type.
         */
        int CENTER = 0;
        /**
         * Fill to x/y for the scale type.
         */
        int FILL = 1;
        /**
         * Matrix used for the scale type.
         */
        int MATRIX = 2;
    }

    /**
     * Simple constructor to use when creating the view from code.
     *
     * @param context Context given for the view. This determines the resources and theme.
     */
    public PlasmaFractalView(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    /**
     * Constructor that is called when inflating the view from XML.
     *
     * @param context Context given for the view. This determines the resources and theme.
     * @param attrs   The attributes for the inflated XML tag.
     */
    public PlasmaFractalView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    /**
     * Constructor called when inflating from XML and applying a style.
     *
     * @param context      Context given for the view. This determines the resources and theme.
     * @param attrs        The attributes for the inflated XML tag.
     * @param defStyleAttr Default style attributes to apply to this view.
     */
    public PlasmaFractalView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    /**
     * Constructor that is used when given a default shared style.
     *
     * @param context      Context given for the view. This determines the resources and theme.
     * @param attrs        The attributes for the inflated XML tag.
     * @param defStyleAttr Default style attributes to apply to this view.
     * @param defStyleRes  Default style resource to apply to this view.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public PlasmaFractalView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Shared method to initialize the member variables from the XML and create the drawing objects.
     * Input values are checked for sanity.
     *
     * @param context      Context given for the view. This determines the resources and theme.
     * @param attrs        The attributes for the inflated XML tag.
     * @param defStyleAttr Default style attributes to apply to this view.
     * @param defStyleRes  Default style resource to apply to this view.
     */
    private void init(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray typedArray = context.getTheme()
                .obtainStyledAttributes(attrs, R.styleable.PlasmaFractalView, defStyleAttr, defStyleRes);

        String fractalClassName;

        try {
            mBrightness = typedArray.getFloat(R.styleable.PlasmaFractalView_brightness, BRIGHTNESS);
            fractalClassName = typedArray.getString(R.styleable.PlasmaFractalView_fractal);
            mScaleType = typedArray.getInt(R.styleable.PlasmaFractalView_scaleType, SCALE_TYPE);
        } finally {
            typedArray.recycle();
        }

        // Sanitize the input values
        mBrightness = Math.min(Math.max(mBrightness, -1), 1);

        // Assign a default if needed
        mPlasmaFractal = loadClass(fractalClassName != null
                ? fractalClassName : getResources().getString(PLASMA_FRACTAL), context, attrs);

        // Initialize the drawing objects
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        mImageMatrix = new Matrix();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mSurfaceReady = true;
        //startThread();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mSurfaceReady = false;
        stopThread();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        return Math.max(super.getSuggestedMinimumHeight(), dpToPixels(getContext(), VIEW_HEIGHT));
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        return Math.max(super.getSuggestedMinimumWidth(), dpToPixels(getContext(), VIEW_WIDTH));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        startThread();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mImageBitmap != null) {
            mPaint.setColorFilter(brightnessFilter(mBrightness));
            canvas.drawBitmap(mImageBitmap, scaleMatrix(mImageBitmap, getWidth(), getHeight(), mScaleType, mImageMatrix), mPaint);
        }
    }

    /**
     * Start a new thread using the current attributes. Only a single thread is needed.
     */
    protected void startThread() {
        if (mSurfaceReady) {
            stopThread();

            mThread = new Thread(this);
            mThread.start();
        }
    }

    /**
     * Stop any existing thread by sending an interrupt. Only a single thread is needed.
     */
    protected void stopThread() {
        if (mThread != null) mThread.interrupt();
    }

    @Override
    public void run() {
        // Null if broke or interrupted
        mImageBitmap = createFractal(mPlasmaFractal, getWidth(), getHeight());

        if (getHandler() == null) return;

        // Post updates to the UI
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                invalidate();
                onFractalUpdate(mImageBitmap);
            }
        });
    }

    /**
     * Notify the fractal update listener of any changes to the fractal image used by the view.
     *
     * @param bitmap Fractal bitmap or null.
     */
    public void onFractalUpdate(@Nullable Bitmap bitmap) {
        if (mOnFractalUpdateListener != null) {
            mOnFractalUpdateListener.onFractalCompleted(this, bitmap);
        }
    }

    /**
     * Utility method to create a fractal bitmap based on given size and available memory.
     *
     * @param plasmaFractal Plasma fractal generator.
     * @param width         Given width.
     * @param height        Given height.
     * @return Fractal bitmap or null.
     */
    @Nullable
    private static Bitmap createFractal(@Nullable AbstractPlasmaFractal plasmaFractal, int width, int height) {
        // Find the exponent in 2^n + 1
        int n = (int) (log2(Math.max(width, height)) + 0.5f);

        // Try smaller until successful
        if (plasmaFractal != null) {
            for (; n >= 0; n--) {
                try {
                    return plasmaFractal.generate(n);
                } catch (OutOfMemoryError e) {
                    // Stay in the for-loop
                    Log.e(PlasmaFractalView.class.getSimpleName(), "Creating bitmap", e);
                }
            }
        }

        return null;
    }

    /**
     * Utility method to create a LightingColorFilter that can be used to adjust brightness.
     *
     * @param brightness Brightness from -1 to 1.
     * @return The lighting filter.
     */
    @NonNull
    protected static LightingColorFilter brightnessFilter(float brightness) {
        int add = brightness > 0 ? (int) (brightness * 255) : 0x00;
        int mul = brightness < 0 ? (int) ((1 + brightness) * 255) : 0xFF;

        return new LightingColorFilter(mul << 16 | mul << 8 | mul, add << 16 | add << 8 | add);
    }

    /**
     * Utility method tp create a matrix configured for scaling a bitmap based on the ScaleType.
     *
     * @param bitmap      The image bitmap.
     * @param width       Given width.
     * @param height      Given height.
     * @param scaleType   Type of scaling to use.
     * @param imageMatrix External matrix.
     * @return Transforming matrix.
     */
    private static Matrix scaleMatrix(Bitmap bitmap, int width, int height, @ScaleType int scaleType, Matrix imageMatrix) {
        // Find the scale for x/y
        float sx = (float) width / (float) bitmap.getWidth();
        float sy = (float) height / (float) bitmap.getHeight();

        switch (scaleType) {
            case ScaleType.CENTER:
                sx = sy = Math.max(sx, sy);
                break;
            case ScaleType.FILL:
                // Nothing to do here
                break;
            case ScaleType.MATRIX:
                // Use an external matrix
                return imageMatrix;
        }

        // Find the delta for x/y
        float dx = (width - bitmap.getWidth() * sx) * 0.5f;
        float dy = (height - bitmap.getHeight() * sy) * 0.5f;

        Matrix matrix = new Matrix();
        matrix.setScale(sx, sy);
        matrix.postTranslate(dx, dy);

        return matrix;
    }

    /**
     * Utility method to load the given PlasmaFractal class and catch a deluge of exceptions.
     *
     * @param name    Class pathname.
     * @param context Context given for the view.
     * @param attrs   Inflated XML attributes.
     * @return Object or null.
     */
    @Nullable
    protected static AbstractPlasmaFractal loadClass(@NonNull String name, Context context, AttributeSet attrs) {
        AbstractPlasmaFractal plasmaFractal = null;
        String classPath = name.startsWith(".") ? context.getPackageName() + name : name;

        // Try to instantiate class
        try {
            Constructor<?> constructor = Class.forName(classPath).getConstructor(Context.class, AttributeSet.class);
            plasmaFractal = (AbstractPlasmaFractal) constructor.newInstance(context, attrs);
        } catch (Exception e) {
            Log.e(PlasmaFractalView.class.getSimpleName(), "Loading class", e);
        }

        return plasmaFractal;
    }

    /**
     * Utility method for a base 2 (binary) logarithmic function of a floating point number.
     *
     * @param value Given number.
     * @return The exponent.
     */
    private static float log2(float value) {
        return (float) (Math.log(value) / Math.log(2));
    }

    /**
     * Utility method to find the pixel resolution of a density pixel value.
     *
     * @param context Context given for the metrics. This determines the resources and theme.
     * @param dp      Density pixels to convert.
     * @return The pixel resolution.
     */
    private static int dpToPixels(Context context, @Dimension float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5);
    }

    /**
     * Utility method to find the density pixel value of a pixel resolution.
     *
     * @param context Context given for the metrics. This determines the resources and theme.
     * @param px      Pixel resolution to convert.
     * @return The density pixels.
     */
    @Dimension
    private static float pixelsToDp(Context context, int px) {
        return px / context.getResources().getDisplayMetrics().density;
    }

    /**
     * Utility method to find the preferred measurements of this view for the view parent.
     *
     * @param defaultSize Default size of the view.
     * @param measureSpec Constraints imposed by the parent.
     * @return Preferred size for this view.
     * @see View#getDefaultSize(int, int)
     */
    public static int getDefaultSize(int defaultSize, int measureSpec) {
        int mode = MeasureSpec.getMode(measureSpec);
        int size = MeasureSpec.getSize(measureSpec);

        switch (mode) {
            case MeasureSpec.EXACTLY:
                return size;
            case MeasureSpec.AT_MOST:
                return Math.min(size, defaultSize);
            case MeasureSpec.UNSPECIFIED:
            default:
                return defaultSize;
        }
    }

    /**
     * Get the brightness used when displaying a plasma cloud fractal. This property should not be
     * confused with image alpha.
     *
     * @return Display brightness.
     */
    public float getBrightness() {
        return mBrightness;
    }

    /**
     * Set the brightness used when displaying a plasma cloud fractal. This property should not be
     * confused with image alpha.
     *
     * @param brightness Display brightness.
     */
    public void setBrightness(float brightness) {
        mBrightness = brightness;
        invalidate();
    }

    /**
     * Get the plasma cloud fractal generator used to create the displayed image.
     *
     * @return The fractal generator.
     */
    @Nullable
    public AbstractPlasmaFractal getPlasmaFractal() {
        return mPlasmaFractal;
    }

    /**
     * Set the plasma cloud fractal generator used to create the displayed image.
     *
     * @param plasmaFractal The fractal generator.
     */
    public void setPlasmaFractal(@Nullable AbstractPlasmaFractal plasmaFractal) {
        mPlasmaFractal = plasmaFractal;
        startThread();
    }

    /**
     * Get the scale type used to display the image within the view. The enumeration values are
     * shared with a styleable XML attribute of the same name.
     *
     * @return The scale type.
     */
    public int getScaleType() {
        return mScaleType;
    }

    /**
     * Set the scale type used to display the image within the view. The enumeration values are
     * shared with a styleable XML attribute of the same name.
     *
     * @param scaleType The scale type.
     */
    public void setScaleType(int scaleType) {
        mScaleType = scaleType;
        invalidate();
    }

    /**
     * Get the transformation matrix used when the ScaleType is set to "matrix".
     *
     * @return Image matrix.
     */
    @NonNull
    public Matrix getImageMatrix() {
        return mImageMatrix;
    }

    /**
     * Set the transformation matrix used when the ScaleType is set to "matrix".
     *
     * @param imageMatrix Image matrix.
     */
    public void setImageMatrix(@NonNull Matrix imageMatrix) {
        mImageMatrix = imageMatrix;
        invalidate();
    }

    /**
     * Get the current plasma cloud fractal bitmap, without any scaling or brightness applied.
     *
     * @return Fractal bitmap or null.
     */
    @Nullable
    public Bitmap getImageBitmap() {
        return mImageBitmap;
    }

    /**
     * Set the current plasma cloud fractal bitmap, without any scaling or brightness applied.
     *
     * @param imageBitmap Fractal bitmap or null.
     */
    public void setImageBitmap(@Nullable Bitmap imageBitmap) {
        mImageBitmap = imageBitmap;
        invalidate();
    }

    /**
     * Get the fractal update listener. The interface is used to notify the client of any changes to
     * the fractal image used by the view.
     *
     * @return Update notification listener.
     */
    @Nullable
    public OnFractalUpdateListener getOnFractalUpdateListener() {
        return mOnFractalUpdateListener;
    }

    /**
     * Set the fractal update listener. The interface is used to notify the client of any changes to
     * the fractal image used by the view.
     *
     * @param onFractalUpdateListener Update notification listener.
     */
    public void setOnFractalUpdateListener(@Nullable OnFractalUpdateListener onFractalUpdateListener) {
        mOnFractalUpdateListener = onFractalUpdateListener;
    }
}