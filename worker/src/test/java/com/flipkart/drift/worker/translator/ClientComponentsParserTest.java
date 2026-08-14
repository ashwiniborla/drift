package com.flipkart.drift.worker.translator;

import com.flipkart.drift.commons.model.clientComponent.ClientComponents;
import com.flipkart.drift.commons.model.clientComponent.HttpComponents;
import com.flipkart.drift.commons.model.clientComponent.VariableAttributeComponent;
import com.flipkart.drift.commons.model.componentDetail.ComponentDetail;
import com.flipkart.drift.commons.model.componentDetail.ScriptedComponentDetail;
import com.flipkart.drift.commons.model.componentDetail.StaticComponentDetail;
import com.flipkart.drift.commons.model.value.StringValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientComponentsParserTest {

    // --- Helper methods ---

    private ComponentDetail<StringValue, String> buildStaticStringDetail(String data) {
        return new StaticComponentDetail<>(new StringValue(data));
    }

    private ComponentDetail<StringValue, Map> buildStaticMapDetail(String json) {
        return new StaticComponentDetail<>(new StringValue(json));
    }

    private ScriptedComponentDetail buildScriptedDetail(String script) {
        return new ScriptedComponentDetail(new StringValue(script));
    }

    @SuppressWarnings("unchecked")
    private HttpComponents makeHttpComponents(
            ComponentDetail<StringValue, String> url,
            ComponentDetail<StringValue, ?> headers,
            ComponentDetail<StringValue, ?> queryParams,
            ComponentDetail<StringValue, ?> body) {
        return HttpComponents.builder()
                .url(url)
                .headers((ComponentDetail<StringValue, Map>) headers)
                .queryParams((ComponentDetail<StringValue, Map>) queryParams)
                .body((ComponentDetail<StringValue, Map>) body)
                .build();
    }

    private VariableAttributeComponent makeVariableAttributeComponent(ComponentDetail<StringValue, String> attribute) {
        return new VariableAttributeComponent(attribute);
    }

    private String generateScript(ClientComponents components) {
        try {
            return new ClientComponentsParser().generateClientExecutableScript(components);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // --- Test methods ---

    /**
     * Test 1 (AC1, AC6): omitted queryParams field with non-null url.
     * Script must NOT contain "return 'null'" and MUST contain "return null".
     */
    @Test
    void omittedQueryParamsGeneratesReturnNull_notReturnNullString() {
        HttpComponents components = makeHttpComponents(
                buildStaticStringDetail("https://example.com"),
                null,
                null,
                null
        );
        String script = generateScript(components);
        assertFalse(script.contains("return 'null'"),
                "Script must not contain return 'null' (string-quoted null) for omitted field");
        assertTrue(script.contains("return null"),
                "Script must contain return null (bareword) for omitted field");
    }

    /**
     * Test 2 (AC1): all fields null — url omitted.
     * Script must NOT contain "return 'null'" and MUST contain "return null".
     */
    @Test
    void omittedUrlGeneratesReturnNull() {
        HttpComponents components = makeHttpComponents(null, null, null, null);
        String script = generateScript(components);
        assertFalse(script.contains("return 'null'"),
                "Script must not contain return 'null' (string-quoted null) for omitted url");
        assertTrue(script.contains("return null"),
                "Script must contain return null (bareword) for omitted url");
    }

    /**
     * Test 3 (AC1, AC4): all four ComponentDetail fields null.
     * Script must NOT contain "return 'null'". "return null" must appear at least 4 times.
     */
    @Test
    void allFieldsOmittedGeneratesReturnNullForAll() {
        HttpComponents components = makeHttpComponents(null, null, null, null);
        String script = generateScript(components);
        assertFalse(script.contains("return 'null'"),
                "Script must not contain return 'null' when all fields are omitted");

        int count = 0;
        int idx = 0;
        String needle = "return null";
        while ((idx = script.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        assertTrue(count >= 4,
                "Script must contain at least 4 occurrences of 'return null' when all four fields are null, found: " + count);
    }

    /**
     * Test 4 (AC2, AC6): StaticComponentDetail with data==null converges with omitted field.
     * Both must contain "return null" and neither must contain "return 'null'".
     */
    @Test
    void staticNullDataConvergesWithOmittedField() {
        HttpComponents omitted = makeHttpComponents(null, null, null, null);
        String omittedScript = generateScript(omitted);

        HttpComponents staticNull = makeHttpComponents(
                new StaticComponentDetail<>(new StringValue(null)),
                null, null, null
        );
        String staticNullScript = generateScript(staticNull);

        assertTrue(omittedScript.contains("return null"),
                "Omitted field script must contain return null (bareword)");
        assertFalse(omittedScript.contains("return 'null'"),
                "Omitted field script must not contain return 'null'");

        assertTrue(staticNullScript.contains("return null"),
                "StaticComponentDetail with null data script must contain return null (bareword)");
        assertFalse(staticNullScript.contains("return 'null'"),
                "StaticComponentDetail with null data script must not contain return 'null'");
    }

    /**
     * Test 5 (AC2): scripted workaround "return [:]" preserved verbatim.
     * Script must contain "return [:]" and must NOT contain "return 'null'".
     */
    @Test
    void workaroundScriptPreservedUnchanged() {
        HttpComponents components = makeHttpComponents(
                buildStaticStringDetail("https://example.com"),
                null,
                buildScriptedDetail("return [:]"),
                null
        );
        String script = generateScript(components);
        assertTrue(script.contains("return [:]"),
                "Script must preserve scripted workaround 'return [:]' verbatim");
        assertFalse(script.contains("return 'null'"),
                "Script must not contain return 'null' when workaround script is provided");
    }

    /**
     * Test 6 (AC3): populated headers JSON triggers JsonSlurper path.
     * Script must contain "JsonSlurper" and the header key. No "return 'null'".
     */
    @Test
    void populatedStaticMapFieldPreserved() {
        HttpComponents components = makeHttpComponents(
                null,
                buildStaticMapDetail("{\"Authorization\": \"Bearer token123\"}"),
                null,
                null
        );
        String script = generateScript(components);
        assertTrue(script.contains("JsonSlurper"),
                "Script must contain JsonSlurper for a populated static map field");
        assertTrue(script.contains("Authorization"),
                "Script must contain the header key from the populated map field");
        assertFalse(script.contains("return 'null'"),
                "Script must not contain return 'null' when headers are populated");
    }

    /**
     * Test 7 (AC3): populated url string field — script must contain the URL in single quotes.
     * No "return 'null'".
     */
    @Test
    void populatedStaticStringFieldPreserved() {
        HttpComponents components = makeHttpComponents(
                buildStaticStringDetail("https://api.example.com/v1/resource"),
                null,
                null,
                null
        );
        String script = generateScript(components);
        assertTrue(script.contains("'https://api.example.com/v1/resource'"),
                "Script must contain the static URL string single-quoted");
        assertFalse(script.contains("return 'null'"),
                "Script must not contain return 'null' when url is populated");
    }

    /**
     * Test 8 (AC3): scripted url field preserved verbatim.
     * Script must contain the Groovy expression. No "return 'null'".
     */
    @Test
    void populatedScriptedFieldPreserved() {
        String groovyExpr = "return _global?.userId ?: 'default'";
        HttpComponents components = makeHttpComponents(
                buildScriptedDetail(groovyExpr),
                null,
                null,
                null
        );
        String script = generateScript(components);
        assertTrue(script.contains(groovyExpr),
                "Script must preserve the scripted Groovy expression verbatim");
        assertFalse(script.contains("return 'null'"),
                "Script must not contain return 'null' when scripted field is provided");
    }

    /**
     * Test 9 (AC4): VariableAttributeComponent with attribute=null.
     * Script must NOT contain "return 'null'" and MUST contain "return null".
     */
    @Test
    void variableAttributeComponentOmittedAttributeGeneratesReturnNull() {
        VariableAttributeComponent vac = makeVariableAttributeComponent(null);
        String script = generateScript(vac);
        assertFalse(script.contains("return 'null'"),
                "VariableAttributeComponent with null attribute must not generate return 'null'");
        assertTrue(script.contains("return null"),
                "VariableAttributeComponent with null attribute must generate return null (bareword)");
    }

    /**
     * Test 10 (AC5): full HttpComponents with all four fields populated.
     * No "return 'null'". Script contains URL, JsonSlurper, and scripted expression.
     */
    @Test
    void fullHttpComponentsNoOmittedFields_noReturnNullString() {
        HttpComponents components = makeHttpComponents(
                buildStaticStringDetail("https://api.example.com/endpoint"),
                buildStaticMapDetail("{\"Content-Type\": \"application/json\"}"),
                buildScriptedDetail("return [page: _global?.page ?: '1']"),
                buildStaticMapDetail("{\"key\": \"value\"}")
        );
        String script = generateScript(components);
        assertFalse(script.contains("return 'null'"),
                "Fully populated HttpComponents script must not contain return 'null'");
        assertTrue(script.contains("https://api.example.com/endpoint"),
                "Script must contain the URL string");
        assertTrue(script.contains("JsonSlurper"),
                "Script must contain JsonSlurper for the static map fields");
        assertTrue(script.contains("_global?.page"),
                "Script must contain the scripted Groovy expression");
    }
}
