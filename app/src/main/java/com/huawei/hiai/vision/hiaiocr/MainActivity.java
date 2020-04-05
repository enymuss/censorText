package com.huawei.hiai.vision.hiaiocr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.huawei.hiai.vision.common.ConnectionCallback;
import com.huawei.hiai.vision.common.VisionBase;
import com.huawei.hiai.vision.text.TextDetector;
import com.huawei.hiai.vision.visionkit.common.BoundingBox;
import com.huawei.hiai.vision.visionkit.common.Frame;
import com.huawei.hiai.vision.visionkit.text.Text;
import com.huawei.hiai.vision.visionkit.text.TextBlock;
import com.huawei.hiai.vision.visionkit.text.TextConfiguration;
import com.huawei.hiai.vision.visionkit.text.TextDetectType;
import com.huawei.hiai.vision.visionkit.text.TextElement;
import com.huawei.hiai.vision.visionkit.text.TextLine;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static String TAG = "HWOCR";
    private TextView et;
    private ImageView iv;
    private Button btn4ocr;
    private Button btnShare;
    private Button btnSave;
    private Paint paint;
    private Uri savedImage;
    private CheckBox checkBlock;
    private CheckBox checkLine;
    private CheckBox checkChar;

    private static final int REQUEST_CHOOSE_PHOTO_CODE4OCR = 2;
    private TextDetector textDetector;
    private Bitmap bmDraw;

    private Gson gson;

    private String result_final = "init";

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    String data = (String) msg.obj;
                    ClipboardManager cmb = (ClipboardManager) getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    cmb.setText(data);
                    iv.setImageBitmap(bmDraw);
                    Log.d(TAG, "handleMessage:  | " + data.startsWith("{\"resultCode\":200") + "=" + data.startsWith("{\"resultCode\":201") + "re" + (result_final.length() == 0));
                    if (data.startsWith("{\"resultCode\":200") || data.startsWith("{\"resultCode\":201") || result_final.length() == 0) {
                        Log.i(TAG, "Input pictures are not supported at this time, so stay tuned.");
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        gson = new Gson();
        /*get connection to service*/
        VisionBase.init(getApplicationContext(), new ConnectionCallback() {
            @Override
            public void onServiceConnect() {
                Log.e(TAG, "HwVisionManager onServiceConnect OK.");
            }

            @Override
            public void onServiceDisconnect() {
                Log.e(TAG, "HwVisionManager onServiceDisconnect OK.");
            }
        });
        bmDraw = null;

        //create TextDetector
        textDetector = new TextDetector(MainActivity.this);

        btn4ocr = (Button) findViewById(R.id.btn4ocr);
        btn4ocr.setOnClickListener(this);
        btnShare = (Button) findViewById(R.id.btnShare);
        btnShare.setOnClickListener(this);
        btnSave = (Button) findViewById(R.id.btnSave);
        btnSave.setOnClickListener(this);

        checkBlock = (CheckBox) findViewById(R.id.checkBlock);
        checkBlock.setOnClickListener(this);
        checkLine = (CheckBox) findViewById(R.id.checkLine);
        checkLine.setOnClickListener(this);
        checkChar = (CheckBox) findViewById(R.id.checkChar);
        checkChar.setOnClickListener(this);

        checkBlock.setChecked(true);
        iv = (ImageView) findViewById(R.id.imageView);



        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        Log.e(TAG, "OCR Activity onCreate() Done.");

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "*****onNewIntent()*****");
        Log.i(TAG, "onNewIntent：" + getClass().getSimpleName() + " TaskId: " + getTaskId() + " hasCode:" + this.hashCode());
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //WRITE_EXTERNAL_STORAGE permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
                Log.e(TAG, "data == null");
                return;
            }
            final Uri selectedImage = data.getData();
            Log.e(TAG, "select uri:" + selectedImage.toString());


