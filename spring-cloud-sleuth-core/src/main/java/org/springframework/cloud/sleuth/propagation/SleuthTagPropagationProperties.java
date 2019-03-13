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

package org.springframework.cloud.sleuth.propagation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties of tag propagation.
 *
 * @author Taras Danylchuk
 * @since 2.1.0
 */
@ConfigurationProperties("spring.sleuth.propagation.tag")
public class SleuthTagPropagationProperties {

	/**
	 * Enables a {@link TagPropagationFinishedSpanHandler} that adds extra propagated
	 * fields to span tags.
	 */
	private boolean enabled = true;

	/**
	 * A list of keys to be put from extra propagation fields to span tags.
	 */
	private List<String> whitelistedKeys = new ArrayList<>();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getWhitelistedKeys() {
		return this.whitelistedKeys;
	}

	public void setWhitelistedKeys(List<String> whitelistedKeys) {
		this.whitelistedKeys = whitelistedKeys;
	}

}
