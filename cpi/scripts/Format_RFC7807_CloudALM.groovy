import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant

def Message processData(Message message) {
    def ex = message.getProperty("CamelExceptionCaught")
    def correlationId = message.getHeader("X-Correlation-ID", String) ?: UUID.randomUUID().toString()
    def idempotencyKey = message.getProperty("Order_Idempotency_Key") ?: "N/A"
    
    int statusCode = 500
    String errorTitle = "Internal Processing Error"
    String errorDetail = ex ? ex.getMessage() : "Unknown ERP processing exception"

    if (errorDetail.contains("Duplicate entry") || errorDetail.contains("DataStore")) {
        statusCode = 409
        errorTitle = "Conflict / Duplicate Request"
        errorDetail = "Order with Idempotency-Key [${idempotencyKey}] has already been processed or lock is active."
    } else if (errorDetail.contains("401") || errorDetail.contains("Unauthorized")) {
        statusCode = 401
        errorTitle = "Backend Authentication Failure"
    }

    def rfc7807Response = [
        "type"          : "https://api.retail.mesh/errors/${statusCode}",
        "title"         : errorTitle,
        "status"        : statusCode,
        "detail"        : errorDetail,
        "instance"      : "/retail/v1/orders/${correlationId}",
        "correlationId" : correlationId,
        "timestamp"     : Instant.now().toString()
    ]

    def cloudAlmAlert = [
        "specversion"     : "1.0",
        "type"            : "sap.cloud.alm.integration.alert",
        "source"          : "/cpi/tenant/it-cpi026/iflow/O2C_SalesOrder_Sync_API",
        "id"              : UUID.randomUUID().toString(),
        "time"            : Instant.now().toString(),
        "datacontenttype" : "application/json",
        "data"            : [
            "severity"     : statusCode >= 500 ? "CRITICAL" : "WARNING",
            "correlationId": correlationId,
            "errorCode"    : statusCode,
            "errorMessage" : errorDetail,
            "component"    : "CPI_RUNTIME_WORKER"
        ]
    ]

    message.setProperty("CloudALM_Alert_Payload", JsonOutput.toJson(cloudAlmAlert))
    message.setHeader("CamelHttpResponseCode", statusCode)
    message.setHeader("Content-Type", "application/problem+json")
    message.setBody(JsonOutput.toJson(rfc7807Response))
    return message
}
