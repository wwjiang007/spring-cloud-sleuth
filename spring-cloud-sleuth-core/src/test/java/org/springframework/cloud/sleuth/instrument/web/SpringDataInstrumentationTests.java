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

import java.net.URI;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import brave.Tracer;
import brave.sampler.Sampler;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Span;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.RequestEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.BDDAssertions.then;

@RepositoryRestResource
interface ReservationRepository extends JpaRepository<Reservation, Long> {

}

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ReservationServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.sleuth.http.legacy.enabled=true")
@DirtiesContext
@ActiveProfiles("data")
public class SpringDataInstrumentationTests {

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	Environment environment;

	@Autowired
	Tracer tracer;

	@Autowired
	ArrayListSpanReporter reporter;

	@Before
	public void setup() {
		this.reporter.clear();
	}

	@Test
	public void should_create_span_instrumented_by_a_handler_interceptor() {
		long noOfNames = namesCount();

		then(noOfNames).isEqualTo(8);
		then(this.reporter.getSpans()).isNotEmpty();
		Awaitility.await().untilAsserted(() -> {
			// Make sure the data is attached to the right side of the span
			then(this.reporter.getSpans())
					.extracting(Span::kind, Span::name,
							s -> s.tags().get("mvc.controller.class"))
					.containsExactlyInAnyOrder(
							tuple(Span.Kind.CLIENT, "http:/reservations", null),
							tuple(Span.Kind.SERVER, "http:/reservations",
									"RepositoryEntityController"));
		});
		then(this.tracer.currentSpan()).isNull();
	}

	long namesCount() {
		return this.restTemplate
				.exchange(RequestEntity
						.get(URI.create("http://localhost:" + port() + "/reservations"))
						.build(), PagedModel.class)
				.getBody().getMetadata().getTotalElements();
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

}

@Configuration
@EnableAutoConfiguration(exclude = { SecurityAutoConfiguration.class,
		GatewayAutoConfiguration.class, GatewayClassPathWarningAutoConfiguration.class })
@EntityScan(basePackageClasses = Reservation.class)
class ReservationServiceApplication {

	@Bean
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	SampleRecords sampleRecords(ReservationRepository reservationRepository) {
		return new SampleRecords(reservationRepository);
	}

	@Bean
	ArrayListSpanReporter arrayListSpanAccumulator() {
		return new ArrayListSpanReporter();
	}

	@Bean
	Sampler alwaysSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

}

class SampleRecords {

	private final ReservationRepository reservationRepository;

	SampleRecords(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@PostConstruct
	public void create() throws Exception {
		Stream.of("Josh", "Jungryeol", "Nosung", "Hyobeom", "Soeun", "Seunghue", "Peter",
				"Jooyong")
				.forEach(name -> this.reservationRepository.save(new Reservation(name)));
		this.reservationRepository.findAll().forEach(System.out::println);
	}

}

@Entity
class Reservation {

	@Id
	@GeneratedValue
	private Long id; // id

	private String reservationName; // reservation_name

	Reservation() { // why JPA why???
	}

	Reservation(String reservationName) {

		this.reservationName = reservationName;
	}

	public Long getId() {
		return this.id;
	}

	public String getReservationName() {
		return this.reservationName;
	}

	@Override
	public String toString() {
		return "Reservation{" + "id=" + this.id + ", reservationName='"
				+ this.reservationName + '\'' + '}';
	}

}
