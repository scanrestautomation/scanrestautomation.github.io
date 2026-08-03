package com.yubraj.test.scanrest.autoconfigure;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Annotation to explicitly enable ScanRest in a Spring Boot test.
 *
 * <p>Usage:</p>
 * <pre>
 * &#064;SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 * &#064;EnableScanRest
 * class ApiTests {
 *
 *     &#064;Autowired
 *     private ScanRestTestRunner scanRestRunner;
 *
 *     &#064;Test
 *     void runAllApiTests() {
 *         List&lt;TestResult&gt; results = scanRestRunner.runTests();
 *         assertThat(results).allMatch(TestResult::isPassed);
 *     }
 * }
 * </pre>
 *
 * <p>Alternatively, if {@code scanrest.enabled=true} is in your properties,
 * the auto-configuration kicks in automatically without this annotation.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ScanRestAutoConfiguration.class)
public @interface EnableScanRest {
}
