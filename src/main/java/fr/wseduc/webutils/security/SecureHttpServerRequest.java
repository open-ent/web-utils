/*
 * Copyright © WebServices pour l'Éducation, 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.wseduc.webutils.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import fr.wseduc.webutils.http.response.BufferHttpResponse;
import fr.wseduc.webutils.request.HttpServerRequestWithBuffering;
import fr.wseduc.webutils.request.ProxyHttpRequest;
import io.netty.handler.codec.DecoderResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

public class SecureHttpServerRequest implements HttpServerRequest, HttpServerRequestWithBuffering {

	private HttpServerRequest request;
	private JsonObject session;
	private final Map<String, String> attributes;
	private Buffer body;
	private boolean end;

	public SecureHttpServerRequest(HttpServerRequest request) {
		this.request = request;
		this.attributes = new HashMap<>();
	}

	public Optional<Buffer> getBodyResponseBuffered(){
		if(request.response() instanceof  BufferHttpResponse){
			final BufferHttpResponse buffered = (BufferHttpResponse)request.response();
			return Optional.of(buffered.getBuffer());
		} else{
			return Optional.empty();
		}
	}

	public SecureHttpServerRequest enableResponseBuffering(){
		if(request instanceof ProxyHttpRequest){
			return this;
		}else{
			request = new ProxyHttpRequest(request, new BufferHttpResponse(request.response()));
			return this;
		}
	}

	public SecureHttpServerRequest disableResponseBuffering(){
		if(request instanceof ProxyHttpRequest){
			request = ((ProxyHttpRequest) request).getOriginal();
			return this;
		}else{
			return this;
		}
	}

	@Override
	public HttpServerRequest pause() {
		request.pause();
		return this;
	}

	@Override
	public HttpServerRequest resume() {
		request.resume();
		return this;
	}

	@Override
	public HttpServerRequest endHandler(final Handler<Void> endHandler) {
		if (end) {
			if (endHandler != null) {
				endHandler.handle(null);
			}
			return this;
		}
		request.endHandler(new Handler<Void>() {
			@Override
			public void handle(Void event) {
				end = true;
				if (endHandler != null) {
					endHandler.handle(null);
				}
			}
		});
		return this;
	}

	@Override
	public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
		request.exceptionHandler(handler);
		return this;
	}

	@Override
	public HttpServerRequest handler(Handler<Buffer> handler) {
		return request.handler(handler);
	}

	@Override
	public HttpVersion version() {
		return request.version();
	}

	@Override
	public HttpMethod method() {
		return request.method();
	}

	@Override
	public boolean isSSL() {
		return request.isSSL();
	}

	@Override
	public String scheme() {
		return request.scheme();
	}

	@Override
	public String uri() {
		return request.uri();
	}

	@Override
	public String path() {
		return request.path();
	}

	@Override
	public String query() {
		return request.query();
	}

	@Override
	public String host() {
		return request.host();
	}

	@Override
	public HttpServerResponse response() {
		return request.response();
	}

	@Override
	public MultiMap headers() {
		return request.headers();
	}

	@Override
	public String getHeader(String headerName) {
		return request.getHeader(headerName);
	}

	@Override
	public String getHeader(CharSequence headerName) {
		return request.getHeader(headerName);
	}

	@Override
	public MultiMap params() {
		return request.params();
	}

//	@Override
//	public MultiMap params(boolean b) {
//		return request.params();
//	}

	@Override
	public String getParam(String paramName) {
		return request.getParam(paramName);
	}

	@Override
	public SocketAddress remoteAddress() {
		return request.remoteAddress();
	}

	@Override
	public SocketAddress localAddress() {
		return request.localAddress();
	}

	@Override
	public SSLSession sslSession() {
		return request.sslSession();
	}

	@Override
	public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
		return request.peerCertificateChain();
	}

	@Override
	public String absoluteURI() {
		return request.absoluteURI();
	}

	@Override
	public HttpServerRequest bodyHandler(final Handler<Buffer> bodyHandler) {
		if (body != null) {
			if (bodyHandler != null) {
				bodyHandler.handle(body);
			}
			return this;
		}
		request.bodyHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer event) {
				body = event;
				if (bodyHandler != null) {
					bodyHandler.handle(body);
				}
			}
		});
		return this;
	}

	@Override
	public HttpServerRequest setExpectMultipart(boolean expect) {
		if (!request.isExpectMultipart()) request.setExpectMultipart(expect);
		return this;
	}

	@Override
	public boolean isExpectMultipart() {
		return request.isExpectMultipart();
	}

	@Override
	public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> uploadHandler) {
		request.uploadHandler(uploadHandler);
		return this;
	}

	@Override
	public MultiMap formAttributes() {
		return request.formAttributes();
	}

	@Override
	public String getFormAttribute(String attributeName) {
		return request.getFormAttribute(attributeName);
	}

	@Override
	public boolean isEnded() {
		return request.isEnded();
	}

	@Override
	public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
		return request.customFrameHandler(handler);
	}

	@Override
	public HttpConnection connection() {
		return request.connection();
	}

	public JsonObject getSession() {
		return session;
	}

	public void setSession(JsonObject session) {
		this.session = session;
	}

	public void setAttribute(String attr, String value) {
		attributes.put(attr, value);
	}

	public String getAttribute(String attr) {
		return attributes.get(attr);
	}

	@Override
	public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler)
	{
		return request.streamPriorityHandler(handler);
	}

	@Override
	public long bytesRead()
	{
		return request.bytesRead();
	}

	@Override
	public HttpServerRequest fetch(long bytes)
	{
		return request.fetch(bytes);
	}

	@Override
	public Map<String, Cookie> cookieMap()
	{
		return request.cookieMap();
	}

	@Override
	public int cookieCount()
	{
		return request.cookieCount();
	}

	@Override
	public Cookie getCookie(String str)
	{
		return request.getCookie(str);
	}

	@Override
	public @io.vertx.codegen.annotations.Nullable HostAndPort authority() {
		return request.authority();
	}

	@Override
	public HttpServerRequest setParamsCharset(String charset) {
		return request.setParamsCharset(charset);
	}

	@Override
	public String getParamsCharset() {
		return request.getParamsCharset();
	}

	@Override
	public Future<Buffer> body() {
		return request.body();
	}

	@Override
	public Future<Void> end() {
		return request.end();
	}

	@Override
	public Future<NetSocket> toNetSocket() {
		return request.toNetSocket();
	}

	@Override
	public Future<ServerWebSocket> toWebSocket() {
		return request.toWebSocket();
	}

	@Override
	public DecoderResult decoderResult() {
		return request.decoderResult();
	}

	@Override
	public @io.vertx.codegen.annotations.Nullable Cookie getCookie(String name, String domain, String path) {
		return request.getCookie(name, domain, path);
	}

	@Override
	public Set<Cookie> cookies(String name) {
		return request.cookies(name);
	}

	@Override
	public Set<Cookie> cookies() {
		return request.cookies();
	}
}
