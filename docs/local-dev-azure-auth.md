# Local Development Azure Authentication

## Problem

The Collector Service authenticates to Azure Monitor using `DefaultAzureCredential`
from the `azure-identity` SDK. In production (or on the provisioned test VMs), this
resolves via a **System-Assigned Managed Identity**, which only works for code
running on that specific Azure resource — it has no meaning on a developer's own
machine, since there is no Instance Metadata Service (IMDS) endpoint to query
outside of Azure.

This meant local development had no working authentication path out of the box.

## Solution: Azure CLI credential fallback

`DefaultAzureCredential` does not use a single credential source — it tries a
chain of credential providers in order, stopping at the first one that succeeds:

1. Environment variables (`AZURE_CLIENT_ID` / `AZURE_CLIENT_SECRET` / etc.)
2. Workload Identity
3. Managed Identity
4. IntelliJ credential
5. **Azure CLI credential**
6. Azure PowerShell credential
7. Interactive browser (last resort)

On a developer machine with an active `az login` session, the credential chain
falls through to **Azure CLI credential**, which silently reuses that session's
token — no service principal secret, no interactive prompt, no additional
configuration required in code.

## Setup: grant your own Azure AD user access

The Managed Identity's `Monitoring Reader` role assignment (see Task 1.2) only
covers the VM's own identity — it does not grant your personal Azure AD account
any access. To authenticate locally, your own user needs the same role, scoped to
the same resource group.

**1. Find your signed-in user's object ID:**

```bash
az ad signed-in-user show --query id -o tsv
```

**2. Grant `Monitoring Reader`, scoped to the test resource group:**

```bash
az role assignment create \
  --assignee <your-object-id> \
  --role "Monitoring Reader" \
  --scope /subscriptions/<subscription-id>/resourceGroups/rg-cloud-cost-platform-dev
```

Same least-privilege principle as the Managed Identity assignment: scoped to the
resource group only, read-only role, not subscription-wide `Contributor`.

## Verifying it works

A minimal standalone Java program (outside of Spring Boot, to isolate
authentication from any framework complexity) confirms the credential chain
resolves correctly:

```java
TokenCredential credential = new DefaultAzureCredentialBuilder().build();

MetricsQueryClient client = new MetricsQueryClientBuilder()
    .credential(credential)
    .buildClient();

Response<MetricsQueryResult> response = client.queryResourceWithResponse(
    resourceId,
    List.of("Percentage CPU"),
    new MetricsQueryOptions()
        .setTimeInterval(new QueryTimeInterval(Duration.ofHours(1))),
    Context.NONE
);
```

Running this on a machine with `az login` active and the role assignment above in
place returns a real `MetricsQueryResult` with no authentication error. If the
target VM is deallocated, the metric values themselves come back as `null` for
every timestamp — this is expected and unrelated to authentication; it simply
means there is no CPU activity to report during that window. A cleanly structured
response with `null` values still confirms auth succeeded, since an
authentication failure raises an exception well before any response is returned.

## Known API detail: `azure-monitor-query` 1.5.x

`MetricsQueryClient` does not expose a `queryResource(resourceId, metricNames,
options)` overload — passing `MetricsQueryOptions` requires
`queryResourceWithResponse(resourceId, metricNames, options, Context)`, which
returns a `Response<MetricsQueryResult>`. Call `.getValue()` to obtain the actual
result. The time-interval type is `com.azure.monitor.query.models.QueryTimeInterval`,
not `com.azure.core.util.TimeInterval` (the latter does not exist in this SDK
version).

## Summary for the Collector Service

In `collector-service`, `AzureMonitorClient` should build its credential with:

```java
TokenCredential credential = new DefaultAzureCredentialBuilder().build();
```

No further configuration is needed. On the provisioned Azure VM (if the service
is ever deployed there), this resolves via Managed Identity. On a developer's own
machine, it resolves via Azure CLI credential, provided:

- `az login` has been run and the session has not expired
- The signed-in user has been granted `Monitoring Reader` on the target resource
  group (see Setup above)

No credentials of any kind — client secrets, connection strings, or otherwise —
need to be stored in `.env`, application config, or source code for Azure Monitor
access, in either environment.