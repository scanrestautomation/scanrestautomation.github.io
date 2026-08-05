package io.scanrest.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-mocking configuration for ScanRest.
 *
 * <p>When {@code scanrest.mock-services=true}, this configuration replaces
 * all {@code @Service} beans in the context with Mockito mocks. This allows
 * controller-level API tests to run without real service implementations.</p>
 *
 * <p>This is a convenience feature for quick testing. For more complex
 * mocking scenarios (conditional returns, verification), use {@code @MockBean}
 * in your own test class instead.</p>
 *
 * <p>Requires Mockito on the classpath (provided by spring-boot-starter-test).</p>
 *
 * <p>Usage:</p>
 * <pre>
 * scanrest.mode=MOCKMVC
 * scanrest.mock-services=true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(prefix = "scanrest", name = "mock-services", havingValue = "true")
@ConditionalOnClass(name = "org.mockito.Mockito")
public class ScanRestMockConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ScanRestMockConfiguration.class);

    @Bean
    public static BeanFactoryPostProcessor scanRestServiceMocker() {
        return new ServiceMockingPostProcessor();
    }

    /**
     * Post-processor that replaces @Service bean definitions with Mockito mock factories.
     */
    static class ServiceMockingPostProcessor implements BeanFactoryPostProcessor {

        private static final Logger log = LoggerFactory.getLogger(ServiceMockingPostProcessor.class);

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
                log.warn("ScanRest: Cannot auto-mock services - BeanFactory is not a BeanDefinitionRegistry");
                return;
            }

            List<String> mockedBeans = new ArrayList<>();

            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                var beanDef = beanFactory.getBeanDefinition(beanName);
                String beanClassName = beanDef.getBeanClassName();
                if (beanClassName == null) continue;

                try {
                    Class<?> beanClass = Class.forName(beanClassName);
                    if (beanClass.isAnnotationPresent(Service.class)) {
                        // Replace with a mock bean definition
                        RootBeanDefinition mockDef = new RootBeanDefinition();
                        mockDef.setBeanClass(beanClass);
                        mockDef.setInstanceSupplier(() -> createMock(beanClass));
                        mockDef.setPrimary(true);

                        registry.removeBeanDefinition(beanName);
                        registry.registerBeanDefinition(beanName, mockDef);
                        mockedBeans.add(beanName + " (" + beanClass.getSimpleName() + ")");
                    }
                } catch (ClassNotFoundException e) {
                    // Skip beans whose class can't be loaded
                }
            }

            if (!mockedBeans.isEmpty()) {
                log.info("ScanRest: Auto-mocked {} service bean(s): {}", mockedBeans.size(), mockedBeans);
            } else {
                log.info("ScanRest: No @Service beans found to mock");
            }
        }

        private static Object createMock(Class<?> beanClass) {
            try {
                // Use reflection to call Mockito.mock() to avoid compile-time dependency
                Class<?> mockitoClass = Class.forName("org.mockito.Mockito");
                var mockMethod = mockitoClass.getMethod("mock", Class.class);
                return mockMethod.invoke(null, beanClass);
            } catch (Exception e) {
                throw new RuntimeException("ScanRest: Failed to create Mockito mock for " + beanClass.getName() +
                        ". Ensure Mockito is on the classpath (add spring-boot-starter-test).", e);
            }
        }
    }
}
