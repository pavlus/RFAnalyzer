package com.mantz_it.rfanalyzer.ui.util;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by Pavel on 02.04.2017.
 */

public class Toaster {
private final Context context;

public Toaster(Context context) {this.context = context;}

public void show(CharSequence text, int duration){
	Toast.makeText(context, text, duration).show();
}

public void show(int id, int duration){
	Toast.makeText(context, id, duration).show();
}

public void showLong(CharSequence text){
	show(text, Toast.LENGTH_LONG);
}
public void showLong(int id){
	show(id, Toast.LENGTH_LONG);
}

public void showShort(CharSequence text){
	show(text, Toast.LENGTH_SHORT);
}

public void showShort(int id){
	show(id, Toast.LENGTH_SHORT);
}


}
