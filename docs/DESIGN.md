# Cydev Mod - Design Decisions

Target: Minecraft 1.21.11 Java Edition, Fabric, Java 21 (runs on JDK 25).
Concept: a Cities: Skylines II style systems simulation inside Minecraft.

## Decisions
- Scope: full simulation, all 8 phases.
- Citizens: simulated records plus visible villager entities.
- Buildings: players build their own; government projects (roads, pipelines,
  public buildings) are built by the Mayor's administration.
- Server-side simulation, works in single-player and multiplayer.
- Sim logic is plain Java (no Minecraft types); `world/` holds the adapters.

## Government
- Mayor elected by all citizens; anyone may file candidacy.
- Tenure: 1 real-life month (24 x 30 days = 720 h = 51,840,000 ticks).
  Use wall-clock time, not game ticks.
- Mayor score / progress visible to everyone (HUD + screen).
- Mayor appoints advisors, secretary and the Council (Office of the Mayor).
- Permits: construction, water pipelines and other works need Mayor approval.
  Mayor may inspect, stop or ban works within their administrative area.

## Expansion / federation
- Mayor can select an area to carve out a new city and call an election there;
  the original mayor stays mayor of the original city
  (e.g. Mayor of ABC, Mayor of XYZ).
- Mayors can discuss further expansion and form a federation of cities.

## Phases
1 Foundation  2 Roads+zoning  3 Utilities  4 Demand/growth
5 Citizens+economy  6 Traffic  7 Services  8 UI+polish
Government/election/expansion runs alongside phases 1-5.

## Visual style (decided)
Goal: realistic houses, roads and environment, not the default blocky look.
- A: realistic textures + detailed custom block models (thin slabs for roads
  with lane markings and kerbs; multi-part houses with slanted roofs, window
  frames, chimneys, fences). Stays grid-aligned.
- D: recommend an Iris shader pack for smooth lighting, shadows, water and
  reflections. Players install it themselves; the mod only documents it.
- Not doing (for now): custom 3D prefab meshes and spline roads.
- Resource pack source lives in `resourcepack/`; mod models/textures in
  `src/main/resources/assets/cydev/`.

## Property market (decided)
- Anyone can own land plots and houses (deeds recorded per plot).
- Owners can list land or a house for sale; buyers pay through the economy
  system; deed transfers to the buyer. Zoned city land sale may need Mayor
  approval; property tax goes to the city treasury.
- Packages: `property/{land,ownership,market,deed,tax}`.

## Visual style (decided)
Goal: realistic, natural-looking houses, roads and environment, not the
default blocky look.
- A: high-resolution realistic textures (target 256x or 512x, PBR-ready
  normal/specular maps where supported) plus detailed custom block models:
  thin slabs for roads with lane markings and kerbs; multi-part houses with
  slanted roofs, window frames, chimneys, fences; natural-looking terrain,
  grass, foliage and water textures. Stays grid-aligned.
- D: recommend an Iris shader pack for smooth lighting, shadows, water and
  reflections. Players install it themselves; the mod only documents it.
- Not doing (for now): custom 3D prefab meshes and spline roads.
- Sources: `resourcepack/` (full-pack textures) and
  `src/main/resources/assets/cydev/` (mod models/textures/sounds).
- Textures must be original or properly licensed (CC0/CC-BY); credit in README.

## Property market (decided)
- Anyone can own land plots and houses (deed recorded per plot).
- Owners can list land or a house for sale; buyers pay through the economy
  system and the deed transfers. Selling zoned city land may need Mayor
  approval; property tax goes to the city treasury.
- Packages: `property/{land,ownership,market,deed,tax}`.

## Vehicles (decided)
- Drivable and AI cars (citizen commutes) as custom entities with realistic
  models, textures and engine sounds.
- Citizens and players can own cars; registration/licence, fuel, and road
  rules tie into the traffic and government systems.
- Packages: `vehicle/{entity,ownership,fuel,license}`; assets under
  `models/entity/vehicle`, `textures/entity/vehicle`, `sounds/vehicle`.
- Open question: "cars and their assess" read as assets (models, textures,
  sounds). Correct me if you meant something else.
