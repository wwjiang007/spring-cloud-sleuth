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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.BeanFactory;

/**
 * A decorator class for {@link ScheduledExecutorService} to support tracing in Executors.
 *
 * @author Gaurav Rai Mazra
 * @since 1.0.0
 */
public class TraceableScheduledExecutorService extends TraceableExecutorService
		implements ScheduledExecutorService {

	public TraceableScheduledExecutorService(BeanFactory beanFactory,
			final ExecutorService delegate) {
		super(beanFactory, delegate);
	}

	private ScheduledExecutorService getScheduledExecutorService() {
		return (ScheduledExecutorService) this.delegate;
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		return getScheduledExecutorService().schedule(
				ContextUtil.isContextInCreation(this.beanFactory) ? command
						: new TraceRunnable(tracing(), spanNamer(), command),
				delay, unit);
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay,
			TimeUnit unit) {
		return getScheduledExecutorService().schedule(
				ContextUtil.isContextInCreation(this.beanFactory) ? callable
						: new TraceCallable<>(tracing(), spanNamer(), callable),
				delay, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
			long period, TimeUnit unit) {
		return getScheduledExecutorService().scheduleAtFixedRate(
				ContextUtil.isContextInCreation(this.beanFactory) ? command
						: new TraceRunnable(tracing(), spanNamer(), command),
				initialDelay, period, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
			long delay, TimeUnit unit) {
		return getScheduledExecutorService().scheduleWithFixedDelay(
				ContextUtil.isContextInCreation(this.beanFactory) ? command
						: new TraceRunnable(tracing(), spanNamer(), command),
				initialDelay, delay, unit);
	}

}
