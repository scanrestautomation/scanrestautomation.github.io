package io.scanrest.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an endpoint discovered by scanning Spring controller annotations.
 */
public class ScannedEndpoint {

    private String className;
    private String methodName;
    private String path;
    private HttpMethod httpMethod;
    private List<String> produces = new ArrayList<>();
    private List<String> consumes = new ArrayList<>();
    private List<ParameterInfo> parameters = new ArrayList<>();

    public ScannedEndpoint() {}

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public HttpMethod getHttpMethod() { return httpMethod; }
    public void setHttpMethod(HttpMethod httpMethod) { this.httpMethod = httpMethod; }

    public List<String> getProduces() { return produces; }
    public void setProduces(List<String> produces) { this.produces = produces; }

    public List<String> getConsumes() { return consumes; }
    public void setConsumes(List<String> consumes) { this.consumes = consumes; }

    public List<ParameterInfo> getParameters() { return parameters; }
    public void setParameters(List<ParameterInfo> parameters) { this.parameters = parameters; }

    @Override
    public String toString() {
        return "%s %s (%s#%s)".formatted(httpMethod, path, className, methodName);
    }

    /**
     * Represents a method parameter (path variable, query param, request body).
     */
    public static class ParameterInfo {
        public enum Type { PATH_VARIABLE, QUERY_PARAM, REQUEST_BODY, REQUEST_HEADER }

        private String name;
        private String typeName;
        private Type type;

        public ParameterInfo() {}
        public ParameterInfo(String name, String typeName, Type type) {
            this.name = name;
            this.typeName = typeName;
            this.type = type;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getTypeName() { return typeName; }
        public void setTypeName(String typeName) { this.typeName = typeName; }

        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }
    }
}
