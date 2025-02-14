package fr.wseduc.webutils.request.filter;

import java.util.ArrayList;
import java.util.List;

import fr.wseduc.webutils.request.AccessLogger;
import fr.wseduc.webutils.security.XssSecuredHttpServerRequest;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import fr.wseduc.webutils.security.SecureHttpServerRequest;

/*
 * Implement a Security Handler with a per-verticle filters chain
 */
public class SecurityHandler implements Handler<HttpServerRequest> {
	private static final Logger logger = LoggerFactory.getLogger(SecurityHandler.class);

	// Plus de static : chaque instance de SecurityHandler a son propre filtre
	private final List<Filter> chain = new ArrayList<>();

	// Chaque instance peut recevoir son propre Vert.x si nécessaire
	private final Vertx vertx;

	public SecurityHandler(Vertx vertx) {
		this.vertx = vertx;
		chain.add(new AccessLoggerFilter(new AccessLogger()));
		chain.add(new UserAuthFilter());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Handler<Boolean> chainToHandler(final HttpServerRequest request) {
		final Handler[] handlers = new Handler[chain.size()];
		request.pause();
		handlers[chain.size() - 1] = access -> {
			if (Boolean.TRUE.equals(access)) {
				request.resume();
				filter(request);
			} else {
				chain.get(chain.size() - 1).deny(request);
			}
		};
		for (int i = chain.size() - 2; i >= 0; i--) {
			final int idx = i;

			handlers[i] = access -> {
				if (Boolean.TRUE.equals(access)) {
					chain.get(idx + 1).canAccess(request, handlers[idx + 1]);
				} else {
					chain.get(idx).deny(request);
				}
			};
		}

		return handlers[0];
	}

	@Override
	public void handle(HttpServerRequest request) {
		if (!chain.isEmpty()) {
			SecureHttpServerRequest sr = new XssSecuredHttpServerRequest(request);
			chain.get(0).canAccess(sr, chainToHandler(sr));
		} else {
			filter(request);
		}
	}

	public void addFilter(Filter filter) {
		// add only once in case of multiple instances per verticle
		for (final Filter f : chain) {
			if (f.getClass().equals(filter.getClass())) {
				logger.warn("The filter has already been added: " + filter.getClass());
				return;
			}
		}
		chain.add(filter);
	}

	public void clearFilters() {
		chain.clear();
	}

	public void setVertx() {
		for (Filter f : chain) {
			if (f instanceof WithVertx) {
				((WithVertx) f).setVertx(vertx);
			}
		}
	}

	public void filter(HttpServerRequest request) {
		// Implémentation par défaut, peut être surchargée
		logger.info("Request passed through SecurityHandler: " + request.uri());

	}
}
