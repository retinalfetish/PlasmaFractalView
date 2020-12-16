# PlasmaFractalView
A styleable image widget that generates a plasma cloud fractal effect using the [random midpoint displacement](https://en.wikipedia.org/wiki/Diamond-square_algorithm) method, which is a precursor to the diamond-square algorithm.
## Screenshots
<img src="/art/screenshot-color.png" alt="Screenshot" height=600> <img src="/art/screenshot-grayscale.png" alt="Screenshot" height=600>
## Usage
The library is part of [JCenter](https://bintray.com/rogue/maven/com.unary:plasmafractalview) (a default repository) and can be included in your project by adding `implementation 'com.unary:plasmafractalview:1.0.0'` as a module dependency. The latest build can also be found at [JitPack](https://jitpack.io/#com.unary/plasmafractalview).
```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
This widget has a number of options that can be configured in both the XML and code. An example app is provided in the project repository to illustrate its use and the `AbstractPlasmaFractal` class.
```
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <com.unary.plasmafractalview.PlasmaFractalView
        android:id="@+id/fractalview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_gravity="center"
        app:decay="0.5"
        app:deviation="1.0"
        app:fractal="@string/plasmafractalview_color_fractal" />

</FrameLayout>
```
Changing fractals in XML:
```
app:fractal="@string/plasmafractalview_color_fractal"
app:fractal="@string/plasmafractalview_grayscale_fractal"
```
Anonymous class example:
```
fractalView.setPlasmaFractal(new ColorPlasmaFractal(this) {
    @Override
    protected int colorFilter(float value) {
        return 0xFF | super.colorFilter(value);
    }
});
```
The listener interface:
```
fractalView.setOnFractalUpdateListener(this);

@Override
public void onFractalCompleted(View view, Bitmap bitmap) { ... }
```
### XML attributes
The following optional attributes can be used to change the look and feel of the view:
```
app:brightness="float"             // Brightness from -1 to +1
app:decay="float"                  // Rate of decay, with a range of 0 to 1
app:deviation="float"              // Initial midpoint displacement
app:fractal="string"               // Plasma fractal class to load "com.example.Classname"
app:scaleType="center|fill|matrix" // Default type is "center"
```
