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
import android.os.Looper;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Abstract class for a plasma cloud fractal generator based on the random midpoint displacement
 * method, which is a precursor to the diamond-square algorithm.
 *
 * <p><strong>XML attributes</strong></p>
 * <p>The following optional attributes can be used to adjust the generated fractal:</p>
 * <pre>
 *   app:decay="float"     // Rate of decay, with a range of 0 to 1
 *   app:deviation="float" // Initial midpoint displacement
 * </pre>
 * <p>See {@link R.styleable#PlasmaFractal PlasmaFractal Attributes}, {@link R.styleable#View View Attributes}</p>
 */
public abstract class AbstractPlasmaFractal {

    private static final float DEVIATION = 1.0f;
    private static final float DECAY = 0.5f;

    private float mDeviation;
    private float mDecay;
    private float[][] mHeightmap;

    /**
     * Simple constructor to use when creating the view from code.
     *
     * @param context Context given for the view. This determines the resources and theme.
     */
    public AbstractPlasmaFractal(@NonNull Context context) {
        init(context, null);
    }

    /**
     * Constructor that is called when inflating the view from XML.
     *
     * @param context Context given for the view. This determines the resources and theme.
     * @param attrs   The attributes for the inflated XML tag.
     */
    public AbstractPlasmaFractal(@NonNull Context context, @Nullable AttributeSet attrs) {
        init(context, attrs);
    }

    /**
     * Shared method to initialize the member variables from the XML and create the drawing objects.
     * Input values are checked for sanity.
     *
     * @param context Context given for the view. This determines the resources and theme.
     * @param attrs   The attributes for the inflated XML tag.
     */
    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        TypedArray typedArray = context.getTheme()
                .obtainStyledAttributes(attrs, R.styleable.PlasmaFractal, 0, 0);

        try {
            mDeviation = typedArray.getFloat(R.styleable.PlasmaFractal_deviation, DEVIATION);
            mDecay = typedArray.getFloat(R.styleable.PlasmaFractal_decay, DECAY);
        } finally {
            typedArray.recycle();
        }

        // Sanitize the input values
        mDeviation = Math.min(Math.max(mDeviation, 0), 1);
        mDecay = Math.min(Math.max(mDecay, 0), 1);
    }

    /**
     * Generate a plasma cloud fractal bitmap using the random midpoint displacement algorithm.
     * Bitmap size is based on the exponent given in 2^n + 1.
     *
     * @param n Number exponent.
     * @return Fractal bitmap.
     */
    @WorkerThread
    @Nullable
    public Bitmap generate(int n) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Invoked from main thread");
        }

        int size = (1 << n) + 1;

        // Can throw OutOfMemoryError
        mHeightmap = new float[size][size];
        int[] colors = new int[mHeightmap.length * mHeightmap[0].length];

        divide(0, 0, size - 1, mDeviation, displace(1), displace(1), displace(1), displace(1));
        convert(mHeightmap, colors);

        // Null for interrupted junk
        return Thread.currentThread().isInterrupted()
                ? null : Bitmap.createBitmap(colors, mHeightmap.length, mHeightmap[0].length, Bitmap.Config.ARGB_8888);
    }

    /**
     * Recursive method to divide up a heightmap of 2^n + 1 and populate its vertices accordingly.
     * Points and recursion calls are assigned clockwise.
     *
     * @param x    The X coordinate.
     * @param y    The Y coordinate.
     * @param size Working size (length - 1).
     * @param r    Current working deviation.
     * @param a    Upper left value.
     * @param b    Upper right value.
     * @param c    Lower right value.
     * @param d    Lower left value.
     */
    private void divide(int x, int y, int size, float r, float a, float b, float c, float d) {
        int half = size / 2;

        // Save the corner values
        mHeightmap[x][y] = a;
        mHeightmap[x + size][y] = b;
        mHeightmap[x + size][y + size] = c;
        mHeightmap[x][y + size] = d;

        // Check if we're finished
        if (size < 2 || Thread.currentThread().isInterrupted()) return;

        // Find the edge values
        float p0 = average(a, b);
        float p1 = average(b, c);
        float p2 = average(c, d);
        float p3 = average(d, a);

        // Find the midpoint value
        float m = average(p0, p1, p2, p3) + displace(r);

        // Divide up the square again
        divide(x, y, half, r * mDecay, a, p0, m, p3);
        divide(x + half, y, half, r * mDecay, p0, b, p1, m);
        divide(x + half, y + half, half, r * mDecay, m, p1, c, p2);
        divide(x, y + half, half, r * mDecay, p3, m, p2, d);
    }

    /**
     * Normalize and convert a floating point heightmap matrix into an array of int color values.
     *
     * @param heightmap Heightmap array.
     * @param colors    Colors array.
     */
    private void convert(float[][] heightmap, int[] colors) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        int k = 0;

        // Find the min and max values
        for (int i = 0; i < heightmap.length; i++) {
            for (int j = 0; j < heightmap[0].length; j++) {
                min = Math.min(min, heightmap[i][j]);
                max = Math.max(max, heightmap[i][j]);
            }
        }

        // Convert the heightmap
        for (int i = 0; i < heightmap.length; i++) {
            for (int j = 0; j < heightmap[0].length; j++) {
                colors[k++] = colorFilter(scaleFilter(heightmap[i][j], min, max));
            }
        }
    }

    /**
     * Math function to scale individual heightmap pixels so they fall within 0 and 1.
     *
     * @param value Heightmap value.
     * @param min   Minimum range.
     * @param max   Maximum range.
     * @return Scale value.
     */
    protected abstract float scaleFilter(float value, float min, float max);

    /**
     * Math function used to convert individual heightmap pixels into int color values.
     *
     * @param value Heightmap value.
     * @return Color value.
     */
    @ColorInt
    protected abstract int colorFilter(float value);

    /**
     * Utility method to find the average of a variable amount of floating point numbers.
     *
     * @param numbers Variable arguments.
     * @return Average number.
     */
    protected static float average(float... numbers) {
        float sum = 0;

        for (float number : numbers) {
            sum += number;
        }

        return sum / numbers.length;
    }

    /**
     * Utility method to generate a random floating point number within the given deviation.
     *
     * @param value Given deviation.
     * @return Random deviation.
     */
    protected static float displace(float value) {
        return (float) (Math.random() - 0.5f) * value;
    }

    /**
     * Get the deviation used when generating a plasma cloud fractal. Along with the decay it
     * determines the turbulence of the heightmap.
     *
     * @return Maximum deviation.
     */
    public float getDeviation() {
        return mDeviation;
    }

    /**
     * Set the deviation used when generating a plasma cloud fractal. Along with the decay it
     * determines the turbulence of the heightmap.
     *
     * @param deviation Maximum deviation.
     */
    public void setDeviation(float deviation) {
        mDeviation = deviation;
    }

    /**
     * Get the decay value used when generating a plasma cloud fractal. Along with the deviation it
     * determines the turbulence of the heightmap.
     *
     * @return Decay value.
     */
    public float getDecay() {
        return mDecay;
    }

    /**
     * Set the decay value used when generating a plasma cloud fractal. Along with the deviation it
     * determines the turbulence of the heightmap.
     *
     * @param decay Decay value.
     */
    public void setDecay(float decay) {
        mDecay = decay;
    }
}