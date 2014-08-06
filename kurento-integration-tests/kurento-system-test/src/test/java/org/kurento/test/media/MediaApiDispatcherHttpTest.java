/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package org.kurento.test.media;

import java.awt.Color;

import org.junit.Assert;
import org.junit.Test;
import org.kurento.media.Dispatcher;
import org.kurento.media.HttpGetEndpoint;
import org.kurento.media.HubPort;
import org.kurento.media.MediaPipeline;
import org.kurento.media.WebRtcEndpoint;
import org.kurento.test.base.BrowserMediaApiTest;
import org.kurento.test.client.Browser;
import org.kurento.test.client.BrowserClient;
import org.kurento.test.client.Client;
import org.kurento.test.client.WebRtcChannel;

/**
 * 
 * <strong>Description</strong>: A Chrome browser opens a WebRtcEndpoint and
 * this stream is connected through a Dispatcher to an HttpGetEndpoint, played
 * in another browser.<br/>
 * <strong>Pipeline</strong>:
 * <ul>
 * <li>WebRtcEndpoint -> Dispatcher -> HttpGetEndpoint</li>
 * </ul>
 * <strong>Pass criteria</strong>:
 * <ul>
 * <li>Browser starts before default timeout</li>
 * <li>Color of the video should be the expected</li>
 * </ul>
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 4.2.3
 */
public class MediaApiDispatcherHttpTest extends BrowserMediaApiTest {

	@Test
	public void testDispatcherHttpChrome() throws Exception {
		doTest(Browser.CHROME);
	}

	public void doTest(Browser browserType) throws Exception {
		// Media Pipeline
		MediaPipeline mp = pipelineFactory.create();
		WebRtcEndpoint webRtcEP1 = mp.newWebRtcEndpoint().build();
		HttpGetEndpoint httpEP = mp.newHttpGetEndpoint().terminateOnEOS()
				.build();

		Dispatcher dispatcher = mp.newDispatcher().build();
		HubPort hubPort1 = dispatcher.newHubPort().build();
		HubPort hubPort2 = dispatcher.newHubPort().build();

		webRtcEP1.connect(hubPort1);
		hubPort2.connect(httpEP);

		dispatcher.connect(hubPort1, hubPort2);

		// Test execution
		try (BrowserClient browser1 = new BrowserClient.Builder()
				.browser(browserType).client(Client.WEBRTC).build();
				BrowserClient browser2 = new BrowserClient.Builder()
						.browser(browserType).client(Client.PLAYER).build();) {

			browser1.connectToWebRtcEndpoint(webRtcEP1,
					WebRtcChannel.AUDIO_AND_VIDEO);

			browser2.setURL(httpEP.getUrl());
			browser2.subscribeEvents("playing");
			browser2.start();

			// Assertions
			Assert.assertTrue("Timeout waiting playing event",
					browser2.waitForEvent("playing"));
			Assert.assertTrue(
					"The color of the video should be green (RGB #008700)",
					browser2.colorSimilarTo(new Color(0, 135, 0)));
			Thread.sleep(5000);
		}
	}
}