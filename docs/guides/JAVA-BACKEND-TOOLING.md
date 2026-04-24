# Java backend tooling (Maven Wrapper + optional local JDK)

Use this when the product **backend** is **Spring Boot** or any **Maven**-based JVM service. Goal: developers can build and run **without** a global Maven install on `PATH`, and optionally **without** a global JDK on `PATH`.

---

## Maven Wrapper (committed — same idea as Spring Boot)

Place the **Maven Wrapper** at the **backend root**: the directory that contains **`pom.xml`** (e.g. `backend/`, `services/api/` — your repo’s name).

**Include in git** (standard Spring Initializr / `mvn -N wrapper:wrapper` layout):

- `mvnw` and `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.properties`
- `.mvn/wrapper/maven-wrapper.jar` (or the variant your wrapper version uses)

Optional, still in git:

- `.mvn/jvm.config` — JVM flags for all Maven invocations on this project
- `.mvn/maven.config` — default Maven CLI options

Developers run **`./mvnw`** (Unix) or **`mvnw.cmd`** (Windows) from that directory. The wrapper downloads a pinned Maven distribution to the user machine’s wrapper cache unless you point `distributionUrl` elsewhere.

### JVM before Maven (no “fill placeholder after Maven installs”)

The wrapper **starts a JVM first** (to run `maven-wrapper.jar` and then Maven). So you **cannot** defer installing or selecting a JDK until “after Maven is installed” in the sense of the first successful **`mvnw`** run: you need **some** JDK (global install or **`backend/.jdk`**) available **before** the wrapper can download the pinned Maven distribution.

### Committed “placeholder” path for Java — what works

**`maven-wrapper.properties`** (and **`.mvn/jvm.config`**) do **not** define the JDK install directory. The usual contract is **`JAVA_HOME`** (JDK home, not `…/bin`) or, on many setups, **`JAVACMD`** pointing at `java` / `java.exe`.

Ways to get a stable path without typing it every time:

| Approach | Idea |
|----------|------|
| **Fixed local dir** | Use **`backend/.jdk/`** as the only local path (no placeholder file). After **`fetch-local-jdk`**, set **`JAVA_HOME`** to that folder (shell, IDE, or **`direnv`**). |
| **Thin bootstrap script** | One script runs **`fetch-local-jdk`** if **`backend/.jdk/bin/java`** is missing, then sets **`JAVA_HOME`** and invokes **`mvnw`** — still env-based, not read by Maven from a properties file. |
| **Prefer `.jdk` inside `mvnw`** (optional) | Some teams prepend **`mvnw` / `mvnw.cmd`** with: if **`$BASE_DIR/.jdk/bin/java`** exists, **`export JAVA_HOME=…`** before the rest of the script. That encodes the path once in the wrapper; merge carefully when upgrading the wrapper. |
| **Maven Toolchains** | **`toolchains.xml`** maps a JDK **version** to a **literal** path on each machine — useful for multi-JDK, not a single cross-platform placeholder file. |

There is **no** standard Maven feature that reads a committed placeholder like **`@JAVA_HOME@`** in a properties file and substitutes it **after** the Maven zip is downloaded.

---

## Optional local JDK directory `.jdk/` (not in git)

Some teams prefer **not** to rely on a JDK installed globally. For that, reserve a directory **next to `pom.xml`** (same backend root), conventionally **`.jdk/`**, and unpack a vendor JDK there (e.g. Eclipse Temurin) **locally**.

**Do not commit `.jdk/`**: it is large, OS- and architecture-specific, and often unnecessary in CI.

**Scaffold / agent behavior:** After `backend/.jdk/` is on **`.gitignore`**, the agent **must** try to **populate** it in the scaffold pass whenever **`java` is missing or the wrong major version** for `pom.xml` (run **`fetch-local-jdk`**, per **`docs/templates/2. SCAFFOLD-GENERATOR-PROMPT.md`**). Copying the scripts alone is **not** enough unless execution was forbidden; if execution failed or was skipped, **`docs/output/SCAFFOLD-SUMMARY.md`** must record why and the exact follow-up commands. Ways to populate (pick one per team; document in backend **README**):

1. **Download (preferred for automation):** Commit **`backend/scripts/fetch-local-jdk.ps1`** and **`fetch-local-jdk.sh`** (canonical copies live in this repo under **`docs/templates/backend-scripts/`**). They download a pinned **Eclipse Temurin** build for the **current OS/arch** via the Adoptium API, unpack into **`backend/.jdk/`**, and exit. The agent runs the appropriate script when the environment allows network + archive unpack. No JDK binaries live in git.
2. **Copy from a local-only path:** You can keep an unpacked JDK somewhere **outside** git, or under a path that is **gitignored** (e.g. `docs/.local/jdk/` — add that pattern to `.gitignore`). The agent can **copy** from there into `backend/.jdk/` **only if that source folder already exists on the machine** running the agent. That **does work** for your own workspace; it does **not** help teammates or CI unless they recreate the same layout. **Do not commit** a full JDK under `docs/` (or anywhere): it bloats the repo, binds one OS/arch, and duplicates what Temurin already hosts.

**Minimum if populate is impossible:** Still add **`.gitignore`**, **backend `README`** (version + download + target path `backend/.jdk/bin/java`), and optionally the fetch script so the next `pwsh`/`bash` run fills `.jdk/` without the agent.

1. Add **`.jdk/`** to **`.gitignore`** at repo root or under the backend path, for example:
   - `backend/.jdk/`
2. In the **backend `README`**, document:
   - required **Java major version** (align with `pom.xml` / toolchain)
   - where to obtain the JDK and **exact unpack path** (e.g. “extract so that `java` is at `backend/.jdk/bin/java`”)
3. Developers set **`JAVA_HOME`** to that folder (or point the IDE SDK at it), then run **`./mvnw`** as usual.

**CI:** use a **pinned JDK** in the pipeline (container image or setup action). CI does **not** depend on a committed `.jdk/` folder.

---

## Summary

| Item | Location | In git? |
|------|-----------|---------|
| Maven Wrapper (`mvnw`, `.mvn/wrapper/`, …) | Backend root (with `pom.xml`) | **Yes** |
| Local JDK unpack | Backend root, e.g. `.jdk/` | **No** (gitignore + README) |

Scaffold prompts should create the **wrapper** layout for Spring Boot backends, document the **`.jdk/`** convention, and **populate `backend/.jdk/` when possible** (script download or copy from an existing **local** path — never commit JDK binaries).

## Related

- Default local **API port** vs **Next.js dev port**: [LOCAL-DEV-PORTS.md](./LOCAL-DEV-PORTS.md).

### Where this fits (architecture vs scaffold vs guide)

| Topic | Belongs in |
|-------|------------|
| **Which Java major version**, Maven Wrapper, **`backend/.jdk`**, fetch scripts, **`JAVA_HOME`**, CI image JDK | **This guide** + scaffold **files** (scripts, `.gitignore`, backend README) — not **Prompt 1 architecture**. |
| **Per-machine install paths** (IDE, `direnv`, **Wage prompt-helper** Home → JDK install) | **This guide** (patterns) + **`tools/prompt-helper`** (persists `javaHome`; **foundation Cursor prompt** can include `terminal.integrated.env.windows` / `JAVA_HOME` for `.vscode/settings.json`). |
| **Prompt 1 / `ARCHITECTURE-DEFINITION`** | Only if a **named product component** (e.g. desktop orchestrator) **owns** JVM lifecycle — then **one short subsection + link here**, not full path rules. |
