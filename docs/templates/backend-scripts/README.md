# Local JDK fetch scripts (Eclipse Temurin)

Copy **`fetch-local-jdk.ps1`** and **`fetch-local-jdk.sh`** into your product **`backend/scripts/`** directory (next to `pom.xml` is `backend/`, so scripts sit at `backend/scripts/`).

They download a **pinned major version** of Eclipse Temurin from the [Adoptium API](https://api.adoptium.net/) for the **current OS and CPU**, unpack once, and lay out files so **`backend/.jdk/bin/java`** exists.

## Prereqs

- Network access to `api.adoptium.net` and the GitHub release CDN.
- **Windows:** PowerShell 5.1+ (or `pwsh`). `Expand-Archive` is used for `.zip`.
- **macOS / Linux:** `bash`, `curl`, and `tar` (`.tar.gz`). `unzip` is not required on these platforms.

## Usage

From the **backend root** (where `pom.xml` lives):

```bash
./scripts/fetch-local-jdk.sh
```

```powershell
pwsh -File .\scripts\fetch-local-jdk.ps1
```

## Pin Java version

Default major version is **21**. Override without editing the file:

- **bash:** `export ADOPTIUM_JAVA_VERSION=17` then run the script.
- **PowerShell:** `$env:ADOPTIUM_JAVA_VERSION = '17'` then run the script.

## Git

Keep **`backend/.jdk/`** in **`.gitignore`**. Do not commit the JDK. CI should use its own JDK (image or setup action).
