# Item Logistics MVP (Server-Side Core)

This package contains a mixin-independent item logistics core you can build out before intercepting vanilla inventory mutations.

## Included pieces

- `api`: ingress and system API contracts
- `graph`: node/edge transport topology model
- `planner`: shortest-path route planning and bottleneck throughput
- `queue`: immediate/deferred transfer queues
- `runtime`: tick processor, retry handling, diagnostics, fail-open behavior
- `demo`: `ItemLogisticsSelfTest` tiny runnable harness

## Transport families (current behavior)

- `CONVEYOR`: can extract from inventory ports without pumps, supports channel masks for filtering/splitting/combining paths, and rejects vertical edges.
- `TUBE`: higher-throughput routes, can use vertical edges, and can be configured to require a pump on the resolved route.
- `NEUTRAL`: inventory/router nodes/edges that can participate in either family.

Inventory ports are optional for conveyor extraction: requests can allow direct-adjacent conveyor endpoints (`allowDirectInventoryAdjacency=true`) and only require ports when advanced port-only behavior is desired (`requireInventoryPort=true`).

Per-endpoint policy placeholders are available via `policy` + `runtime/ItemEndpointPolicyRegistry` (`NONE`, `FILTER`, `SPLIT`, `COMBINE`). Ingress can derive source/destination port requirements independently from these policies.

Server config key: `item_conveyor_require_port_for_advanced` can globally force port-required conveyor ingress while advanced port logic is being tested.

Channel masks are bitmasks (`-1` means all channels). Example: mask `0b0010` accepts channel `1` only.

## Quick try

Compile and run only this subsystem (without compiling the whole mod):

```powershell
Set-Location "C:\Users\garre\Documents\GitHub\ResourcesRefueled"
$src = Get-ChildItem -Path ".\src\main\java\videogoose\resourcesreorganized\logistics\item" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path ".\build\tmp\item-logistics-selftest" | Out-Null
javac -d ".\build\tmp\item-logistics-selftest" $src
java -cp ".\build\tmp\item-logistics-selftest" videogoose.resourcesreorganized.logistics.item.demo.ItemLogisticsSelfTest
```

## Next integration step

Wire `ItemMutationIngress.tryRouteMutation(...)` from inventory mixin hooks, and add a live StarMade inventory adapter implementation for `ItemTransferExecutor`.

