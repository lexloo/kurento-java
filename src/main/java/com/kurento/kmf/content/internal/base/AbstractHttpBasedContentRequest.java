package com.kurento.kmf.content.internal.base;

import java.io.IOException;
import java.util.concurrent.Future;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import com.kurento.kmf.content.ContentException;
import com.kurento.kmf.content.internal.ContentRequestManager;
import com.kurento.kmf.content.internal.StreamingProxy;
import com.kurento.kmf.content.internal.StreamingProxyListener;
import com.kurento.kmf.content.internal.jsonrpc.JsonRpcResponse;
import com.kurento.kmf.media.HttpEndPoint;
import com.kurento.kmf.media.MediaElement;

public abstract class AbstractHttpBasedContentRequest extends
		AbstractContentRequest {

	@Autowired
	private StreamingProxy proxy;

	protected boolean useControlProtocol;
	protected boolean redirect;
	protected volatile Future<?> tunnellingProxyFuture;

	public AbstractHttpBasedContentRequest(ContentRequestManager manager,
			AsyncContext asyncContext, String contentId, boolean redirect,
			boolean useControlProtocol) {
		super(manager, asyncContext, contentId);
		this.useControlProtocol = useControlProtocol;
		this.redirect = redirect;
		if (!useControlProtocol) {
			state = STATE.HANDLING;
		}
	}

	protected abstract MediaElement buildRepositoryBasedMediaElement(
			String contentPath);

	protected abstract HttpEndPoint buildHttpEndPointMediaElement(
			MediaElement mediaElement);

	/*
	 * This is an utility method designed for minimizing code replication. For
	 * it to work, one and only one of the two parameters must be null;
	 */
	protected void activateMedia(MediaElement mediaElement, String contentPath)
			throws ContentException {
		synchronized (this) {
			Assert.isTrue(state == STATE.HANDLING,
					"Cannot start media exchange in state " + state
							+ ". This error means ..."); // TODO further
															// explanation
			state = STATE.STARTING;
		}

		Assert.isTrue(mediaElement == null || contentPath == null,
				"Internal error. Cannot process request containing two null parameters");
		Assert.isTrue(mediaElement != null || contentPath != null,
				"Internal error. Cannot process request containing two non null parameters");

		if (contentPath != null) {
			mediaElement = buildRepositoryBasedMediaElement(contentPath);
		}
		HttpEndPoint httpEndPoint = buildHttpEndPointMediaElement(mediaElement);

		// We need to assert that session was not rejected while we were
		// creating media infrastructure
		boolean terminate = false;
		synchronized (this) {
			if (state == STATE.TERMINATED) {
				terminate = true;
			} else if (state == STATE.STARTING) {
				state = STATE.ACTIVE;
			}
		}

		// If session was rejected, just terminate
		if (terminate) {
			// clean up
			// return
		}
		// If session was not rejected (state=ACTIVE) we send an answer and
		// the initialAsyncCtx becomes useless
		if (useControlProtocol) {
			answerActivateMediaRequest4JsonControlProtocolConfiguration(httpEndPoint);
		} else {
			answerActivateMediaRequest4SimpleHttpConfiguration(httpEndPoint);
		}

	}

	private void answerActivateMediaRequest4SimpleHttpConfiguration(
			HttpEndPoint httpEndPoint) throws ContentException {
		try {
			HttpServletResponse response = (HttpServletResponse) initialAsyncCtx
					.getResponse();
			HttpServletRequest request = (HttpServletRequest) initialAsyncCtx
					.getRequest();
			if (redirect) {
				response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
				//response.setHeader("Location", httpEndPoint.getUrl()); //TODO: uncomment
				response.setHeader("Location", "http://media.w3.org/2010/05/sintel/trailer.webm"); //TODO remove
			} else {
				tunnellingProxyFuture = proxy.tunnelTransaction(request,
						response, httpEndPoint.getUrl(), 
						new StreamingProxyListener() {

							@Override
							public void onProxySuccess() {
								tunnellingProxyFuture = null;
								// Parameters no matter, no answer will be sent
								// given that we are already in ACTIVE state
								terminate(HttpServletResponse.SC_OK, "");
							}

							@Override
							public void onProxyError(String message) {
								tunnellingProxyFuture = null;
								// Parameters no matter, no answer will be sent
								// given that we are already in ACTIVE state
								terminate(
										HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
										message);
							}
						});
			}
		} catch (Throwable t) {
			// TODO when final KMS version is ready, perhaps it will be
			// necessary to release httpEndPoint and playerEndPoint resources.
			throw new ContentException(t.getMessage(), t);
		} finally {
			if (redirect) {
				initialAsyncCtx.complete();
			}
			initialAsyncCtx = null;
		}
	}

	private void answerActivateMediaRequest4JsonControlProtocolConfiguration(
			HttpEndPoint httpEndPoint) throws ContentException {
		try {
			// Send URL as answer to client
			protocolManager.sendJsonAnswer(initialAsyncCtx, JsonRpcResponse
					//.newStartUrlResponse(httpEndPoint.getUrl(), sessionId, //TODO uncomment
					.newStartUrlResponse("http://media.w3.org/2010/05/sintel/trailer.webm", sessionId, //TODO remove
							initialJsonRequest.getId()));
			initialAsyncCtx = null;
			initialJsonRequest = null;
		} catch (IOException e) {
			// TODO when final KMS version is ready, perhaps it will be
			// necessary to release httpEndPoint and playerEndPoint resources.
			throw new ContentException(e);
		}
	}

	public boolean useControlProtocol() {
		return useControlProtocol;
	}

	@Override
	protected void sendOnTerminateErrorMessageInInitialContext(int code,
			String description) throws IOException {
		if (useControlProtocol) {
			protocolManager.sendJsonError(initialAsyncCtx, JsonRpcResponse
					.newError(code, description, initialJsonRequest.getId()));
		} else {
			((HttpServletResponse) initialAsyncCtx.getResponse()).sendError(
					code, description);
		}
	}

	@Override
	protected void destroy() {
		super.destroy();

		Future<?> localTunnelingProxyFuture = tunnellingProxyFuture;
		if (localTunnelingProxyFuture != null) {
			localTunnelingProxyFuture.cancel(true);
			tunnellingProxyFuture = null;
		}
	}

}