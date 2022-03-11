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

import io.vertx.core.MultiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.text.translate.AggregateTranslator;
import org.apache.commons.text.translate.CharSequenceTranslator;
import org.apache.commons.text.translate.EntityArrays;
import org.apache.commons.text.translate.LookupTranslator;
import org.apache.commons.text.translate.NumericEntityUnescaper;

public final class XSSUtils {

	private static final Logger log = LoggerFactory.getLogger(XSSUtils.class);

	private static final Map<CharSequence, CharSequence> UNESCAPE_ADDITIONAL_ENTITIES = new HashMap<>();
	static {
		UNESCAPE_ADDITIONAL_ENTITIES.put("&equals;", "\u003D");
		UNESCAPE_ADDITIONAL_ENTITIES.put("&colon;", "\u003A");
		UNESCAPE_ADDITIONAL_ENTITIES.put("&semi;", "\u003B");
		UNESCAPE_ADDITIONAL_ENTITIES.put("&comma;", "\u002C");
	}
	private static final CharSequenceTranslator UNESCAPE_HTMLENTITIES =
            new AggregateTranslator(
                    new LookupTranslator(EntityArrays.BASIC_UNESCAPE),
                    new LookupTranslator(EntityArrays.ISO8859_1_UNESCAPE),
                    new LookupTranslator(EntityArrays.HTML40_EXTENDED_UNESCAPE),
                    new LookupTranslator(UNESCAPE_ADDITIONAL_ENTITIES),
                    new NumericEntityUnescaper(NumericEntityUnescaper.OPTION.semiColonOptional)
            );

	private XSSUtils() {}

	private static final Pattern[] patterns = new Pattern[]{
			Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
//			Pattern.compile("src[\r\n]*=[\r\n]*\\\'(.*?)\\\'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
//			Pattern.compile("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("<script>", Pattern.CASE_INSENSITIVE),
			Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
			Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("atob\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
			Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
			Pattern.compile("on(click|context|mouse|dblclick|key|abort|error|before|hash|load|page|" +
					"resize|scroll|unload|blur|change|focus|input|invalid|reset|search|select|submit|drag|drop|copy|cut|paste|" +
					"after|before|can|end|duration|emptied|play|progress|seek|stall|suspend|time|volume|waiting|message|open|touch|" +
					"online|offline|popstate|show|storage|toggle|wheel|animationstart|begin)(\\s*\\w*\\s*)=",
					Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
	};

	public static MultiMap safeMultiMap(MultiMap m) {
		for (String name : m.names()) {
			List<String> values = m.getAll(name);
			List<String> safeValues = new ArrayList<>();
			if (values == null) continue;
			for (String value: values) {
				safeValues.add(stripXSS(value));
			}
			m.set(name, safeValues);
		}
		return m;
	}

	private static final Pattern unicodePattern = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

	private static String unescapeUnicode(String value) {
		Matcher unicodeMatcher = unicodePattern.matcher(value);
		while (unicodeMatcher.find()) {
			try {
				value = unicodeMatcher.replaceAll(String.valueOf((char)(Integer.parseInt(unicodeMatcher.group(1), 16))));
			} catch (Exception e) {
				log.error("[XSSUtils] Error in escaping Unicode value", e);
			}
		}
		return value;
	}

	private static Pattern base64Pattern = Pattern.compile("base64\\s*,\\s*((?:(?:(?:(?:\\\\[frnt])| )*[A-Z0-9+/]){4})*(?:(?:(?:(?:\\\\[frnt]| ))*[A-Z0-9+/]){2}==|(?:(?:(?:\\\\[frnt]| ))*[A-Z0-9+/]){3}=)?)",
			Pattern.CASE_INSENSITIVE);

	private static String unescapeBase64(String value) {
		Matcher base64Matcher = base64Pattern.matcher(value);
		while (base64Matcher.find()) {
			try {
				String group = base64Matcher.group(1);
				group = group.replaceAll("(?:\\\\[frnt])| ", ""); // Removing whitespace characters before decoding
				String base64Token = new String(Base64.getDecoder().decode(group));
				String tmp = stripXSS(base64Token);
				if (base64Token.length() != tmp.length()) {
					value = base64Matcher.replaceAll("");
				}
			} catch (Exception e) {
				log.error("[XSSUtils] Error in escaping Base64 value", e);
			}
		}
		return value;
	}

	public static String stripXSS(String value) {
		if (value != null) {
			//value = ESAPI.encoder().canonicalize(value);
			value = value.replaceAll("\0", "");
			String tmp = unescapeUnicode(value);
			tmp = UNESCAPE_HTMLENTITIES.translate(tmp);
			final int lengthBeforeBase64 = tmp.length();
			tmp = unescapeBase64(tmp);
			final int lengthAfterBase64 = tmp.length();
			for (Pattern scriptPattern : patterns){
				tmp = scriptPattern.matcher(tmp).replaceAll("");
			}
			if (lengthBeforeBase64 != lengthAfterBase64 || lengthAfterBase64 != tmp.length()) {
				value = stripXSS(tmp);
			}
		}
		return value;
	}

}
