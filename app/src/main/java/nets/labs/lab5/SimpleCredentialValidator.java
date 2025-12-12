package nets.labs.lab5;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class SimpleCredentialValidator implements CredentialValidator {
    private static final Logger logger = LogManager.getLogger(SimpleCredentialValidator.class);
    
    // Пример: храним в памяти. В реальном приложении можно заменить на БД или файл
    private final Map<String, String> credentials = Map.of(
        "admin", "admin123",
        "user", "password123",
        "test", "test"
    );

    @Override
    public boolean validate(String username, String password) {
        if (username == null || password == null || 
            username.isEmpty() || password.isEmpty() ||
            username.length() > 255 || password.length() > 255) {
            logger.warn("Неверный формат логина/пароля");
            return false;
        }
        
        boolean isValid = credentials.containsKey(username) && 
                         credentials.get(username).equals(password);
        
        logger.debug("Аутентификация пользователя '{}': {}", username, 
                    isValid ? "УСПЕХ" : "НЕУДАЧА");
        return isValid;
    }
}