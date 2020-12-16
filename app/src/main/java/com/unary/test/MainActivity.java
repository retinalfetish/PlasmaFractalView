package com.unary.test;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.unary.plasmafractalview.ColorPlasmaFractal;
import com.unary.plasmafractalview.PlasmaFractalView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PlasmaFractalView fractalView = findViewById(R.id.fractalview);

        // Adjust display brightness
        //fractalView.setBrightness(0.5f);

        // Change fractal deviation
        //fractalView.getPlasmaFractal().setDeviation(0.25f);

        // Use an external matrix
        //fractalView.setScaleType(PlasmaFractalView.ScaleType.MATRIX);
        //fractalView.getImageMatrix().setSkew(0.2f, 0.2f);

        // Firewater plasma fractal
        //fractalView.setPlasmaFractal(new ColorPlasmaFractal(this) {
        //    @Override
        //    protected float scaleFilter(float value, float min, float max) {
        //        return super.scaleFilter(value, min, 0.8f);
        //    }
        //});

        // Purple haze plasma fractal
        fractalView.setPlasmaFractal(new ColorPlasmaFractal(this) {
            @Override
            protected int colorFilter(float value) {
                return 0xFF | super.colorFilter(value);
            }
        });
    }
}