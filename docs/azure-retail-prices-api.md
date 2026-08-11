# Azure Retail Prices API — Integration Notes

## Endpoint

`https://prices.azure.com/api/retail/prices`

Public, unauthenticated REST API — no token or service principal required. This is distinct from
`management.azure.com`, which does require an authenticated identity (see Task 1.2 notes on the
Azure Monitor Metrics API).

## Query syntax

The API uses the OData `$filter` query parameter. curl does **not** automatically URL-encode query
strings, so the filter must either be passed as a single-quoted shell string with the OData expression
manually percent-encoded (spaces as `%20`, string literals wrapped in `%27...%27`), for example:

```bash
curl -s 'https://prices.azure.com/api/retail/prices?$filter=armRegionName%20eq%20%27southeastasia%27%20and%20armSkuName%20eq%20%27Standard_D2s_v4%27'
```

A double-quoted shell string containing a raw, unencoded `$filter=... and ...` expression (literal
spaces and apostrophes) was tested and returns an empty HTTP body, which fails JSON parsing
(`Expecting value: line 1 column 1 (char 0)`). Confirmed during testing on 2026-08-09.

## Key response fields

| Field | Meaning |
|---|---|
| `retailPrice` | Pay-as-you-go price per unit (see `unitOfMeasure`), in `currencyCode` |
| `unitPrice` | Same as `retailPrice` for non-tiered meters |
| `armSkuName` | The VM size string as used in ARM/CLI (e.g. `Standard_D2s_v4`). **Not unique to compute** — other services (e.g. Azure Managed Instance for Apache Cassandra) reuse the same string for their own meters |
| `skuName` | Short display name (e.g. `D2s v4`). Spot/Low Priority variants append a suffix (`D2s v4 Spot`, `D2s v4 Low Priority`) |
| `meterName` | Same pattern as `skuName`; this is the field to exact-match against to exclude Spot/Low Priority sibling meters |
| `productName` | Full product line name. Contains the word "Windows" for Windows-licensed variants; absent for Linux |
| `armRegionName` | Region filter value (e.g. `southeastasia`) |
| `serviceName` | Top-level service name (e.g. `Virtual Machines`). Required to exclude unrelated services that happen to reuse the same `armSkuName` |
| `priceType` | `Consumption` (pay-as-you-go) vs `Reservation` (1yr/3yr commitment) |
| `type` | `Consumption` vs Spot/Low-priority variant type |
| `effectiveStartDate` | Date this price took effect |
| `meterId` / `productId` / `skuId` | Internal identifiers, unique per meter row |
| `NextPageLink` (top-level, not per item) | Pagination cursor — see Pagination section below |
| `Count` (top-level) | Number of items returned in the current page |

## Filtering gotchas discovered during testing

Filtering only on `armRegionName` + `armSkuName` + `priceType eq 'Consumption'` is **not sufficient**
to isolate a single, unambiguous VM compute price. For `Standard_D2s_v4` in `southeastasia`, this
combination returned 4 rows:

1. `D2s v4 Spot` — Spot pricing
2. `D2s v4` — the correct on-demand Linux price
3. `D2s v4 Low Priority` — low-priority pricing
4. `Standard_D2s_v4` under service `Azure Managed Instance for Apache Cassandra` — an unrelated
   service that reuses the same `armSkuName`

The filter combination verified to return exactly one row — the standard, on-demand, Linux compute
price — is:

```
$filter = armRegionName eq 'southeastasia'
  and armSkuName eq 'Standard_D2s_v4'
  and priceType eq 'Consumption'
  and serviceName eq 'Virtual Machines'
  and meterName eq 'D2s v4'
  and contains(productName, 'Windows') eq false
```

Full curl command used:

```bash
curl -s 'https://prices.azure.com/api/retail/prices?$filter=armRegionName%20eq%20%27southeastasia%27%20and%20armSkuName%20eq%20%27Standard_D2s_v4%27%20and%20priceType%20eq%20%27Consumption%27%20and%20serviceName%20eq%20%27Virtual%20Machines%27%20and%20meterName%20eq%20%27D2s%20v4%27%20and%20contains(productName,%20%27Windows%27)%20eq%20false'
```

Key points:
- `serviceName eq 'Virtual Machines'` excludes non-compute services that reuse the same SKU string.
- `meterName eq 'D2s v4'` uses an **exact match**, not `contains`, to exclude the `Spot` and
  `Low Priority` suffixed variants.
- `contains(productName, 'Windows') eq false` excludes the Windows-licensed price row.

Verified: this exact filter pattern returns exactly 1 item for `Standard_D2s_v4`, `Standard_D2s_v3`,
and `Standard_D2s_v5`, all in `southeastasia`.

## Pagination

The API pages results at 1000 items per page. A broad query (region + service only, no SKU filter)
confirms this:

```bash
curl -s 'https://prices.azure.com/api/retail/prices?$filter=armRegionName%20eq%20%27southeastasia%27%20and%20serviceName%20eq%20%27Virtual%20Machines%27' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('Count:', d['Count']); print('NextPageLink:', d['NextPageLink'])"
```

Result:
```
Count: 1000
NextPageLink: https://prices.azure.com:443/api/retail/prices?$filter=armRegionName%20eq%20%27southeastasia%27%20and%20serviceName%20eq%20%27Virtual%20Machines%27&$skip=1000
```

`NextPageLink` is a **complete, directly callable URL** with `$skip` already appended — no manual
offset arithmetic is needed. Pagination handling logic:

1. Call the current URL.
2. Read the top-level `NextPageLink` field.
3. If it is not `null`, call that URL next.
4. Repeat until `NextPageLink` is `null`.

When a query's result set fits in a single page (e.g. the exact single-SKU filter above), `NextPageLink`
is `null` and `Count` reflects the actual number of items returned (in that case, `1`).

## SKUs validated

| SKU | Role in project | On-demand Linux price (`southeastasia`) | Notes |
|---|---|---|---|
| `Standard_D2s_v4` | Current-generation (test VM) | $0.12/hr | Substituted for `Standard_D2s_v5` in Task 1.1 due to zero quota for the `DSv5` family on this subscription |
| `Standard_D2s_v3` | Older-generation (test VM) | Validated, see `pricing-d2sv3.json` | |
| `Standard_D2s_v5` | Documented for comparison only | Validated via API | Not provisioned as a VM — subscription quota for `Standard DSv5 Family vCPUs` is 0 in `southeastasia` |

Raw captured responses for reference: `pricing-d2sv4.json`, `pricing-d2sv3.json`, `pricing-d2sv5.json`
(captured 2026-08-09, `southeastasia` region).
