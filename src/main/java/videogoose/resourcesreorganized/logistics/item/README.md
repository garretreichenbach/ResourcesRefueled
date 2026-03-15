# Item Logistics MVP (Server-Side Core)

This package contains a mixin-independent item logistics core you can build out before intercepting vanilla inventory mutations.

## Included pieces

- `api`: ingress and system API contracts
- `graph`: node/edge transport topology model
- `planner`: shortest-path route planning and bottleneck throughput
- `queue`: immediate/deferred transfer queues
- `runtime`: tick processor, retry handling, diagnostics, fail-open behavior
- `demo`: `ItemLogisticsSelfTest` tiny runnable harness

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

