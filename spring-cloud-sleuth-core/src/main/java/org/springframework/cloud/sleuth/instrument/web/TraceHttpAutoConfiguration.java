/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import brave.ErrorParser;
import brave.Tracing;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;
import brave.http.HttpSampler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} related to HTTP based communication.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(name = "spring.sleuth.http.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(TraceWebAutoConfiguration.class)
@EnableConfigurationProperties({ TraceKeys.class, SleuthHttpLegacyProperties.class })
public class TraceHttpAutoConfiguration {

	static final int TRACING_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	HttpTracing httpTracing(Tracing tracing, SkipPatternProvider provider,
			HttpClientParser clientParser, HttpServerParser serverParser,
			@ClientSampler HttpSampler clientSampler,
			@Nullable @ServerSampler HttpSampler serverSampler) {
		HttpSampler combinedSampler = combineUserProvidedSamplerWithSkipPatternSampler(
				serverSampler, provider);
		return HttpTracing.newBuilder(tracing).clientParser(clientParser)
				.serverParser(serverParser).clientSampler(clientSampler)
				.serverSampler(combinedSampler).build();
	}

	private HttpSampler combineUserProvidedSamplerWithSkipPatternSampler(
			HttpSampler serverSampler, SkipPatternProvider provider) {
		SleuthHttpSampler skipPatternSampler = new SleuthHttpSampler(provider);
		if (serverSampler == null) {
			return skipPatternSampler;
		}
		return new CompositeHttpSampler(skipPatternSampler, serverSampler);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled", havingValue = "true")
	HttpClientParser sleuthHttpClientParser(TraceKeys traceKeys) {
		return new SleuthHttpClientParser(traceKeys);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled", havingValue = "false", matchIfMissing = true)
	@ConditionalOnMissingBean
	HttpClientParser httpClientParser(ErrorParser errorParser) {
		return new HttpClientParser() {
			@Override
			protected ErrorParser errorParser() {
				return errorParser;
			}
		};
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled", havingValue = "true")
	HttpServerParser sleuthHttpServerParser(TraceKeys traceKeys,
			ErrorParser errorParser) {
		return new SleuthHttpServerParser(traceKeys, errorParser);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled", havingValue = "false", matchIfMissing = true)
	@ConditionalOnMissingBean
	HttpServerParser defaultHttpServerParser() {
		return new HttpServerParser();
	}

	@Bean
	@ConditionalOnMissingBean(name = ClientSampler.NAME)
	HttpSampler sleuthClientSampler(SleuthWebProperties sleuthWebProperties) {
		return new PathMatchingHttpSampler(sleuthWebProperties);
	}

}

/**
 * Composite Http Sampler.
 *
 * @author Adrian Cole
 */
class CompositeHttpSampler extends HttpSampler {

	private final HttpSampler left;

	private final HttpSampler right;

	CompositeHttpSampler(HttpSampler left, HttpSampler right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
		// If either decision is false, return false
		Boolean leftDecision = this.left.trySample(adapter, request);
		if (Boolean.FALSE.equals(leftDecision)) {
			return false;
		}
		Boolean rightDecision = this.right.trySample(adapter, request);
		if (Boolean.FALSE.equals(rightDecision)) {
			return false;
		}
		// If either decision is null, return the other
		if (leftDecision == null) {
			return rightDecision;
		}
		if (rightDecision == null) {
			return leftDecision;
		}
		// Neither are null and at least one is true
		return leftDecision && rightDecision;
	}

}

/**
 * Http Sampler that looks at paths.
 *
 * @author Marcin Grzejszczak
 */
class PathMatchingHttpSampler extends HttpSampler {

	private final SleuthWebProperties properties;

	PathMatchingHttpSampler(SleuthWebProperties properties) {
		this.properties = properties;
	}

	@Override
	public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
		String path = adapter.path(request);
		if (path == null) {
			return null;
		}
		return path.matches(this.properties.getClient().getSkipPattern()) ? false : null;
	}

}
