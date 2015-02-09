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

package fr.wseduc.webutils;

import java.io.UnsupportedEncodingException;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerFileUpload;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.http.ETag;

public class FileUtils {

	public static JsonObject metadata(HttpServerFileUpload upload) {
		JsonObject metadata = new JsonObject();
		metadata.putString("name", upload.name());
		metadata.putString("filename", upload.filename());
		metadata.putString("content-type", upload.contentType());
		metadata.putString("content-transfer-encoding", upload.contentTransferEncoding());
		metadata.putString("charset", upload.charset().name());
		metadata.putNumber("size", upload.size());
		return metadata;
	}

	public static void writeUploadFile(final HttpServerRequest request, final String filePath,
			final Handler<JsonObject> handler) {
		request.expectMultiPart(true);
		request.uploadHandler(new Handler<HttpServerFileUpload>() {
			@Override
			public void handle(final HttpServerFileUpload upload) {
				final String filename = filePath;
				upload.endHandler(new Handler<Void>() {
					@Override
					public void handle(Void event) {
						handler.handle(FileUtils.metadata(upload));
					}
				});
				upload.streamToFileSystem(filename);
			}
		});
	}

	public static void gridfsWriteUploadFile(final HttpServerRequest request, final EventBus eb,
			final String gridfsAddress, final Handler<JsonObject> handler) {
		gridfsWriteUploadFile(request, eb, gridfsAddress, null, handler);
	}

	public static void gridfsWriteUploadFile(final HttpServerRequest request, final EventBus eb,
			final String gridfsAddress, final Long maxSize, final Handler<JsonObject> handler) {
		request.expectMultiPart(true);
		request.uploadHandler(new Handler<HttpServerFileUpload>() {
			@Override
			public void handle(final HttpServerFileUpload event) {
				final Buffer buff = new Buffer();
				event.dataHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer event) {
						buff.appendBuffer(event);
					}
				});
				event.endHandler(new Handler<Void>() {
					@Override
					public void handle(Void end) {
						gridfsWriteBuffer(null, buff, maxSize, handler, eb,
								event.contentType(), event.filename(), metadata(event), gridfsAddress);
					}
				});
			}
		});
	}

	public static void gridfsWriteBuffer(Buffer buff, String contentType,
			String filename, EventBus eb, final Handler<JsonObject> handler, String gridfsAddress) {
		gridfsWriteBuffer(null, buff, null, handler, eb, contentType, filename, null, gridfsAddress);
	}

	public static void gridfsWriteBuffer(String id, Buffer buff, String contentType,
			String filename, EventBus eb, final Handler<JsonObject> handler, String gridfsAddress) {
		gridfsWriteBuffer(id, buff, null, handler, eb, contentType, filename, null, gridfsAddress);
	}

	private static void gridfsWriteBuffer(String id, Buffer buff, Long maxSize,
		final Handler<JsonObject> handler, EventBus eb, String contentType,
		String filename, final JsonObject m, final String gridfsAddress) {
		JsonObject save = new JsonObject();
		save.putString("action", "save");
		save.putString("content-type", contentType);
		save.putString("filename", filename);
		if (id != null && !id.trim().isEmpty()) {
			save.putString("_id", id);
		}
		final JsonObject metadata = (m != null) ? m : new JsonObject()
				.putString("content-type", contentType)
				.putString("filename", filename);
		if (metadata.getLong("size", 0l).equals(0l)) {
			metadata.putNumber("size", buff.length());
		}
		if (maxSize != null && maxSize < metadata.getLong("size", 0l)) {
			handler.handle(new JsonObject().putString("status", "error")
				.putString("message", "file.too.large"));
			return;
		}
		byte [] header = null;
		try {
			header = save.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			JsonObject json = new JsonObject().putString("status", "error")
					.putString("message", e.getMessage());
			handler.handle(json);
		}
		if (header != null) {
			buff.appendBytes(header).appendInt(header.length);
			eb.send(gridfsAddress, buff, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					handler.handle(message.body()
							.putObject("metadata", metadata));
				}
			});
		}
	}

	public static void gridfsReadFile(String id, final EventBus eb,
			final String gridfsAddress, final Handler<Buffer> handler) {
		JsonObject find = new JsonObject();
		find.putString("action", "findone");
		find.putObject("query", new JsonObject("{ \"_id\": \"" + id + "\"}"));
		byte [] header = null;
		try {
			header = find.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			handler.handle(new Buffer());
		}
		if (header != null) {
			Buffer buf = new Buffer(header);
			buf.appendInt(header.length);
			eb.send(gridfsAddress, buf, new  Handler<Message<Buffer>>() {
			@Override
			public void handle(Message<Buffer> res) {
				handler.handle(res.body());
			}
		});
		}
	}

	public static void gridfsSendFile(final String id, final String downloadName, final EventBus eb,
			final String gridfsAddress, final HttpServerResponse response, final boolean inline,
			final JsonObject metadata) {
		gridfsReadFile(id, eb, gridfsAddress, new Handler<Buffer>() {
			@Override
			public void handle(Buffer file) {
				if (!inline) {
					String name = downloadName;
					if (metadata != null && metadata.getString("filename") != null) {
						String filename = metadata.getString("filename");
						int fIdx = filename.lastIndexOf('.');
						String fExt = null;
						if (fIdx >= 0) {
							fExt = filename.substring(fIdx);
						}
						int dIdx = downloadName.lastIndexOf('.');
						String dExt = null;
						if (dIdx >= 0) {
							dExt = downloadName.substring(dIdx);
						}
						if (fExt != null && !fExt.equals(dExt)) {
							name += fExt;
						}
					}
					response.putHeader("Content-Disposition",
							"attachment; filename=\"" + name +"\"");
				} else {
					ETag.addHeader(response, id);
				}
				if (metadata != null && metadata.getString("content-type") != null) {
					response.putHeader("Content-Type", metadata.getString("content-type"));
				}
				response.end(file);
			}
		});
	}

	public static void gridfsRemoveFile(String id, EventBus eb, String gridfsAddress,
			final Handler<JsonObject> handler) {
		gridfsRemoveFiles(new JsonArray().add(id), eb, gridfsAddress, handler);
	}

	public static void gridfsRemoveFiles(JsonArray ids, EventBus eb, String gridfsAddress,
			final Handler<JsonObject> handler) {
		JsonObject find = new JsonObject();
		find.putString("action", "remove");
		JsonObject query = new JsonObject();
		if (ids != null && ids.size() == 1) {
			query.putString("_id", ids.<String>get(0));
		} else {
			query.putObject("_id", new JsonObject().putArray("$in", ids));
		}
		find.putObject("query", query);
		byte [] header = null;
		try {
			header = find.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			handler.handle(new JsonObject().putString("status", "error"));
		}
		if (header != null) {
			Buffer buf = new Buffer(header);
			buf.appendInt(header.length);
			eb.send(gridfsAddress, buf, new  Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> res) {
					if (handler != null) {
						handler.handle(res.body());
					}
				}
			});
		}
	}

	public static void gridfsCopyFile(String id, EventBus eb, String gridfsAddress,
			final Handler<JsonObject> handler) {
		JsonObject find = new JsonObject();
		find.putString("action", "copy");
		find.putObject("query", new JsonObject("{ \"_id\": \"" + id + "\"}"));
		byte [] header = null;
		try {
			header = find.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			handler.handle(new JsonObject().putString("status", "error"));
		}
		if (header != null) {
			Buffer buf = new Buffer(header);
			buf.appendInt(header.length);
			eb.send(gridfsAddress, buf, new  Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> res) {
					handler.handle(res.body());
				}
			});
		}
	}

}
