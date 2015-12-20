/*
 * Copyright (c) 2015 Fabio Berta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.berta.fabio.fabprogress.sample;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import ch.berta.fabio.fabprogress.FabProgress;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final FabProgress fabProgress = (FabProgress) findViewById(R.id.fab_progress);
        fabProgress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(fabProgress, "This is a snackbar", Snackbar.LENGTH_SHORT).show();
                fabProgress.startProgress();
            }
        });

        final FabProgress fabProgress2 = (FabProgress) findViewById(R.id.fab_progress_2);
        fabProgress2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fabProgress2.startProgress();
            }
        });

        final FabProgress fb2 = (FabProgress) findViewById(R.id.fb2);
        fb2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fb2.startProgress();
            }
        });

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fabProgress.startProgressFinalAnimation();
                fabProgress2.stopProgress();
                fb2.startProgressFinalAnimation();
            }
        });
    }
}
