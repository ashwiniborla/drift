package com.flipkart.drift.commons.utils;

public class Constants {

    public static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = ObjectMapperUtil.INSTANCE.getMapper();

    public static final class GroovyParser {
        public static final String GROOVY_PARSER_FILTER_METHOD_NOT_STARTS_WITH_PREFIX = "templatized_";
    }

    public static final class Workflow {
        public static final String GLOBAL = "_global";
        public static final String HTTP_RESPONSE = "_response";
        public static final String ENUM_STORE = "_enum_store";
        public static final String WORKFLOW_EXCEPTION = "Encountered WorkflowException : {}";
        public static final String API_EXCEPTION = "Error while returning API response";

    }

    public static final class GroovyBuilder {
        public static final String GROOVY_BUILDER_IMPORT = "import groovy.json.*\n import com.flipkart.drift.commons.Utility.*\n";
        public static final String GROOVY_BUILDER_METHOD_PREFIX = "def ";
        public static final String GROOVY_BUILDER_METHOD_SIGNATURE = "()";
        public static final String GROOVY_BUILDER_SCRIPTED_FIELDS_INFO = "scriptedFieldsInfo";
        public static final String GROOVY_BUILDER_METHOD_START_BRACES = "{\n";
        public static final String GROOVY_BUILDER_METHOD_END_BRACES = "}\n";
        public static final String GROOVY_BUILDER_MAP_VARIABLE_NAME = "Map";
        public static final String GROOVY_BUILDER_ASSIGN_OPERATOR = "=";
        public static final String GROOVY_BUILDER_MAP_DEFINE_SYNTAX = "[:]\n";
        public static final String GROOVY_BUILDER_ASSIGN_MAP_KEY_VALUE_OPEN = "['";
        public static final String GROOVY_BUILDER_ASSIGN_MAP_KEY_VALUE_CLOSE = "']";
        public static final String GROOVY_BUILDER_OPEN_SMALL_BRACKET = "('";
        public static final String GROOVY_BUILDER_CLOSE_SMALL_BRACKET = "')";
        public static final String GROOVY_BUILDER_RETURN_KEYWORD = "return ";
        public static final String GROOVY_BUILDER_SINGLE_QUOTE = "'";
        public static final String GROOVY_BUILDER_JSON_SLURPER_PARSER = "(new groovy.json.JsonSlurper()).parseText";
    }

    public static final class PerfConstants {
        public static final String X_PERF_TEST_UNDERSCORE = "X_PERF_TEST";
        public static final String X_PERF_TEST_DASH = "X-PERF-TEST";
        public static final String PERF_FLAG = "perfFlag";
    }
}
