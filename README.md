# Enterprise Integration Architecture: Omni-Channel O2C API Mesh & Clean Core (Global Retail)

![SAP Integration Suite](https://img.shields.io/badge/SAP%20Integration%20Suite-Cloud%20Integration%20%7C%20APIM-0a85ea.svg)
![SAP S/4HANA](https://img.shields.io/badge/SAP%20S%2F4HANA-OData%20V4%20Clean%20Core-E35205.svg)
![Security](https://img.shields.io/badge/Security-Zero--Trust%20mTLS%201.3%20%7C%20Principal%20Propagation-green.svg)
![Compliance](https://img.shields.io/badge/Governance-Clean%20Core%20In--App%20Extensibility-blueviolet.svg)
![Resilience](https://img.shields.io/badge/Resilience-Distributed%20Idempotency%20%7C%20Global%20CSRF%20Cache-critical.svg)

## Executive Summary

This enterprise integration architecture establishes a mission-critical, low-latency, and resilient Order-to-Cash (O2C) synchronous gateway bridging omni-channel frontends (Salesforce CRM, Headless E-Commerce) with SAP S/4HANA Cloud / Private Edition.

Governed strictly by **SAP's Clean Core Paradigm**, the architecture decouples client ingestion from enterprise ledger updates. By combining edge traffic governance via SAP API Management (APIM), a distributed idempotency lock engine, global CSRF token/session caching, and short-lived identity propagation, the system eliminates traditional ERP contention, guarantees sub-300ms SLA during flash retail events (e.g., Black Friday), and preserves 100% auditability down to the individual business caller without custom ABAP modifications.

---

## Architecture Overview

### High-Level Topology

```text
[Omni-Channel Storefront / Salesforce CRM]
       |
       | HTTPS POST /retail/v1/orders
       | (Headers: Authorization: Bearer <JWT>, Idempotency-Key, X-Correlation-ID)
       v
[SAP API Management (APIM Edge Gateway)]
  |-- Policy 1: JSON Threat Protection (Container depth <= 10, prevents parser exhaustion)
  |-- Policy 2: Spike Arrest (Rate: 50 req/min traffic smoothing against flash spikes)
  `-- Policy 3: VerifyJWT (Signature & audience validation against SAP Cloud Identity Services)
       |
       | Mutual TLS (mTLS 1.3) + Sanitized Context
       v
[SAP Cloud Integration (CPI) - Runtime Engine]
  |
  |-- 1. Distributed Idempotency Lock (CPI Global Data Store)
  |        |-- Duplicate Detected ---> Instant Return HTTP 409 Conflict (RFC 7807)
  |        `-- Unique Transaction ---> Atomic Lock Acquired & Execution Proceeds
  |
  |-- 2. Global CSRF Token & Session Cache Engine (Groovy Processing)
  |        |-- Cache Hit (Token age < 25m) ---> Inject Active x-csrf-token & Set-Cookie
  |        `-- Cache Miss (Expired/Null)   ---> Local Subprocess: S/4HANA Fetch & Populate
  |
  |-- 3. Clean Core Payload Orchestration (Prepare OData V4 + In-App Extensibility)
  |        `-- Maps upstream JSON into A_SalesOrder schema + YY1_LoyaltyPts_SDH
  |
  |-- 4. Identity-Aware Outbound Transaction (OData V4 Adapter)
  |        `-- Reverse-Invoke Secure Tunnel via SAP Cloud Connector (TLS 1.3)
  |            `-- Principal Propagation (SAML Bearer Assertion -> SY-UNAME User Context)
  |
  `-- [Exception Handling & Observability Subprocess]
           |-- Formats RFC 7807 Problem Details (Uniform HTTP 4xx / 5xx JSON response)
           `-- Emits Async OpenTelemetry CloudEvent to SAP Cloud ALM (PagerDuty / Jira Trigger)
       |
       v
[SAP S/4HANA Enterprise Core (Clean Core Architecture)]
  `-- Standard Service: /sap/opu/odata4/sap/api_salesorder/srvd_a2x/sap/salesorder/0001
      `-- Strictly bypasses legacy BAPIs/RFCs and zero Z-table schema contamination.
