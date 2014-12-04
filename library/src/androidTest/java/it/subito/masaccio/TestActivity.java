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

import android.app.Activity;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.widget.ImageView.ScaleType;

import org.fest.assertions.data.Offset;

import it.subito.masaccio.TestActivity.ActivityUnderTest;

import static org.fest.assertions.api.Assertions.assertThat;

public class TestActivity extends ActivityInstrumentationTestCase2<ActivityUnderTest> {

    public TestActivity() {

        super(ActivityUnderTest.class);
    }

    @Override
    public void setUp() throws Exception {

        super.setUp();
        // Espresso will not launch our activity for us, we must launch it via getActivity().
        getActivity();
    }

    @UiThreadTest
    public void testFaceDetection() throws InterruptedException {

        final MasaccioImageView view =
                (MasaccioImageView) getActivity().findViewById(R.id.masaccio_view);

        assertThat(view.getScaleType()).isEqualTo(ScaleType.MATRIX);

        final float[] coeffs = new float[9];
        view.getImageMatrix().getValues(coeffs);

        assertThat(coeffs[0]).isEqualTo(0.234375f, Offset.offset(0.01f));
        assertThat(coeffs[1]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[2]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[3]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[4]).isEqualTo(0.234375f, Offset.offset(0.01f));
        assertThat(coeffs[5]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[6]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[7]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[8]).isEqualTo(1.0f, Offset.offset(0.01f));
    }

    @UiThreadTest
    public void testFaceDetection2() throws InterruptedException {

        final MasaccioImageView view =
                (MasaccioImageView) getActivity().findViewById(R.id.masaccio_view);

        view.setImageDrawable(getActivity().getResources().getDrawable(R.drawable.pan0));

        assertThat(view.getScaleType()).isEqualTo(ScaleType.MATRIX);

        final float[] coeffs = new float[9];
        view.getImageMatrix().getValues(coeffs);

        assertThat(coeffs[0]).isEqualTo(0.3125f, Offset.offset(0.01f));
        assertThat(coeffs[1]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[2]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[3]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[4]).isEqualTo(0.3125f, Offset.offset(0.01f));
        assertThat(coeffs[5]).isEqualTo(-10.175781f, Offset.offset(0.01f));
        assertThat(coeffs[6]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[7]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[8]).isEqualTo(1.0f, Offset.offset(0.01f));
    }

    @UiThreadTest
    public void testFaceDetection3() throws InterruptedException {

        final MasaccioImageView view =
                (MasaccioImageView) getActivity().findViewById(R.id.masaccio_view);

        view.setImageDrawable(getActivity().getResources().getDrawable(R.drawable.pan1));

        assertThat(view.getScaleType()).isEqualTo(ScaleType.MATRIX);

        final float[] coeffs = new float[9];
        view.getImageMatrix().getValues(coeffs);

        assertThat(coeffs[0]).isEqualTo(0.8333333f, Offset.offset(0.01f));
        assertThat(coeffs[1]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[2]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[3]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[4]).isEqualTo(0.8333333f, Offset.offset(0.01f));
        assertThat(coeffs[5]).isEqualTo(-17.239578f, Offset.offset(0.01f));
        assertThat(coeffs[6]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[7]).isEqualTo(0.0f, Offset.offset(0.01f));
        assertThat(coeffs[8]).isEqualTo(1.0f, Offset.offset(0.01f));
    }

    public static class ActivityUnderTest extends Activity {

        @Override
        protected void onCreate(final Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);

            setContentView(R.layout.activity_layout);
        }
    }
}
