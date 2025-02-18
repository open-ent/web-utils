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

package fr.wseduc.webutils.validation;

import fr.wseduc.webutils.data.FileResolver;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class JsonSchemaValidator {

	private String address;
	private EventBus eb;
	private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);
	private final ConcurrentMap<String, JsonObject> schemas = new ConcurrentHashMap<>();

	private JsonSchemaValidator() {}

	private static class JsonSchemaValidatorHolder {
		private static final JsonSchemaValidator instance = new JsonSchemaValidator();
	}

	public static JsonSchemaValidator getInstance() {
		return JsonSchemaValidatorHolder.instance;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public void setEventBus(EventBus eb) {
		this.eb = eb;
	}

	public void loadJsonSchema(final String keyPrefix, Vertx vertx, JsonObject config) {
		String JSONSCHEMA_PATH = FileResolver.absolutePath(config.getString("main"), "jsonschema");
		final FileSystem fs = vertx.fileSystem();

		fs.exists(JSONSCHEMA_PATH, event -> {
			if (event.failed() || Boolean.FALSE.equals(event.result())) {
				log.debug("Json schema directory not found for " + config.getString("path-prefix"));
				return;
			}
			fs.readDir(JSONSCHEMA_PATH, readDirEvent -> {
				if (readDirEvent.succeeded()) {
					for (final String path : readDirEvent.result()) {
						final String key = keyPrefix + path.substring(
								path.lastIndexOf(File.separatorChar) + 1, path.lastIndexOf('.'));

						fs.readFile(path, readFileEvent -> {
							if (readFileEvent.succeeded()) {
								JsonObject schemaJson = new JsonObject(readFileEvent.result().toString());

								// Vérifie si le schéma est déjà chargé pour éviter les doublons
								if (!schemas.containsKey(key)) {
									schemas.put(key, schemaJson);

									// Envoie individuellement chaque schéma sans supprimer les autres
									JsonObject addSchemaMessage = new JsonObject()
											.put("action", "addSchema")
											.put("key", key)
											.put("jsonSchema", schemaJson);

									eb.publish(address, addSchemaMessage);
								}
							} else {
								log.error("Error loading json schema : " + path, readFileEvent.cause());
							}
						});
					}
				} else {
					log.error("Error loading json schemas.", readDirEvent.cause());
				}
			});
		});
	}

	public void validate(String schema, JsonObject json, Handler<AsyncResult<Message<JsonObject>>> handler) {
		JsonObject j = new JsonObject()
				.put("action", "validate")
				.put("key", schema)
				.put("json", json);

		eb.request(address, j, new DeliveryOptions().setSendTimeout(10000), handler);
	}
}
