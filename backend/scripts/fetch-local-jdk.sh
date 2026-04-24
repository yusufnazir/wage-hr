#!/usr/bin/env bash
# Downloads Eclipse Temurin (pinned major) into ../.jdk next to backend/pom.xml
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_JDK="${BACKEND_ROOT}/.jdk"
JAVA_FEATURE_VERSION="${ADOPTIUM_JAVA_VERSION:-21}"

os="$(uname -s | tr '[:upper:]' '[:lower:]')"
mach="$(uname -m)"
case "${os}" in
  linux)
    adopt_os="linux"
    case "${mach}" in
      x86_64) adopt_arch="x64" ;;
      aarch64 | arm64) adopt_arch="aarch64" ;;
      *) echo "Unsupported Linux machine: ${mach}" >&2; exit 1 ;;
    esac
    bundle_ext="tar.gz"
    ;;
  darwin)
    adopt_os="mac"
    case "${mach}" in
      x86_64) adopt_arch="x64" ;;
      arm64) adopt_arch="aarch64" ;;
      *) echo "Unsupported macOS machine: ${mach}" >&2; exit 1 ;;
    esac
    bundle_ext="tar.gz"
    ;;
  *)
    echo "Unsupported OS: ${os} (use fetch-local-jdk.ps1 on Windows)" >&2
    exit 1
    ;;
esac

url="https://api.adoptium.net/v3/binary/latest/${JAVA_FEATURE_VERSION}/ga/${adopt_os}/${adopt_arch}/jdk/hotspot/normal/eclipse"
tmp_root="$(mktemp -d)"
cleanup() { rm -rf "${tmp_root}"; }
trap cleanup EXIT

archive="${tmp_root}/temurin.${bundle_ext}"
echo "Downloading Temurin ${JAVA_FEATURE_VERSION} (${adopt_os}/${adopt_arch})…"
curl -fsSL -o "${archive}" "${url}"

unpack="${tmp_root}/unpack"
mkdir -p "${unpack}"
tar -xzf "${archive}" -C "${unpack}"

shopt -s nullglob dotglob
inner_dirs=( "${unpack}"/*/ )
if [[ ${#inner_dirs[@]} -ne 1 || ! -d "${inner_dirs[0]}" ]]; then
  echo "Unexpected archive layout under ${unpack}" >&2
  ls -la "${unpack}" >&2
  exit 1
fi
inner="${inner_dirs[0]}"
inner="${inner%/}"

rm -rf "${TARGET_JDK}"
mkdir -p "${TARGET_JDK}"
# shellcheck disable=SC2086
mv "${inner}"/* "${TARGET_JDK}/"

echo "Installed JDK to: ${TARGET_JDK}"
echo "export JAVA_HOME=\"${TARGET_JDK}\""
