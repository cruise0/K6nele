/*
 * Copyright 2011, Institute of Cybernetics at Tallinn University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.ioc.phon.android.speak;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.text.Html;
import android.text.method.LinkMovementMethod;

/**
 * <p>Simple activity that shows the info page (help page).
 * The content and style of this activity are defined entirely
 * in the resource XML files. The content is interpreted here
 * as an HTML string, i.e. one can use simple HTML-formatting
 * and linking.</p>
 * 
 * @author Kaarel Kaljurand
 */
public class AboutActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);

		TextView tvAboutHeader = (TextView) findViewById(R.id.tvAboutHeader);
		tvAboutHeader.setMovementMethod(LinkMovementMethod.getInstance());

		// Header
		String aboutHeader = String.format(
				getString(R.string.tvAboutHeader),
				getString(R.string.labelApp),
				getString(R.string.descriptionApp),
				Utils.getVersionName(this)
		);
		tvAboutHeader.setText(Html.fromHtml(aboutHeader));

		// Content
		TextView tvAbout = (TextView) findViewById(R.id.tvAbout);
		tvAbout.setMovementMethod(LinkMovementMethod.getInstance());
		String about = String.format(
				getString(R.string.tvAbout),
				getString(R.string.labelApp)
		);
		tvAbout.setText(Html.fromHtml(about));
	}
}