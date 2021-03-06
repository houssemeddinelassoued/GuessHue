package com.bestteamathackrice.guesshue;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import mtree.DistanceFunction;
import mtree.MTree;

/**
 * Activity for taking and approving images.
 */
public class Approval extends GlobalSettingsActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private int[] imageArray;

    private ColorDiff colorDiff = new ColorDiff();

    private int goalColor;

    private int actualColor;

    private boolean takenPicture = false;

    private TextView yourColorText;

    private ImageView image;

    private long current_time;

    private TextView time_display;

    private CountDownTimer countdown;

    private class ColorDiff implements DistanceFunction<Integer> {

        @Override
        public double calculate(Integer p1, Integer p2) {
            int r1 = (p1 & 0xFF0000) >> 16;
            int g1 = (p1 & 0x00FF00) >> 8;
            int b1 = p1 & 0x0000FF;

            int r2 = (p2 & 0xFF0000) >> 16;
            int g2 = (p2 & 0x00FF00) >> 8;
            int b2 = p2 & 0x0000FF;

            return Math.sqrt((r1 - r2) * (r1 - r2) +
                    (g1 - g2) * (g1 - g2) +
                    (b1 - b2) * (b1 - b2));
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_approval);

        yourColorText = (TextView) findViewById(R.id.your_color_text);
        yourColorText.setTypeface(type);
        yourColorText.setVisibility(View.GONE);

        time_display = (TextView) findViewById(R.id.count_down_text_approval);
        time_display.setTypeface(type);

        current_time = (long) getIntent().getExtras().get("time_left");
        goalColor = (int) getIntent().getExtras().get("goal_color");

        image = (ImageView) findViewById(R.id.user_color);
        image.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.guesshue2));
        countdown = new CountDownTimer(current_time, 1000) {

            public void onTick(long millisUntilFinished) {
                current_time = millisUntilFinished;
                time_display.setText("TIME LEFT: " + current_time / 1000);
            }

            public void onFinish() {
                time_display.setText("OUT OF TIME");
                DataMule.totalRound += 1;
                dispatchScoreHoldingIntent(0);
            }

        }.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button bottomButton = (Button) findViewById(R.id.use_photo);
        if (!takenPicture) {
            bottomButton.setVisibility(View.GONE);
        } else {
            bottomButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_approval, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void takePhoto(View view) {
        dispatchTakePictureIntent();
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            intArrayFromBitmap(imageBitmap);
            takenPicture = true;
            actualColor = getClosestColor(imageArray, goalColor);
            actualColor |= 0xFF000000;
            image.setImageBitmap(null);
            image.setBackgroundColor(actualColor);
            yourColorText.setVisibility(View.VISIBLE);
        }
    }

    public int getClosestColor(int[] pixels, int goalColor) {

        MTree<Integer> mtree = new MTree<>(colorDiff, null);
        for (int pixel : pixels) {
            mtree.add(pixel);
        }
        int r = 0;
        int g = 0;
        int b = 0;
        int num = 0;
        for (MTree<Integer>.ResultItem resultItem :
                mtree.getNearestByLimit(goalColor, pixels.length / 100)) {
            num++;
            int cur = resultItem.data;
            r += ((cur & 0xFF0000) >> 16);
            g += ((cur & 0x00FF00) >> 8);
            b += (cur & 0x0000FF);
        }
        r /= num;
        g /= num;
        b /= num;
        r &= 0xFF;
        g &= 0xFF;
        b &= 0xFF;
        r <<= 16;
        g <<= 8;
        return r | g | b;
    }

    private void intArrayFromBitmap(Bitmap bitmap) {
        imageArray = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(imageArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(),
                bitmap.getHeight());
    }

    public int getScore(int inputColor, int goalColor) {
        double maxDist = -1;
        for (int r = 0; r <= 1; r++) {
            for (int g = 0; g <= 1; g++) {
                for (int b = 0; b <= 1; b++) {
                    maxDist = Math.max(colorDiff.calculate(goalColor, (r * 0xFF0000) |
                            (g * 0x00FF00) | (b * 0x0000FF)), maxDist);
                }
            }
        }

        double diff =  colorDiff.calculate(inputColor, goalColor);
        diff = maxDist - diff;
        diff /= maxDist;

        diff -= .5;
        diff = Math.max(0, diff);
        diff *= 2.0;

        diff = (int) (diff*100);
        return (int) diff*10;
    }

    public void goToScore(View view) {
        if (takenPicture) {
            countdown.cancel();
            int score = getScore(actualColor, goalColor);
            DataMule.totalRound += 1;
            DataMule.totalScore += score;
            dispatchScoreHoldingIntent(score);
        }
    }

    private void dispatchScoreHoldingIntent(int score) {
        Intent scoreHoldingIntent = new Intent(this, Score.class);
        scoreHoldingIntent.putExtra("round_score", score);
        startActivity(scoreHoldingIntent);
    }
}