//            Bitmap imageBitmap = null;
//            try {
//                imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//            iv.setImageBitmap(imageBitmap);

            /*start OCR in a new thread*/
            new Thread(new Runnable() {
                @Override
                public void run() {
                    go(selectedImage);
                }
            }) {
            }.start();
        }
    }

    @Override
    protected void onDestroy() {
        //unbindService(mConnection);
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        MainActivity.this.requestPermission();
        int requestCode;
        switch (view.getId()) {
            case R.id.btn4ocr:
                result_final = "init";
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                requestCode = REQUEST_CHOOSE_PHOTO_CODE4OCR;
                startActivityForResult(intent, requestCode);
                break;
            case R.id.btnShare:
                Intent share = new Intent(Intent.ACTION_SEND);
                String savedFile = saveImageFile(bmDraw, "/CensorText");

                Uri imageUri = FileProvider.getUriForFile(getApplicationContext(),
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        new File(savedFile));

                share.setType("image/*");
                share.putExtra(Intent.EXTRA_STREAM, imageUri);
                startActivity(Intent.createChooser(share, "Share Image"));
                break;
            case R.id.btnSave:
                saveImageFile(bmDraw, "/CensorText");
                break;
            case R.id.checkBlock:
                if (checkBlock.isChecked()) {
                    checkChar.setChecked(false);
                    checkLine.setChecked(false);
                }
                break;

            case R.id.checkLine:
                if (checkLine.isChecked()) {
                    checkBlock.setChecked(false);
                    checkChar.setChecked(false);
                }
                break;

            case R.id.checkChar:
                if (checkChar.isChecked()) {
                    checkBlock.setChecked(false);
                    checkLine.setChecked(false);
                }
                break;

            default:
                break;
        }
        if (view.getId() != R.id.btn4ocr && null != savedImage) {
            /*start OCR in a new thread*/
            new Thread(new Runnable() {
                @Override
                public void run() {
                    go(savedImage);
                }
            }) {
            }.start();
        }
    }

    public String saveImageFile(Bitmap image, String folder){

        String now = Long.toString(new Date().getTime());

        File imageFile = new File( Environment.getExternalStorageDirectory() + folder + "/");
        if (!imageFile.exists()) {
            imageFile.mkdirs();
        }

        File imageName = new File(imageFile, now + ".jpg" );

        try {
            FileOutputStream outputStream = new FileOutputStream(imageName);
            image.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(imageName);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);

        return imageName.toString();
    }

    private BoundingBox covertPointsToBoundingBox(Point[] cornerPoints) {
        int left = cornerPoints[0].x;
        int top = cornerPoints[0].y;
        int width = cornerPoints[2].x - cornerPoints[0].x;
        int height = cornerPoints[2].y - cornerPoints[0].y;

        final BoundingBox boundingBox = new BoundingBox(left, top, width, height);
        return boundingBox;
    }

    private String putTextLine(TextBlock block, Canvas canvas) {
        if (null == block) {
            return "";
        }
        paint.setStrokeWidth(4);
        paint.setColor(Color.BLUE);
        block.setBoundingBox(covertPointsToBoundingBox(block.getCornerPoints()));
        canvas.drawRect(toRect(block.getBoundingBox()), paint);
        if (null != block.getTextLines()) {
            for (TextLine line : block.getTextLines()) {
                paint.setStrokeWidth(2);
                paint.setColor(Color.GREEN);
                line.setLineRect(covertPointsToBoundingBox(line.getCornerPoints()));
                canvas.drawRect(toRect(line.getLineRect()), paint);
                List<TextElement> elements = line.getElements();
                if (null != elements) {
                    for (TextElement element : elements) {
                        paint.setStrokeWidth(2);
                        paint.setColor(Color.GREEN);
                        Rect eRect = toRect(element.getElementRect());
                        canvas.drawRect(eRect, paint);
                    }
                }
            }
        }

        return block.getValue();
    }

    private Rect toRect(BoundingBox box) {
        return new Rect(box.getLeft(), box.getTop(), box.getLeft() + box.getWidth(), box.getTop() + box.getHeight());
    }

    private void go(Uri selectedImage) {
        Log.e(TAG, "OCR GO1:" + selectedImage.toString());
        savedImage = selectedImage;
        String[] pathColumn = {MediaStore.Images.Media.DATA};

        //get image by Uri
        Cursor cursor = getContentResolver().query(selectedImage, pathColumn, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(pathColumn[0]);
        String picturePath = cursor.getString(columnIndex);  //Get photo path
        cursor.close();

        Bitmap bm = BitmapFactory.decodeFile(picturePath);
        int randValue = (int) (500 * Math.random());
        int x = (int) (bm.getWidth() * Math.random());
        int y = (int) (bm.getHeight() * Math.random());
        int left = Math.max(0, x - randValue);
        int right = Math.min(bm.getWidth(), x + randValue);
        int top = Math.max(0, y - randValue);
        int bottom = Math.min(bm.getHeight(), y + randValue);

        Rect roi = new Rect(left, top, right, bottom);
        int level = checkChar.isChecked() ? TextConfiguration.TEXT_LEVAL_CHAR : checkLine.isChecked() ? TextConfiguration.TEXT_LEVAL_LINE : TextConfiguration.TEXT_LEVAL_BLOCK;

        // prepare image
        Frame frame = new Frame();
        frame.setBitmap(bm);

        // prepare configuration for ocr
        TextConfiguration config = new TextConfiguration();
        config.setEngineType(TextDetectType.TYPE_TEXT_DETECT_FOCUS_SHOOT);
        config.setLevel(level);

        // set configuration to textDetector
        textDetector.setTextConfiguration(config);
        long t0 = System.currentTimeMillis();

        // run detect for textDetector
        JSONObject jsonResult = textDetector.detect(frame, null);

        //convert result
        Text text = textDetector.convertResult(jsonResult);
        long t1 = System.currentTimeMillis();
        Log.e(TAG, "ocrRes1:" + jsonResult.toString() + "text:" + text);
        if (null == text) {
            bmDraw = bm;
            Message msg = new Message();
            msg.what = 0;
            msg.obj = jsonResult.toString();
            mHandler.sendMessage(msg);
            return;
        }
        List<TextBlock> contents = text.getBlocks();
        if (null == contents) {
            bmDraw = bm;
            Message msg = new Message();
            msg.what = 0;
            msg.obj = jsonResult.toString();
            mHandler.sendMessage(msg);
            return;
        }
        // draw rects in a new bitmap
        bmDraw = bm.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bmDraw);

        Log.e(TAG, "lines.length:" + contents.size());

        result_final = "";
        for (int i = 0; i < contents.size(); i += 1) {
            String res_str = putTextLine(contents.get(i), canvas);
            if (!res_str.isEmpty()) {
                result_final += res_str + "\n";
            }
        }
        Message msg = new Message();
        msg.what = 0;
        msg.obj = new Long(t1 - t0).toString() + "ms chars：" + result_final.length() + "  " + String.format("%.2f", (t1 - t0) * 1.0 / result_final.length()) + "ms/char" + "\n" + result_final;
        mHandler.sendMessage(msg);
    }
}
