import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {
    def body = message.getBody(String)
    def jsonSlurper = new JsonSlurper()
    def input = jsonSlurper.parseText(body)

    def odataV4Payload = [
        "SalesOrderType"          : input.SalesOrderType ?: "OR",
        "SalesOrganization"       : input.SalesOrganization ?: "1010",
        "DistributionChannel"     : input.DistributionChannel ?: "10",
        "OrganizationDivision"    : input.OrganizationDivision ?: "00",
        "SoldToParty"             : input.SoldToParty,
        "PurchaseOrderByCustomer" : input.PurchaseOrderByCustomer ?: input.IdempotencyKey,
        "YY1_LoyaltyPts_SDH"      : input.LoyaltyPoints ?: 0,
        "to_Item"                 : []
    ]

    input.to_Item?.eachWithIndex { item, index ->
        odataV4Payload.to_Item << [
            "SalesOrderItem"        : String.format("%06d", (index + 1) * 10),
            "Material"              : item.Material,
            "RequestedQuantity"     : item.RequestedQuantity,
            "RequestedQuantityUnit" : item.RequestedQuantityUnit ?: "PCE"
        ]
    }

    message.setBody(JsonOutput.toJson(odataV4Payload))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("Accept", "application/json")
    return message
}
