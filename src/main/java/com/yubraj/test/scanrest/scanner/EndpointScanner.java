package com.yubraj.test.scanrest.scanner;

import com.yubraj.test.scanrest.model.HttpMethod;
import com.yubraj.test.scanrest.model.ScannedEndpoint;
import com.yubraj.test.scanrest.model.ScannedEndpoint.ParameterInfo;
import io.github.classgraph.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scans compiled Spring Boot classes for @RestController/@Controller endpoints
 * using ClassGraph. Works on compiled .class files (target/classes or a JAR).
 */
public class EndpointScanner {

    private static final Logger log = LoggerFactory.getLogger(EndpointScanner.class);

    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private static final String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    private static final String DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";
    private static final String PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping";

    private static final String PATH_VARIABLE = "org.springframework.web.bind.annotation.PathVariable";
    private static final String REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam";
    private static final String REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody";
    private static final String REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader";

    private static final Map<String, HttpMethod> MAPPING_TO_METHOD = Map.of(
            GET_MAPPING, HttpMethod.GET,
            POST_MAPPING, HttpMethod.POST,
            PUT_MAPPING, HttpMethod.PUT,
            DELETE_MAPPING, HttpMethod.DELETE,
            PATCH_MAPPING, HttpMethod.PATCH
    );

    /**
     * Scan a directory or JAR for Spring controller endpoints.
     *
     * @param classpath path to compiled classes directory (e.g., target/classes) or a JAR file
     * @return list of discovered endpoints
     */
    public List<ScannedEndpoint> scan(String classpath) {
        log.info("Scanning classpath: {}", classpath);
        List<ScannedEndpoint> endpoints = new ArrayList<>();

        File cpFile = new File(classpath);
        if (!cpFile.exists()) {
            log.error("Classpath does not exist: {}", classpath);
            return endpoints;
        }

        try {
            URL[] urls = {cpFile.toURI().toURL()};
            try (URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader())) {
                try (ScanResult scanResult = new ClassGraph()
                        .overrideClasspath(classpath)
                        .addClassLoader(classLoader)
                        .enableAllInfo()
                        .scan()) {

                    ClassInfoList controllers = scanResult.getClassesWithAnnotation(REST_CONTROLLER);
                    controllers.addAll(scanResult.getClassesWithAnnotation(CONTROLLER));

                    log.info("Found {} controller classes", controllers.size());

                    for (ClassInfo controllerInfo : controllers) {
                        String classLevelPath = extractPath(controllerInfo.getAnnotationInfo(REQUEST_MAPPING));
                        processController(controllerInfo, classLevelPath, endpoints);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan classpath: {}", e.getMessage(), e);
        }

        log.info("Discovered {} endpoints", endpoints.size());
        return endpoints;
    }

    private void processController(ClassInfo controllerInfo, String classLevelPath, List<ScannedEndpoint> endpoints) {
        for (MethodInfo methodInfo : controllerInfo.getDeclaredMethodInfo()) {
            // Check for @RequestMapping
            AnnotationInfo requestMapping = methodInfo.getAnnotationInfo(REQUEST_MAPPING);
            if (requestMapping != null) {
                String methodPath = extractPath(requestMapping);
                HttpMethod[] methods = extractMethods(requestMapping);
                for (HttpMethod method : methods) {
                    endpoints.add(buildEndpoint(controllerInfo, methodInfo, classLevelPath, methodPath, method));
                }
                continue;
            }

            // Check for shortcut annotations (@GetMapping, @PostMapping, etc.)
            for (var entry : MAPPING_TO_METHOD.entrySet()) {
                AnnotationInfo mappingAnnotation = methodInfo.getAnnotationInfo(entry.getKey());
                if (mappingAnnotation != null) {
                    String methodPath = extractPath(mappingAnnotation);
                    endpoints.add(buildEndpoint(controllerInfo, methodInfo, classLevelPath, methodPath, entry.getValue()));
                }
            }
        }
    }

    private ScannedEndpoint buildEndpoint(ClassInfo controllerInfo, MethodInfo methodInfo,
                                           String classLevelPath, String methodPath, HttpMethod httpMethod) {
        ScannedEndpoint endpoint = new ScannedEndpoint();
        endpoint.setClassName(controllerInfo.getSimpleName());
        endpoint.setMethodName(methodInfo.getName());
        endpoint.setPath(normalizePath(classLevelPath, methodPath));
        endpoint.setHttpMethod(httpMethod);

        // Extract parameters
        for (MethodParameterInfo paramInfo : methodInfo.getParameterInfo()) {
            AnnotationInfoList paramAnnotations = paramInfo.getAnnotationInfo();
            for (AnnotationInfo ann : paramAnnotations) {
                String annName = ann.getName();
                ParameterInfo.Type type = switch (annName) {
                    case PATH_VARIABLE -> ParameterInfo.Type.PATH_VARIABLE;
                    case REQUEST_PARAM -> ParameterInfo.Type.QUERY_PARAM;
                    case REQUEST_BODY -> ParameterInfo.Type.REQUEST_BODY;
                    case REQUEST_HEADER -> ParameterInfo.Type.REQUEST_HEADER;
                    default -> null;
                };
                if (type != null) {
                    String name = extractParamName(ann, paramInfo.getName());
                    endpoint.getParameters().add(
                            new ParameterInfo(name, paramInfo.getTypeDescriptor().toStringWithSimpleNames(), type)
                    );
                }
            }
        }

        return endpoint;
    }

    private String extractPath(AnnotationInfo annotationInfo) {
        if (annotationInfo == null) return "";
        AnnotationParameterValueList params = annotationInfo.getParameterValues();

        // Try "value" first, then "path"
        for (String key : List.of("value", "path")) {
            Object val = params.getValue(key);
            if (val instanceof String[] arr && arr.length > 0) {
                return arr[0];
            }
            if (val instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        return "";
    }

    private HttpMethod[] extractMethods(AnnotationInfo requestMapping) {
        AnnotationParameterValueList params = requestMapping.getParameterValues();
        Object methodVal = params.getValue("method");
        if (methodVal instanceof Object[] methods && methods.length > 0) {
            List<HttpMethod> result = new ArrayList<>();
            for (Object m : methods) {
                try {
                    String methodStr = m.toString();
                    // Handle enum refs like "RequestMethod.GET"
                    if (methodStr.contains(".")) {
                        methodStr = methodStr.substring(methodStr.lastIndexOf('.') + 1);
                    }
                    result.add(HttpMethod.valueOf(methodStr));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!result.isEmpty()) return result.toArray(new HttpMethod[0]);
        }
        // Default to GET if no method specified
        return new HttpMethod[]{HttpMethod.GET};
    }

    private String extractParamName(AnnotationInfo annotation, String fallbackName) {
        AnnotationParameterValueList params = annotation.getParameterValues();
        for (String key : List.of("value", "name")) {
            Object val = params.getValue(key);
            if (val instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        return fallbackName != null ? fallbackName : "unknown";
    }

    private String normalizePath(String classPath, String methodPath) {
        String combined = ("/" + classPath + "/" + methodPath)
                .replaceAll("/+", "/");
        if (combined.endsWith("/") && combined.length() > 1) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined;
    }
}
