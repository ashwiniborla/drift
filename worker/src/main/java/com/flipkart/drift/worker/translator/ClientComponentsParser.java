package com.flipkart.drift.worker.translator;


import com.flipkart.drift.commons.model.componentDetail.ScriptedComponentDetail;
import com.flipkart.drift.commons.model.componentDetail.StaticComponentDetail;
import com.flipkart.drift.commons.model.clientComponent.ClientComponents;
import com.flipkart.drift.commons.model.annotation.IgnoreParsing;
import com.flipkart.drift.commons.utils.Utility;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.flipkart.drift.commons.utils.Constants.GroovyBuilder.*;
@Slf4j
public class ClientComponentsParser {
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * This will generate client executable script from given custom action executable
     *
     * @param components
     * @return
     * @throws IllegalAccessException
     */

    /**
     * Note: Below class has been imported from journey code base as it is.
     */
    private static final Cache<Class<?>, Field[]> fieldCache = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)  // Uses LRU (Least Recently Used) eviction policy by default
            .removalListener(notification -> log.warn("Field cache evicting entry: {}", notification.getKey()))
            .build();

    // Initial capacity for StringBuilder to reduce resizing operations
    private static final int INITIAL_SCRIPT_CAPACITY = 1024;


    public String generateClientExecutableScript(ClientComponents components) throws IllegalAccessException {
        try {
            StringBuilder clientExecutableScriptBuilder = new StringBuilder(INITIAL_SCRIPT_CAPACITY);
            addImportsToClientExecutableScript(clientExecutableScriptBuilder);
            generateScriptedFieldsInfoMap(components, clientExecutableScriptBuilder);
            Field[] fields = getFields(components.getClass());
            for (Field field : fields) {
                IgnoreParsing annotation = field.getAnnotation(IgnoreParsing.class);
                if (annotation == null) {
                    field.setAccessible(true);
                    Object value = field.get(components);
                    generateComponentScript(field, value, clientExecutableScriptBuilder);
                }
            }
            generateMethodCaller(components, clientExecutableScriptBuilder);
            return clientExecutableScriptBuilder.toString();
        } catch (Exception e) {
            log.error("Failed to generate client executable script for components: {}", components.getClass().getName(), e);
            throw e;
        }
    }

    private Field[] getFields(Class<?> clazz) {
        try {
            return fieldCache.get(clazz, clazz::getDeclaredFields);
        } catch (ExecutionException e) {
            log.error("Failed to get fields from cache for class: {}", clazz.getName(), e);
            return clazz.getDeclaredFields();
        }
    }

    /**
     * This will generate caller map of all components
     *
     * @param components
     * @param clientExecutableScriptBuilder
     */
    private void generateMethodCaller(ClientComponents components, StringBuilder clientExecutableScriptBuilder) {
        try {
            Map<String, Object> data = new HashMap<>();
            Field[] superClassFields = getFields(components.getClass().getSuperclass());
            String scriptedFieldsInfoMapName = Utility.getTemplatizedFieldName(superClassFields[0]);
            data.put(superClassFields[0].getName(), scriptedFieldsInfoMapName + GROOVY_BUILDER_METHOD_SIGNATURE);

            Field[] fields = getFields(components.getClass());
            for (Field field : fields) {
                IgnoreParsing annotation = field.getAnnotation(IgnoreParsing.class);
                if (annotation == null) {
                    data.put(field.getName(), Utility.getTemplatizedFieldName(field) + GROOVY_BUILDER_METHOD_SIGNATURE);
                }
            }
            char[] charArray = components.getClass().getSimpleName().toCharArray();
            charArray[0] = Character.toLowerCase(charArray[0]);
            String variableName = new String(charArray);

            clientExecutableScriptBuilder.append(GROOVY_BUILDER_METHOD_PREFIX);
            generateGroovyMapVariable(variableName, data, clientExecutableScriptBuilder);
        } catch (Exception e) {
            log.error("Failed to generate method caller for components: {}", components.getClass().getName(), e);
            throw e;
        }
    }

    /**
     * This will generate individual component groovy method
     *
     * @param field
     * @param value
     * @param clientExecutableScriptBuilder
     */
    private void generateComponentScript(Field field, Object value, StringBuilder clientExecutableScriptBuilder) {
        try {
            IgnoreParsing annotation = field.getAnnotation(IgnoreParsing.class);
            if (annotation == null) {
                // Use separate StringBuilder for component script
                StringBuilder scriptBuilder = new StringBuilder(INITIAL_SCRIPT_CAPACITY);
                String templatizedFieldName = Utility.getTemplatizedFieldName(field);
                
                // Use method chaining for better performance
                scriptBuilder.append(GROOVY_BUILDER_METHOD_PREFIX)
                           .append(templatizedFieldName)
                           .append(GROOVY_BUILDER_METHOD_SIGNATURE)
                           .append(GROOVY_BUILDER_METHOD_START_BRACES);

                if (value == null) {
                    scriptBuilder.append(GROOVY_BUILDER_RETURN_KEYWORD).append("null");
                } else if (value instanceof StaticComponentDetail) {
                    scriptBuilder.append(GROOVY_BUILDER_RETURN_KEYWORD);
                    StaticComponentDetail scd = (StaticComponentDetail) value;
                    Object data = scd.getValue().getData();
                    ParameterizedType cFieldGenericType = (ParameterizedType) field.getGenericType();
                    Class<?> genericTypeClass = (Class<?>) cFieldGenericType.getActualTypeArguments()[1];

                    if (data == null) {
                        scriptBuilder.append(data);
                    } else {
                        if (genericTypeClass.getSimpleName().equals(Map.class.getSimpleName()) ||
                            genericTypeClass.getSimpleName().equals(List.class.getSimpleName())) {
                            String singleLineJson = data.toString().replaceAll("[\r\n\t]+", " ");
                            scriptBuilder.append(GROOVY_BUILDER_JSON_SLURPER_PARSER)
                                       .append(GROOVY_BUILDER_OPEN_SMALL_BRACKET)
                                       .append(singleLineJson)
                                       .append(GROOVY_BUILDER_CLOSE_SMALL_BRACKET);
                        } else {
                            scriptBuilder.append(GROOVY_BUILDER_SINGLE_QUOTE)
                                       .append(data)
                                       .append(GROOVY_BUILDER_SINGLE_QUOTE);
                        }
                    }
                } else if (value instanceof ScriptedComponentDetail) {
                    ScriptedComponentDetail scd = (ScriptedComponentDetail) value;
                    String scriptContent = scd.getValue().getData();

                    // Replace evaluate() calls with customEvaluate() to use our cached implementation
                    if (scriptContent != null && scriptContent.contains("evaluate(")) {
                        scriptContent = scriptContent.replaceAll("\\bevaluate\\(", "customEvaluate(");
                    }

                    scriptBuilder.append(scriptContent);
                } else {
                    scriptBuilder.append(GROOVY_BUILDER_RETURN_KEYWORD)
                               .append(GROOVY_BUILDER_SINGLE_QUOTE)
                               .append(value)
                               .append(GROOVY_BUILDER_SINGLE_QUOTE);
                }
                scriptBuilder.append("\n")
                           .append(GROOVY_BUILDER_METHOD_END_BRACES);
                clientExecutableScriptBuilder.append(scriptBuilder);
            }
        } catch (Exception e) {
            log.error("Failed to generate component script for field: {}", field.getName(), e);
            throw e;
        }
    }

    /**
     * This will generate ScriptedFieldsInfoMap which defines the component if static or scripted
     *
     * @param components
     * @param clientExecutableScriptBuilder
     * @throws IllegalAccessException
     */
    private void generateScriptedFieldsInfoMap(ClientComponents components, StringBuilder clientExecutableScriptBuilder) throws IllegalAccessException {
        try {
            Field[] superClassFields = getFields(components.getClass().getSuperclass());
            String scriptedFieldsInfoMapName = Utility.getTemplatizedFieldName(superClassFields[0]);
            Map<String, Object> data = new HashMap<>();
            
            Field[] fields = getFields(components.getClass());
            for (Field field : fields) {
                IgnoreParsing annotation = field.getAnnotation(IgnoreParsing.class);
                if (annotation == null) {
                    field.setAccessible(true);
                    Object value = field.get(components);
                    data.put(field.getName(), value instanceof ScriptedComponentDetail);
                }
            }
            
            generateGroovyMethodWithMapVariable(clientExecutableScriptBuilder, scriptedFieldsInfoMapName, 
                                              GROOVY_BUILDER_SCRIPTED_FIELDS_INFO, data);
        } catch (Exception e) {
            log.error("Failed to generate scripted fields info map for components: {}", components.getClass().getName(), e);
            throw e;
        }
    }

    /**
     * This generates method with map variable with given data
     *
     * @param clientExecutableScriptBuilder
     * @param methodName
     * @param variablePrefix
     * @param data
     */
    private void generateGroovyMethodWithMapVariable(StringBuilder clientExecutableScriptBuilder,
                                                   String methodName,
                                                   String variablePrefix,
                                                   Map<String, Object> data) {
        try {
            clientExecutableScriptBuilder.append(GROOVY_BUILDER_METHOD_PREFIX)
                                       .append(methodName)
                                       .append(GROOVY_BUILDER_METHOD_SIGNATURE)
                                       .append(GROOVY_BUILDER_METHOD_START_BRACES);
            
            generateGroovyMapVariable(variablePrefix, data, clientExecutableScriptBuilder);
            
            clientExecutableScriptBuilder.append(GROOVY_BUILDER_METHOD_END_BRACES);
        } catch (Exception e) {
            log.error("Failed to generate Groovy method with map variable for method: {}", methodName, e);
            throw e;
        }
    }

    /**
     * This generates map variable with given data
     *
     * @param variablePrefix
     * @param data
     * @param clientExecutableScriptBuilder
     */
    private void generateGroovyMapVariable(String variablePrefix, Map<String, Object> data, StringBuilder clientExecutableScriptBuilder) {
        try {
            String variableName = variablePrefix + GROOVY_BUILDER_MAP_VARIABLE_NAME;
            clientExecutableScriptBuilder.append(variableName)
                                       .append(GROOVY_BUILDER_ASSIGN_OPERATOR)
                                       .append(GROOVY_BUILDER_MAP_DEFINE_SYNTAX);
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                clientExecutableScriptBuilder.append(variableName)
                                           .append(GROOVY_BUILDER_ASSIGN_MAP_KEY_VALUE_OPEN)
                                           .append(entry.getKey())
                                           .append(GROOVY_BUILDER_ASSIGN_MAP_KEY_VALUE_CLOSE)
                                           .append(GROOVY_BUILDER_ASSIGN_OPERATOR)
                                           .append(entry.getValue())
                                           .append("\n");
            }
            
            clientExecutableScriptBuilder.append(GROOVY_BUILDER_RETURN_KEYWORD)
                                       .append(variableName)
                                       .append("\n");
        } catch (Exception e) {
            log.error("Failed to generate Groovy map variable for prefix: {}", variablePrefix, e);
            throw e;
        }
    }

    /**
     * This adds import to script
     *
     * @param clientExecutableScriptBuilder
     */
    private void addImportsToClientExecutableScript(StringBuilder clientExecutableScriptBuilder) {
        clientExecutableScriptBuilder.append(GROOVY_BUILDER_IMPORT);

    }
}
