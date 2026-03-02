package com.flipkart.drift.worker.translator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.flipkart.drift.worker.exception.GroovyException;
import com.flipkart.drift.commons.utils.Utility;
import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import groovy.lang.*;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;
import static groovy.lang.GroovyShell.DEFAULT_CODE_BASE;

@Slf4j
public class GroovyTranslator {
    /**
     * Note: Below class has been imported from journey code base as it is.
     */
    private static final GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
    private static final String GROOVY_CLASS_FORMAT = "K-%s";
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Cache<String, Class<?>> groovyClassCache = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)  // Uses LRU (Least Recently Used) eviction policy by default
            .removalListener(notification -> log.warn("Groovy class cache evicting entry: {}", notification.getKey()))
            .build();

    /**
     * @param groovyScript The script content
     * @return Unique filename that follows Java class naming conventions
     * @throws UnsupportedEncodingException If URL encoding fails
     */
    private static String getFileName(String groovyScript) throws UnsupportedEncodingException {
        if (groovyScript.length() > 50) {
            // Prefix MD5 hash with 'G' to ensure valid Java class name (cannot start with number)
            return "G" + Utility.getMD5(groovyScript);
        } else {
            return URLEncoder.encode(String.format(GROOVY_CLASS_FORMAT, groovyScript.replace(" ", "`")), Charsets.UTF_8.name());
        }
    }

    /**
     * @param groovyScript The script to compile
     * @return Compiled Groovy class
     * @throws GroovyException If compilation fails
     */
    private static Class createGroovyClass(String groovyScript) throws GroovyException {
        try {
            String fileName = getFileName(groovyScript);
            return groovyClassCache.get(fileName, () -> {
                log.info("groovyClassCache miss: {}", fileName);
                GroovyCodeSource gcs = AccessController.doPrivileged((PrivilegedAction<GroovyCodeSource>)
                        () -> new GroovyCodeSource(groovyScript, fileName, DEFAULT_CODE_BASE));
                return groovyClassLoader.parseClass(gcs);
            });
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to generate filename for Groovy script", e);
            throw new GroovyException("Failed to generate filename for Groovy script", e);
        } catch (Exception e) {
            log.error("Failed to compile Groovy script: {}", groovyScript, e);
            throw new GroovyException("Failed to compile Groovy script", e);
        }
    }

    /**
     * @param groovyScript The script to execute
     * @param data         The input data
     * @return The script execution result
     * @throws GroovyException If translation fails
     */
    public static Object translate(String groovyScript, JsonNode data) throws GroovyException {
        try {
            Class groovyClass = createGroovyClass(groovyScript);
            Map<String, Object> dataMap = MAPPER.convertValue(data, new TypeReference<HashMap<String, Object>>() {
            });
            Script script = (Script) groovyClass.newInstance();
            Binding binding = new Binding(dataMap);
            binding.setVariable("logger", log);

            // Add customEvaluate method that uses caching to avoid compilation in runtime
            binding.setVariable("customEvaluate", new groovy.lang.Closure(script) {
                @Override
                public Object call(Object scriptContent) {
                    try {
                        String scriptString = scriptContent.toString();
                        log.info("Executing dynamic script via custom evaluate method, script length: {}", scriptString.length());
                        Script dynamicScript = (Script) createGroovyClass(scriptString).newInstance();
                        dynamicScript.setBinding(binding);
                        return dynamicScript.run();
                    } catch (Exception e) {
                        log.error("Error in custom evaluate method", e);
                        throw new GroovyException("Error in custom evaluate method", e);
                    }
                }
            });
            
            script.setBinding(binding);
            return script.run();
        } catch (Throwable e) {
            log.error("Error while transformation using groovy for groovyScript: {}", groovyScript, e);
            throw new GroovyException("Error while transformation using groovy for groovyScript", e);
        }
    }
}