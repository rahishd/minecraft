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

## Nepal local-government model (supersedes earlier "Mayor approves everything")
Source: Nepal_Municipal_Ward_Office_Minecraft_Mod_Design_Brief.pdf. Gameplay
abstraction only - not legally authoritative; all rules are configurable.
- One municipality contains wards. A ward is a service office inside the
  municipality, not a separate government, with no independent budget.
- Elected: Mayor (+ Deputy Mayor, simplified here as the runner-up) and each
  Ward Chairperson. Term: 30 real days, per decision above.
- Appointed staff: CAO (administrative lead, not elected), Admin, Engineer,
  Revenue, Accounts officers, Inspector (by the Mayor); Ward Secretary and
  committee members (by the Ward Chairperson).
- The Mayor is NOT the approver of every request. Citizen services follow
  per-service authority rules (`AuthorityRules`): ward staff issue simple
  services directly, otherwise refer to the responsible department.
- Projects: Ward Chair submits -> Engineer technical review -> Accounts budget
  check -> Mayor (Executive) final approval -> procurement -> work ->
  Inspector sign-off. Nothing can be skipped; insufficient funds defer the
  project with a recorded reason.
- Every status change goes to an audit log (actor, role, record, old/new
  status, time, reason). Staff cannot process their own case.

## Status (as of this commit)
Done + unit tested (pure Java): roles, wards, elections for mayor and ward
chair, appointments, service-case workflow, project/budget workflow, audit.
Persisted: cities, wards, elections, appointments.
Not yet: persistence of cases/projects/budget/audit, GUI/NPC counters, request
and project boards, citizen satisfaction score, sample scenario data.

## Status update: administration layer complete (commands only, no GUI)
Added and unit tested (29 tests incl. save/load round trips):
- Everything persisted: cities, wards, elections, cases, projects, budgets, audit log, federations.
- Municipal Assembly = Mayor + Deputy + Ward Chairs; a budget line needs a majority
  before any project can use it.
- Stop / lift / ban orders on works (Mayor, Inspector, own-ward Chair; ban = Mayor only).
- Judicial matters: DISPUTE_MEDIATION cases go to the Deputy Mayor.
- Administration score (0-100) from ratings, resolution rate, timeliness, delivery.
- Land claims (rectangles), carving a new city out of a city (new election, original
  Mayor stays), federations (majority of member mayors + invited mayor accepts).
- Sample data: `/cydev scenario load` (operator) builds the "Demo" city.

Command map: `case file|evidence|move|info|list|rate`, `project propose|move|budgetcheck|
contractor|progress|stop|lift|ban|info|list`, `budget add|list|vote`, `score`, `audit`,
`land claim|here`, `expand`, `federation create|propose|vote|accept|list`,
plus `city ...` and `ward ...` from earlier.

Still to do: GUI/NPC counters and boards, physical enforcement of land/stop orders in the
world (block placement), configurable authority rules (not persisted), the real
Municipal Assembly election, then roads/zoning, utilities, economy, traffic, services,
property market, vehicles, citizens, HUD.
