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

    public static final int FLAG_IF_FACE = 0x10;

    public static final int FLAG_LANDSCAPE = 0x2;

    public static final int FLAG_NO_FACE = 0x8;

    public static final int FLAG_PORTRAIT = 0x1;

    public static final int FLAG_SQUARE = 0x4;

    private static final float FACE_POSITION_RATIO_X = 0.5f;

    private static final float FACE_POSITION_RATIO_Y = 0.5f;

    private static final int MAX_FACES = 4;

    private static final Map<Bitmap, Face[]> sFacesMap =
            Collections.synchronizedMap(new WeakHashMap<Bitmap, Face[]>());

    public final StepInterpolator mDefaultInterpolator = new StepInterpolator();

    private final Matrix mAnimMatrix = new Matrix();

    private int mActivateDetectionFlags;

    private int mActivateMatrixFlags;

    private long mAnimationDuration;

    private boolean mAutoFaceDetection;

    private CropRunnable mCropRunnable;

    private boolean mCyclicAnimation;

    private Face[] mDetectedFaces;

    private long mDuration;

    private float[] mEndCoeffs;

    private float mEndScale;

    private float mEndX;

    private float mEndY;

    private Interpolator mInterpolator;

    private int mMaxFaceNumber;

    private Handler mMessageHandler;

    private float mMinConfidence;

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

        return getFaceDetector(MAX_FACES);
    }

    public static MasaccioFaceDetector getFaceDetector(int maxFaceNumber) {

        return new DefaultMasaccioFaceDetector(maxFaceNumber);
    }

    private static boolean enabledDimensions(final int width, final int height, final int flags) {

        boolean enabled = !hasAnyFlag(flags, FLAG_PORTRAIT | FLAG_LANDSCAPE | FLAG_SQUARE);

        if ((width > height) && hasAllFlags(flags, FLAG_LANDSCAPE)) {

            enabled = true;

        } else if ((width < height) && hasAllFlags(flags, FLAG_PORTRAIT)) {

            enabled = true;

        } else if (hasAllFlags(flags, FLAG_SQUARE)) {

            enabled = true;
        }

        return enabled;
    }

    private static boolean hasAllFlags(final int value, final int flags) {

        return (value & flags) == flags;
    }

    private static boolean hasAnyFlag(final int value, final int flags) {

        return (value & flags) != 0;
    }

    public void setActivateDetectionFlags(final int flags) {

        final boolean isUpdate = (mActivateDetectionFlags != flags);

        mActivateDetectionFlags = flags;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setActivateMatrixFlags(final int flags) {

        final boolean isUpdate = (mActivateMatrixFlags != flags);

        mActivateMatrixFlags = flags;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setAnimationDuration(final long durationMs) {

        mAnimationDuration = durationMs;
    }

    public void setAnimationInterpolator(final Interpolator interpolator) {

        if (interpolator != null) {

            mInterpolator = interpolator;

        } else {

            mInterpolator = mDefaultInterpolator;
        }
    }

    public void setCenterFace(final boolean enabled) {

        final boolean isUpdate = (mAutoFaceDetection != enabled);

        mAutoFaceDetection = enabled;

        if (enabled) {

            // Force instantiation
            getFaceDetector();
        }

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setCyclicAnimation(final boolean isCyclic) {

        mCyclicAnimation = isCyclic;
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

            invalidate();

        } else if ((startTime > 0) && (now >= endTime)) {

            if (mCyclicAnimation) {

                mStartTime = System.currentTimeMillis();

            } else {

                mStartTime = 0;
            }

            final Matrix matrix = mAnimMatrix;

            matrix.setValues(mEndCoeffs);

            setImageMatrix(matrix);
        }

        super.onDraw(canvas);
    }

    public void setMaxFaceNumber(final int maxFaceNumber) {

        mMaxFaceNumber = maxFaceNumber;
    }

    public void setMinConfidence(final float minConfidence) {

        mMinConfidence = minConfidence;
    }

    public void setPreScale(final float scale) {

        final boolean isUpdate = (mStartScale != scale);

        mStartScale = scale;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setPreTranslate(final float translateX, final float translateY) {

        final boolean isUpdate = (mStartX != translateX) || (mStartY != translateY);

        mStartX = translateX;
        mStartY = translateY;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setPreTranslateX(final float translateX) {

        final boolean isUpdate = (mStartX != translateX);

        mStartX = translateX;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setPreTranslateY(final float translateY) {

        final boolean isUpdate = (mStartY != translateY);

        mStartY = translateY;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setScale(final float scale) {

        final boolean isUpdate = (mEndScale != scale);

        mEndScale = scale;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setTranslate(final float translateX, final float translateY) {

        final boolean isUpdate = (mEndX != translateX) || (mEndY != translateY);

        mEndX = translateX;
        mEndY = translateY;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setTranslateX(final float translateX) {

        final boolean isUpdate = (mEndX != translateX);

        mEndX = translateX;

        if (isUpdate) {

            applyCrop();
        }
    }

    public void setTranslateY(final float translateY) {

        final boolean isUpdate = (mEndY != translateY);

        mEndY = translateY;

        if (isUpdate) {

            applyCrop();
        }
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right,
            final int bottom) {

        super.onLayout(changed, left, top, right, bottom);

        if (changed) {

            applyCrop();
        }
    }

    private void applyCrop() {

        setImageDrawable(getDrawable());
    }

    private void cropImage(final int originalImageWidth, final int originalImageHeight) {

        final Handler messageHandler = mMessageHandler;

        if (messageHandler == null) {

            // We can't do anything right now.
            return;
        }

        if (mCropRunnable != null) {

            messageHandler.removeCallbacks(mCropRunnable);
        }

        if ((!mAutoFaceDetection && (mEndX == 0) && (mEndY == 0) && (mEndScale == 1)) || (
                originalImageWidth <= 0) || (originalImageHeight <= 0)) {

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

        offsets[0] = maxOffsetX / 2;
        offsets[1] = maxOffsetY / 2;
    }

    private void getDetectedFaces(final Bitmap bitmap) {

        if (bitmap == null) {

            // Do nothing
            return;
        }

        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();

        if (enabledDimensions(width, height, mActivateDetectionFlags)) {

            if (mAutoFaceDetection) {

                mDetectedFaces = getFaceDetector(mMaxFaceNumber).process(bitmap);

            } else {

                mDetectedFaces = sFacesMap.get(bitmap);
            }

        } else {

            mDetectedFaces = null;
        }
    }

    private void getFaceOffsets(final Face[] faces, final float[] offsets, final float scaleFactor,
            final float width, final float height, final float maxOffsetX, final float maxOffsetY) {

        try {

            Face bestFace = null;

            float maxConfidence = mMinConfidence;

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

            if (Math.round(maxOffsetX) >= 0) {

                offsets[0] = Math.min(Math.max(0, scaledOffsetX), maxOffsetX);

            } else {

                offsets[0] = scaledOffsetX;
            }

            if (Math.round(maxOffsetY) >= 0) {

                offsets[1] = Math.min(Math.max(0, scaledOffsetY), maxOffsetY);

            } else {

                offsets[1] = scaledOffsetY;
            }


        } catch (final Exception e) {

            getDefaultOffsets(offsets, maxOffsetX, maxOffsetY);
        }
    }

    private Matrix getOriginalMatrix(final int originalImageWidth, final int originalImageHeight) {

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

                if (Math.max(fitHorizontallyScaleFactor, fitVerticallyScaleFactor) < 1) {

                    matrix.postTranslate((frameWidth - originalImageWidth) / 2,
                                         (frameHeight - originalImageHeight) / 2);

                    break;
                }

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

        mActivateDetectionFlags =
                typedArray.getInt(R.styleable.MasaccioImageView_activate_face_detection, 0);
        mActivateMatrixFlags = typedArray.getInt(R.styleable.MasaccioImageView_activate_matrix, 0);

        final boolean autoFaceDetection =
                typedArray.getBoolean(R.styleable.MasaccioImageView_center_face, false);

        mMinConfidence = typedArray.getFloat(R.styleable.MasaccioImageView_min_confidence, 0);

        mMaxFaceNumber =
                typedArray.getInteger(R.styleable.MasaccioImageView_max_face_number, MAX_FACES);

        mStartScale = typedArray.getFloat(R.styleable.MasaccioImageView_pre_scale, -1);
        mStartX = typedArray.getFloat(R.styleable.MasaccioImageView_pre_translate_x, 0);
        mStartY = typedArray.getFloat(R.styleable.MasaccioImageView_pre_translate_y, 0);

        mEndScale = typedArray.getFloat(R.styleable.MasaccioImageView_scale, -1);
        mEndX = typedArray.getFloat(R.styleable.MasaccioImageView_translate_x, 0);
        mEndY = typedArray.getFloat(R.styleable.MasaccioImageView_translate_y, 0);

        mAnimationDuration = typedArray.getInt(R.styleable.MasaccioImageView_animation_duration, 0);

        final int interpolatorId =
                typedArray.getResourceId(R.styleable.MasaccioImageView_animation_interpolator,
                                         NO_ID);

        mCyclicAnimation =
                typedArray.getBoolean(R.styleable.MasaccioImageView_cyclic_animation, false);

        mOriginalScaleType = getScaleType();
        mMessageHandler = new Handler();

        if (interpolatorId != NO_ID) {

            mInterpolator = AnimationUtils.loadInterpolator(getContext(), interpolatorId);

        } else {

            mInterpolator = mDefaultInterpolator;
        }

        setCenterFace(autoFaceDetection);
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

        private static final Face[] NO_FACES = new Face[0];

        private final int mMaxFaceNumber;

        private DefaultMasaccioFaceDetector(final int maxFaceNumber) {

            mMaxFaceNumber = maxFaceNumber;
        }

        @Override
        public Face[] process(final Bitmap bitmap) {

            final Map<Bitmap, Face[]> facesMap = sFacesMap;

            final Face[] preProcessed = facesMap.get(bitmap);

            if (preProcessed != null) {

                if (preProcessed == NO_FACES) {

                    return null;
                }

                return preProcessed;
            }

            final Face[] faces = new Face[mMaxFaceNumber];

            final Bitmap bitmap565 = convertTo565(bitmap);

            if (bitmap565 != null) {

                final FaceDetector faceDetector =
                        new FaceDetector(bitmap565.getWidth(), bitmap565.getHeight(),
                                         mMaxFaceNumber);

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
    }

    private class CropRunnable implements Runnable {

        private final int mOriginalImageHeight;

        private final int mOriginalImageWidth;

        public CropRunnable(final int originalImageWidth, final int originalImageHeight) {

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

            final int originalImageWidth = mOriginalImageWidth;
            final int originalImageHeight = mOriginalImageHeight;

            final float fitHorizontallyScaleFactor = frameWidth / originalImageWidth;
            final float fitVerticallyScaleFactor = frameHeight / originalImageHeight;

            final float maxScaleFactor =
                    Math.max(fitHorizontallyScaleFactor, fitVerticallyScaleFactor);

            final float newImageWidth = originalImageWidth * maxScaleFactor;
            final float newImageHeight = originalImageHeight * maxScaleFactor;

            final float maxOffsetX = newImageWidth - frameWidth;
            final float maxOffsetY = newImageHeight - frameHeight;

            final Matrix matrix;
            final Face[] detectedFaces = mDetectedFaces;
            final float[] translateOffset = new float[2];
            final int matrixFlag = mActivateMatrixFlags;

            if (detectedFaces != null) {

                getFaceOffsets(detectedFaces, translateOffset, maxScaleFactor, newImageWidth,
                               newImageHeight, maxOffsetX, maxOffsetY);

                matrix = new Matrix();
                matrix.setScale(maxScaleFactor, maxScaleFactor);
                matrix.postTranslate(-translateOffset[0], -translateOffset[1]);

                if (hasAllFlags(matrixFlag, FLAG_NO_FACE) && !hasAllFlags(matrixFlag,
                                                                          FLAG_IF_FACE)) {

                    setImageMatrix(matrix);

                    return;
                }

            } else {

                matrix = getOriginalMatrix(originalImageWidth, originalImageHeight);

                if (!hasAllFlags(matrixFlag, FLAG_NO_FACE) && hasAllFlags(matrixFlag,
                                                                          FLAG_IF_FACE)) {

                    setImageMatrix(matrix);

                    return;
                }
            }

            if (!enabledDimensions(originalImageWidth, originalImageHeight, matrixFlag)) {

                setImageMatrix(matrix);

                return;
            }

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
                        -scaledTranslateOffset[0] + (Math.abs(endImageWidth - frameWidth) * endX),
                        -scaledTranslateOffset[1] + (Math.abs(endImageHeight - frameHeight)
                                * endY));
            }

            if (mAnimationDuration > 0) {

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

                    final float startScaleFactor = maxScaleFactor * scale;
                    final float startImageWidth = newImageWidth * scale;
                    final float startImageHeight = newImageHeight * scale;

                    startMatrix.setScale(startScaleFactor, startScaleFactor);

                    final float scaledOffsetX =
                            ((newImageWidth * (scale - 1)) / 2) + translateOffset[0];

                    final float scaledOffsetY =
                            ((newImageHeight * (scale - 1)) / 2) + translateOffset[1];

                    startMatrix.postTranslate(
                            -scaledOffsetX + (Math.abs(startImageWidth - frameWidth) * startX),
                            -scaledOffsetY + (Math.abs(startImageHeight - frameHeight) * startY));
                }

                startMatrixAnimation(startMatrix, endMatrix, mAnimationDuration);

            } else {

                setImageMatrix(endMatrix);
            }
        }
    }

    private class StepInterpolator implements Interpolator {

        @Override
        public float getInterpolation(final float input) {

            return 0;
        }
    }
}
