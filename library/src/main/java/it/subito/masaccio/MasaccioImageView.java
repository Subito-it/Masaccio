/**
 * Copyright (C) 2014 Subito.it S.r.l (www.subito.it)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.subito.masaccio;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class MasaccioImageView extends ImageView {

    private static final float FACE_POSITION_RATIO_X = 0.5f;

    private static final float FACE_POSITION_RATIO_Y = 0.5f;

    private static final Object sMutex = new Object();

    private static DefaultMasaccioFaceDetector sFaceDetector;

    private final Matrix mAnimMatrix = new Matrix();

    private long mAnimationDuration;

    private boolean mAutoFaceDetection;

    private CropRunnable mCropRunnable;

    private Face[] mDetectedFaces;

    private long mDuration;

    private float[] mEndCoeffs;

    private float mEndScale;

    private float mEndX;

    private float mEndY;

    private Interpolator mInterpolator;

    private Handler mMessageHandler;

    private float mOffsetX = -1;

    private float mOffsetY = -1;

    private ScaleType mOriginalScaleType;

    private float[] mStartCoeffs;

    private float mStartScale;

    private long mStartTime;

    private float mStartX;

    private float mStartY;

    public MasaccioImageView(final Context context) {

        super(context);
        init(null, 0);
    }

    public MasaccioImageView(final Context context, final AttributeSet attrs) {

        super(context, attrs);
        init(attrs, 0);
    }

    public MasaccioImageView(final Context context, final AttributeSet attrs, final int defStyle) {

        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public static MasaccioFaceDetector getFaceDetector() {

        synchronized (sMutex) {

            if (sFaceDetector == null) {

                sFaceDetector = new DefaultMasaccioFaceDetector();
            }
        }

        return sFaceDetector;
    }

    public void resetOffset() {

        mOffsetX = -1;
        mOffsetY = -1;

        applyCrop();
    }

    public void setAutoFaceDetection(final boolean enabled) {

        mAutoFaceDetection = enabled;

        if (enabled) {

            // Force instantiation
            getFaceDetector();
        }

        applyCrop();
    }

    public void setFaces(final Face[] faces) {

        if ((faces != null) && (faces.length > 0)) {

            mDetectedFaces = faces;

        } else {

            mDetectedFaces = null;
        }

        applyCrop();
    }

    @Override
    public void setImageDrawable(final Drawable drawable) {

        super.setImageDrawable(drawable);

        if (drawable == null) {

            setImageMatrix(new Matrix());

            return;
        }

        if (drawable instanceof BitmapDrawable) {

            getDetectedFaces(((BitmapDrawable) drawable).getBitmap());
        }

        cropImage(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    @Override
    public void setImageBitmap(final Bitmap bitmap) {

        super.setImageBitmap(bitmap);

        if (bitmap == null) {

            setImageMatrix(new Matrix());

            return;
        }

        getDetectedFaces(bitmap);

        cropImage(bitmap.getWidth(), bitmap.getHeight());
    }

    @Override
    public void setScaleType(final ScaleType scaleType) {

        super.setScaleType(scaleType);

        mOriginalScaleType = getScaleType();
    }

    @Override
    protected void onDraw(final Canvas canvas) {

        final long now = System.currentTimeMillis();

        final long startTime = mStartTime;
        final long duration = mDuration;
        final long endTime = startTime + duration;

        if ((now >= startTime) && (now < endTime)) {

            final Interpolator interpolator = mInterpolator;

            final float[] coeffs = new float[9];

            for (int i = 0; i < 9; i++) {

                final float interpolation =
                        interpolator.getInterpolation((float) (now - startTime) / duration);

                final float start = mStartCoeffs[i];

                coeffs[i] = start + (interpolation * (mEndCoeffs[i] - start));
            }

            final Matrix matrix = mAnimMatrix;

            matrix.setValues(coeffs);

            setImageMatrix(matrix);

        } else if ((startTime > 0) && (now >= endTime)) {

            mStartTime = 0;

            final Matrix matrix = mAnimMatrix;

            matrix.setValues(mEndCoeffs);

            setImageMatrix(matrix);
        }

        super.onDraw(canvas);
    }

    public void setOffsetX(final float offset) {

        mOffsetX = Math.min(1f, Math.abs(offset));

        applyCrop();
    }

    public void setOffsetY(final float offset) {

        mOffsetY = Math.min(1f, Math.abs(offset));

        applyCrop();
    }

    public void setOffsets(final float offsetX, final float offsetY) {

        mOffsetX = Math.min(1f, Math.abs(offsetX));
        mOffsetY = Math.min(1f, Math.abs(offsetY));

        applyCrop();
    }

    private void applyCrop() {

        setImageDrawable(getDrawable());
    }

    private void cropImage(final float originalImageWidth, final float originalImageHeight) {

        final Handler messageHandler = mMessageHandler;

        if (messageHandler == null) {

            // We can't do anything right now.
            return;
        }

        if (mCropRunnable != null) {

            messageHandler.removeCallbacks(mCropRunnable);
        }

        if ((!mAutoFaceDetection && (mOffsetX < 0) && (mOffsetY < 0)) || (originalImageWidth <= 0)
                || (originalImageHeight <= 0)) {

            final ScaleType scaleType = super.getScaleType();
            final ScaleType originalScaleType = mOriginalScaleType;

            if (scaleType != originalScaleType) {

                super.setScaleType(originalScaleType);
            }

            return;
        }

        mCropRunnable = new CropRunnable(originalImageWidth, originalImageHeight);

        if (Looper.getMainLooper() == Looper.myLooper()) {

            mCropRunnable.run();

        } else {

            messageHandler.post(mCropRunnable);
        }
    }

    private void getDefaultOffsets(final float[] offsets, final float maxOffsetX,
            final float maxOffsetY) {

        final float offsetX = mOffsetX;
        final float offsetY = mOffsetY;

        offsets[0] = ((offsetX >= 0) ? offsetX : 0.5f) * maxOffsetX;
        offsets[1] = ((offsetY >= 0) ? offsetY : 0.5f) * maxOffsetY;
    }

    private void getDetectedFaces(final Bitmap bitmap) {

        if (bitmap == null) {

            // Do nothing
            return;
        }

        final DefaultMasaccioFaceDetector faceDetector = sFaceDetector;

        if (faceDetector != null) {

            if (mAutoFaceDetection) {

                mDetectedFaces = faceDetector.process(bitmap);

            } else {

                mDetectedFaces = faceDetector.getFaces(bitmap);
            }
        }
    }

    private void getFaceOffsets(final Face[] faces, final float[] offsets, final float scaleFactor,
            final float width, final float height, final float maxOffsetX, final float maxOffsetY) {

        try {

            Face bestFace = null;

            float maxConfidence = 0;

            for (final Face face : faces) {

                final float faceConfidence = face.confidence();

                if (faceConfidence > maxConfidence) {

                    maxConfidence = faceConfidence;
                    bestFace = face;
                }
            }

            if (bestFace == null) {

                getDefaultOffsets(offsets, maxOffsetX, maxOffsetY);

                return;
            }

            final PointF midPoint = new PointF();

            bestFace.getMidPoint(midPoint);

            final float scaledOffsetX =
                    (midPoint.x * scaleFactor) - ((width - maxOffsetX) * FACE_POSITION_RATIO_X);

            final float scaledOffsetY =
                    (midPoint.y * scaleFactor) - ((height - maxOffsetY) * FACE_POSITION_RATIO_Y);

            if (maxOffsetX >= 0) {

                offsets[0] = Math.min(Math.max(0, scaledOffsetX), maxOffsetX);

            } else {

                offsets[0] = scaledOffsetX;
            }

            if (maxOffsetY >= 0) {

                offsets[1] = Math.min(Math.max(0, scaledOffsetY), maxOffsetY);

            } else {

                offsets[1] = scaledOffsetY;
            }


        } catch (final Exception e) {

            getDefaultOffsets(offsets, maxOffsetX, maxOffsetY);
        }
    }

    private Matrix getOriginalMatrix(final float originalImageWidth,
            final float originalImageHeight) {

        final float frameWidth = getWidth();
        final float frameHeight = getHeight();

        final float fitHorizontallyScaleFactor = frameWidth / originalImageWidth;
        final float fitVerticallyScaleFactor = frameHeight / originalImageHeight;

        final float minScaleFactor = Math.min(fitHorizontallyScaleFactor, fitVerticallyScaleFactor);

        final float minOffsetX = frameWidth - (originalImageWidth * minScaleFactor);
        final float minOffsetY = frameHeight - (originalImageHeight * minScaleFactor);

        final Matrix matrix = new Matrix();

        switch (mOriginalScaleType) {

            case CENTER:

                matrix.postTranslate((frameWidth - originalImageWidth) / 2,
                                     (frameHeight - originalImageHeight) / 2);

                break;

            case CENTER_CROP:

                final float maxScaleFactor =
                        Math.max(fitHorizontallyScaleFactor, fitVerticallyScaleFactor);

                final float maxOffsetX = frameWidth - (originalImageWidth * maxScaleFactor);
                final float maxOffsetY = frameHeight - (originalImageHeight * maxScaleFactor);

                matrix.setScale(maxScaleFactor, maxScaleFactor);
                matrix.postTranslate(maxOffsetX / 2, maxOffsetY / 2);

                break;

            case CENTER_INSIDE:
            case FIT_CENTER:

                matrix.setScale(minScaleFactor, minScaleFactor);
                matrix.postTranslate(minOffsetX / 2, minOffsetY / 2);

                break;

            case FIT_END:

                matrix.setScale(minScaleFactor, minScaleFactor);
                matrix.postTranslate(minOffsetX, minOffsetY);

                break;

            case FIT_START:

                matrix.setScale(minScaleFactor, minScaleFactor);

                break;

            case FIT_XY:

                matrix.setScale(fitHorizontallyScaleFactor, fitVerticallyScaleFactor);

                break;

            case MATRIX:

                matrix.set(MasaccioImageView.super.getImageMatrix());

                break;
        }

        return matrix;
    }

    private void init(final AttributeSet attrs, final int defStyle) {

        // Read attributes

        final TypedArray typedArray =
                getContext().obtainStyledAttributes(attrs, R.styleable.MasaccioImageView, defStyle,
                                                    0);

        final boolean autoFaceDetection =
                typedArray.getBoolean(R.styleable.MasaccioImageView_auto_face, false);

        mOffsetX = typedArray.getFloat(R.styleable.MasaccioImageView_offset_x, -1);
        mOffsetY = typedArray.getFloat(R.styleable.MasaccioImageView_offset_y, -1);

        mAnimationDuration =
                typedArray.getInt(R.styleable.MasaccioImageView_animation_duration, 1000);

        final int interpolatorId =
                typedArray.getResourceId(R.styleable.MasaccioImageView_animation_interpolator,
                                         NO_ID);

        mStartScale = typedArray.getFloat(R.styleable.MasaccioImageView_animation_start_scale, -1);
        mStartX = typedArray.getFloat(R.styleable.MasaccioImageView_animation_start_x, 0);
        mStartY = typedArray.getFloat(R.styleable.MasaccioImageView_animation_start_y, 0);

        mEndScale = typedArray.getFloat(R.styleable.MasaccioImageView_animation_end_scale, -1);
        mEndX = typedArray.getFloat(R.styleable.MasaccioImageView_animation_end_x, 0);
        mEndY = typedArray.getFloat(R.styleable.MasaccioImageView_animation_end_y, 0);

        mOriginalScaleType = getScaleType();
        mMessageHandler = new Handler();

        setAutoFaceDetection(autoFaceDetection);

        if (interpolatorId != NO_ID) {

            mInterpolator = AnimationUtils.loadInterpolator(getContext(), interpolatorId);
        }
    }

    private void startMatrixAnimation(final Matrix start, final Matrix end, final long timeMs) {

        setImageMatrix(start);

        mStartCoeffs = new float[9];
        start.getValues(mStartCoeffs);

        mEndCoeffs = new float[9];
        end.getValues(mEndCoeffs);

        mStartTime = System.currentTimeMillis();
        mDuration = timeMs;
    }

    public interface MasaccioFaceDetector {

        public Face[] process(Bitmap bitmap);
    }

    private static class DefaultMasaccioFaceDetector implements MasaccioFaceDetector {

        private static final int MAX_FACES = 4;

        private static final Face[] NO_FACES = new Face[0];

        private final Map<Bitmap, Face[]> mFacesMap =
                Collections.synchronizedMap(new WeakHashMap<Bitmap, Face[]>());

        @Override
        public Face[] process(final Bitmap bitmap) {

            final Map<Bitmap, Face[]> facesMap = mFacesMap;

            final Face[] preProcessed = facesMap.get(bitmap);

            if (preProcessed != null) {

                if (preProcessed == NO_FACES) {

                    return null;
                }

                return preProcessed;
            }

            final Face[] faces = new Face[MAX_FACES];

            final Bitmap bitmap565 = convertTo565(bitmap);

            if (bitmap565 != null) {

                final FaceDetector faceDetector =
                        new FaceDetector(bitmap565.getWidth(), bitmap565.getHeight(), MAX_FACES);

                final int faceCount = faceDetector.findFaces(bitmap565, faces);

                if (faceCount > 0) {

                    final Face[] detected = new Face[faceCount];

                    System.arraycopy(faces, 0, detected, 0, faceCount);

                    facesMap.put(bitmap, detected);

                    return detected;
                }
            }

            facesMap.put(bitmap, NO_FACES);

            return null;
        }

        private Bitmap convertTo565(final Bitmap origin) {

            if (origin == null) {

                return null;
            }

            Bitmap bitmap = origin;

            if (bitmap.getConfig() != Bitmap.Config.RGB_565) {

                bitmap = bitmap.copy(Bitmap.Config.RGB_565, true);
            }

            if ((bitmap.getWidth() & 0x1) != 0) {

                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth() & ~0x1,
                                             bitmap.getHeight());
            }

            return bitmap;
        }

        private Face[] getFaces(final Bitmap bitmap) {

            return mFacesMap.get(bitmap);
        }
    }

    private class CropRunnable implements Runnable {

        private final float mOriginalImageHeight;

        private final float mOriginalImageWidth;

        public CropRunnable(final float originalImageWidth, final float originalImageHeight) {

            mOriginalImageWidth = originalImageWidth;
            mOriginalImageHeight = originalImageHeight;
        }

        @Override
        public void run() {

            final float frameWidth = getWidth();
            final float frameHeight = getHeight();

            if ((frameWidth <= 0) || (frameHeight <= 0)) {

                MasaccioImageView.super.setScaleType(mOriginalScaleType);

                mMessageHandler.post(this);

                return;
            }

            MasaccioImageView.super.setScaleType(ScaleType.MATRIX);

            final float originalImageWidth = mOriginalImageWidth;
            final float originalImageHeight = mOriginalImageHeight;

            final float fitHorizontallyScaleFactor = frameWidth / originalImageWidth;
            final float fitVerticallyScaleFactor = frameHeight / originalImageHeight;

            final float maxScaleFactor =
                    Math.max(fitHorizontallyScaleFactor, fitVerticallyScaleFactor);

            final float newImageWidth = originalImageWidth * maxScaleFactor;
            final float newImageHeight = originalImageHeight * maxScaleFactor;

            final Matrix matrix = new Matrix();

            matrix.setScale(maxScaleFactor, maxScaleFactor);

            final float[] translateOffset = new float[2];

            final float maxOffsetX = newImageWidth - frameWidth;
            final float maxOffsetY = newImageHeight - frameHeight;

            final Face[] detectedFaces = mDetectedFaces;

            if (detectedFaces != null) {

                getFaceOffsets(detectedFaces, translateOffset, maxScaleFactor, newImageWidth,
                               newImageHeight, maxOffsetX, maxOffsetY);

            } else {

                getDefaultOffsets(translateOffset, maxOffsetX, maxOffsetY);
            }

            matrix.postTranslate(-translateOffset[0], -translateOffset[1]);

            final float endScale = mEndScale;
            final float endX = mEndX;
            final float endY = mEndY;

            final Matrix endMatrix;

            if ((endScale < 0) && (endX == 0) && (endY == 0)) {

                endMatrix = matrix;

            } else {

                endMatrix = new Matrix();

                final float scale;

                if (endScale < 0) {

                    scale = 1;

                } else {

                    scale = endScale;
                }

                final float endScaleFactor = maxScaleFactor * scale;
                final float endImageWidth = newImageWidth * scale;
                final float endImageHeight = newImageHeight * scale;
                final float[] scaledTranslateOffset = new float[2];

                if (detectedFaces != null) {

                    getFaceOffsets(detectedFaces, scaledTranslateOffset, endScaleFactor,
                                   endImageWidth, endImageHeight, endImageWidth - frameWidth,
                                   endImageHeight - frameHeight);

                } else {

                    getDefaultOffsets(scaledTranslateOffset, endImageWidth - frameWidth,
                                      endImageHeight - frameHeight);
                }

                endMatrix.setScale(endScaleFactor, endScaleFactor);
                endMatrix.postTranslate(
                        -scaledTranslateOffset[0] + (Math.abs(scaledTranslateOffset[0]) * (endX
                                / scale)),
                        -scaledTranslateOffset[1] + (Math.abs(scaledTranslateOffset[1]) * (endY
                                / scale)));
            }

            if (mInterpolator != null) {

                final float startScale = mStartScale;
                final float startX = mStartX;
                final float startY = mStartY;

                final Matrix startMatrix;

                if ((startScale < 0) && (startX == 0) && (startY == 0)) {

                    startMatrix = getOriginalMatrix(originalImageWidth, originalImageHeight);

                } else {

                    startMatrix = new Matrix();

                    final float scale;

                    if (startScale < 0) {

                        scale = 1;

                    } else {

                        scale = startScale;
                    }

                    startMatrix.setScale(maxScaleFactor * scale, maxScaleFactor * scale);

                    final float scaledOffsetX =
                            ((newImageWidth * (scale - 1)) / 2) + translateOffset[0];

                    final float scaledOffsetY =
                            ((newImageHeight * (scale - 1)) / 2) + translateOffset[1];

                    startMatrix.postTranslate(
                            -scaledOffsetX + (Math.abs(scaledOffsetX) * (endX / scale)),
                            -scaledOffsetY + (Math.abs(scaledOffsetY) * (endY / scale)));
                }

                startMatrixAnimation(startMatrix, endMatrix, mAnimationDuration);

            } else {

                setImageMatrix(endMatrix);
            }
        }
    }
}
