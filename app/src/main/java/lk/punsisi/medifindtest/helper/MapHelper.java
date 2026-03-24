package lk.punsisi.medifindtest.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public class MapHelper {

    public static BitmapDescriptor getCustomMarkerIcon(Context context, int vectorResId, int colorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        if (vectorDrawable == null) return BitmapDescriptorFactory.defaultMarker();

        vectorDrawable.setTint(ContextCompat.getColor(context, colorResId));

        int width = vectorDrawable.getIntrinsicWidth() * 2;
        int height = vectorDrawable.getIntrinsicHeight() * 2;
        vectorDrawable.setBounds(0, 0, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public static BitmapDescriptor getPngMarkerIcon(Context context, int drawableResId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableResId);
        if (drawable == null) return BitmapDescriptorFactory.defaultMarker();


        int width = 200;
        int height = 200;

        drawable.setBounds(0, 0, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
}