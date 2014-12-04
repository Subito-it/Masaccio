package it.subito.masaccio.demo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import com.nostra13.universalimageloader.cache.memory.impl.WeakMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.download.BaseImageDownloader;
import com.nostra13.universalimageloader.core.process.BitmapProcessor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.Executors;

import it.subito.masaccio.MasaccioImageView;
import it.subito.masaccio.MasaccioImageView.MasaccioFaceDetector;

public class DemoActivity extends Activity {

    public static final int MAX_HEIGHT = 1280;

    public static final int MAX_WIDTH = 1920;

    public static final int MIN_HEIGHT = 320;

    public static final int MIN_WIDTH = 480;

    private final Random mRandom = new Random();

    private MasaccioImageView mMasaccioImageView;

    private ImageView mPreviewImageView;

    private static DisplayImageOptions getProcessorDisplayImageOptions(
            final BitmapProcessor processor) {

        final DisplayImageOptions.Builder defaultOptionsBuilder = new DisplayImageOptions.Builder();

        return defaultOptionsBuilder.imageScaleType(ImageScaleType.NONE)
                                    .postProcessor(processor)
                                    .build();
    }

    private static ImageLoaderConfiguration getStandardOptions(final Context context,
            final BitmapProcessor processor) {

        final ImageLoaderConfiguration.Builder config =
                new ImageLoaderConfiguration.Builder(context);

        config.defaultDisplayImageOptions(getProcessorDisplayImageOptions(processor));

        config.memoryCache(new WeakMemoryCache());
        config.taskExecutor(Executors.newCachedThreadPool());
        config.threadPriority(Thread.MIN_PRIORITY);
        config.imageDownloader(new OkHttpDownloader(context));

        return config.build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout);

        mPreviewImageView = (ImageView) findViewById(R.id.preview_image);
        mMasaccioImageView = (MasaccioImageView) findViewById(R.id.masaccio_view);

        findViewById(R.id.download_button).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                onDownload();
            }
        });

        findViewById(R.id.rotate_button).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                onRotate();
            }
        });

        // register the processor so to make face detection happen in the background loading thread
        ImageLoader.getInstance()
                   .init(getStandardOptions(this, new FaceDetectionProcessor(
                           MasaccioImageView.getFaceDetector())));
    }

    private void onDownload() {

        ImageLoader.getInstance().cancelDisplayTask(mMasaccioImageView);

        final int width = mRandom.nextInt(MAX_WIDTH - MIN_WIDTH) + MIN_WIDTH;
        final int height = mRandom.nextInt(MAX_HEIGHT - MIN_HEIGHT) + MIN_HEIGHT;

        ImageLoader.getInstance()
                   .displayImage("http://lorempixel.com/" + width + "/" + height + "/people",
                                 mMasaccioImageView, new ImageLoadingListener() {

                               @Override
                               public void onLoadingStarted(final String imageUri,
                                       final View view) {

                               }

                               @Override
                               public void onLoadingFailed(final String imageUri, final View view,
                                       final FailReason failReason) {

                               }

                               @Override
                               public void onLoadingComplete(final String imageUri, final View view,
                                       final Bitmap loadedImage) {

                                   mPreviewImageView.setImageBitmap(loadedImage);
                               }

                               @Override
                               public void onLoadingCancelled(final String imageUri,
                                       final View view) {

                               }
                           });
    }

    private void onRotate() {

        final MasaccioImageView masaccioImageView = mMasaccioImageView;
        final int width = masaccioImageView.getWidth();
        final int height = masaccioImageView.getHeight();

        // swap width and height
        final LayoutParams layoutParams = masaccioImageView.getLayoutParams();
        layoutParams.width = height;
        layoutParams.height = width;
        masaccioImageView.setLayoutParams(layoutParams);
    }

    private static class FaceDetectionProcessor implements BitmapProcessor {

        private final MasaccioFaceDetector mDetector;

        public FaceDetectionProcessor(final MasaccioFaceDetector detector) {

            mDetector = detector;
        }

        @Override
        public Bitmap process(final Bitmap bitmap) {

            mDetector.process(bitmap);

            return bitmap;
        }
    }

    private static class OkHttpDownloader extends BaseImageDownloader {

        private final OkHttpClient mClient = new OkHttpClient();

        public OkHttpDownloader(final Context context) {

            super(context);
        }

        public OkHttpDownloader(final Context context, final int connectTimeout,
                final int readTimeout) {

            super(context, connectTimeout, readTimeout);
        }

        @Override
        protected InputStream getStreamFromNetwork(final String imageUri, final Object extra) throws
                IOException {

            final Request request = new Request.Builder().url(imageUri).build();

            return mClient.newCall(request).execute().body().byteStream();
        }
    }
}
