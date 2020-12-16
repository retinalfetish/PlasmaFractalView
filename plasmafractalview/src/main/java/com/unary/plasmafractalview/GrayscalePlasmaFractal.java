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
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Grayscale class for a plasma cloud fractal generator based on the random midpoint displacement
 * method, which is a precursor to the diamond-square algorithm.
 */
public class GrayscalePlasmaFractal extends AbstractPlasmaFractal {

    /**
     * Simple constructor to use when creating the view from code.
     *
     * @param context Context given for the view. This determines the resources and theme.
     */
    public GrayscalePlasmaFractal(@NonNull Context context) {
        super(context);
    }

    /**
     * Constructor that is called when inflating the view from XML.
     *
     * @param context Context given for the view. This determines the resources and theme.
     * @param attrs   The attributes for the inflated XML tag.
     */
    public GrayscalePlasmaFractal(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected float scaleFilter(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    @Override
    @ColorInt
    protected int colorFilter(float value) {
        return 0xFF << 24 | (int) (value * 255) << 16 | (int) (value * 255) << 8 | (int) (value * 255);
    }
}