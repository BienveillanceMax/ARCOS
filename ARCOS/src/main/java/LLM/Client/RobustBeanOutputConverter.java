package LLM.Client;

import LLM.Utils.JsonSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.NonNull;

public class RobustBeanOutputConverter<T> extends BeanOutputConverter<T> {

    private static final Logger logger = LoggerFactory.getLogger(RobustBeanOutputConverter.class);
    private final ObjectMapper objectMapper;
    private final Class<T> targetClass;

    public RobustBeanOutputConverter(Class<T> targetClass, ObjectMapper objectMapper) {
        super(targetClass, objectMapper);
        this.targetClass = targetClass;
        this.objectMapper = objectMapper;
    }

    @Override
    @NonNull
    public T convert(@NonNull String text) {
        String sanitizedText = JsonSanitizer.sanitize(text);

        try {
            return super.convert(sanitizedText);
        } catch (Exception e) {
            logger.warn("Initial conversion failed. Text: '{}'. Sanitized: '{}'. Error: {}", text, sanitizedText, e.getMessage());

            // Second chance: sometimes the sanitizer might be too aggressive or not aggressive enough.
            // Or maybe the error is just a small syntax error that Jackson + lenient mode can handle if we strip control chars manually?
            // Actually, super.convert uses the objectMapper which is already configured to be lenient.

            // Retrying with 'text' again is useless.
            // We could try to log detailed error and rethrow, or try one more fallback if we had one.
            throw e;
        }
    }
}
