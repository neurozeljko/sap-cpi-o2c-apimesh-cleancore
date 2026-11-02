import com.sap.gateway.ip.core.customdev.util.Message
import java.time.Instant

def Message processData(Message message) {
    def properties = message.getProperties()
    String cachedToken = properties.get("S4_GLOBAL_CSRF_TOKEN") as String
    String cachedCookie = properties.get("S4_GLOBAL_SESSION_COOKIE") as String
    def tokenTimestamp = properties.get("S4_CSRF_TOKEN_TIMESTAMP")

    long currentTime = Instant.now().getEpochSecond()
    boolean isCacheValid = false

    if (cachedToken != null && cachedCookie != null && tokenTimestamp != null) {
        try {
            long tokenAge = currentTime - Long.parseLong(tokenTimestamp.toString())
            if (tokenAge >= 0 && tokenAge < 1500) {
                isCacheValid = true
            }
        } catch (Exception e) {
            isCacheValid = false
        }
    }

    if (isCacheValid) {
        message.setHeader("x-csrf-token", cachedToken)
        message.setHeader("Cookie", cachedCookie)
        message.setProperty("CSRF_Cache_Hit", "TRUE")
    } else {
        message.setProperty("CSRF_Cache_Hit", "FALSE")
        message.setHeader("x-csrf-token", "Fetch")
    }

    return message
}
