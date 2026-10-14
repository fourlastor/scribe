#!/usr/bin/env bash
# Build a signed Scribe release and everything its GitHub release must carry for Appfredo: appfredo.json, the
# full APK, and Wharf patches from recent releases, so installed copies download only what changed. Nearly all
# of the APK is the speech model, which rarely changes, so a patch is a small fraction of it.
# Adapted from fourlastor/appfredo's scripts/release.sh; prepare-release.py beside it is Appfredo's own helper.
#
#   scripts/release.sh vMAJOR.MINOR.PATCH [--previous-apk FILE]...
#
# versionCode is MAJOR*1000000 + MINOR*1000 + PATCH, so it rises with every release. Without --previous-apk,
# the APKs of the newest $SCRIBE_PATCH_RELEASES (default 3; 0 for none) stable GitHub releases are fetched
# with gh. Signing needs SCRIBE_KEYSTORE, the PKCS12 keystore holding the key aliased scribe, and
# SCRIBE_KEYSTORE_PASSWORD; butler is $BUTLER or on PATH. The assets land in build/release/<tag>/, ready to
# upload as they are.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tag="${1:?Usage: scripts/release.sh vMAJOR.MINOR.PATCH [--previous-apk FILE]...}"
shift
if [[ ! "$tag" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Release tags look like v1.2.3, not $tag" >&2; exit 1
fi
major=$((10#${BASH_REMATCH[1]})) minor=$((10#${BASH_REMATCH[2]})) patch=$((10#${BASH_REMATCH[3]}))
if (( major > 2000 || minor > 999 || patch > 999 )); then
    echo "$tag does not fit the versionCode scheme (minor and patch up to 999)" >&2; exit 1
fi
version="$major.$minor.$patch"
code=$(( major * 1000000 + minor * 1000 + patch ))
previous=()
while (( $# )); do
    case "$1" in
        --previous-apk) previous+=("$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"); shift 2 ;;
        *) echo "Unknown argument: $1" >&2; exit 1 ;;
    esac
done
: "${SCRIBE_KEYSTORE:?Set SCRIBE_KEYSTORE and SCRIBE_KEYSTORE_PASSWORD: a release must be signed with the release key}"
: "${SCRIBE_KEYSTORE_PASSWORD:?Set SCRIBE_KEYSTORE_PASSWORD to the password of the release keystore}"
export SCRIBE_KEY_ALIAS="${SCRIBE_KEY_ALIAS:-scribe}"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
build_tools="${SCRIBE_BUILD_TOOLS:-$(ls -d "$sdk"/build-tools/* | sort -V | tail -n 1)}"
out="$root/build/release/$tag"
work="$root/build/release/.work-$tag"
rm -rf "$out" "$work"
mkdir -p "$work"
cd "$root"

./gradlew :app:assembleRelease -Pscribe.versionCode="$code" -Pscribe.versionName="$version"
apk="$root/app/build/outputs/apk/release/app-release.apk"
[[ -f "$apk" ]] || { echo "No signed release APK at $apk" >&2; exit 1; }

if (( ${#previous[@]} == 0 && ${SCRIBE_PATCH_RELEASES:-3} > 0 )); then
    # Newest first. A re-run never patches from the release it is replacing.
    releases=$(gh release list --exclude-drafts --exclude-pre-releases --limit 20 --json tagName --jq '.[].tagName') \
        || { echo "Could not list earlier releases with gh; pass --previous-apk to build patches without it" >&2; exit 1; }
    for earlier in $(grep -vx -- "$tag" <<<"$releases" | head -n "${SCRIBE_PATCH_RELEASES:-3}"); do
        dir="$work/previous/$earlier"
        mkdir -p "$dir"
        if ! gh release download "$earlier" --pattern appfredo.json --dir "$dir"; then
            echo "$earlier has no appfredo.json, so there is no patch from it" >&2
            continue
        fi
        asset=$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["apk"]["asset"])' "$dir/appfredo.json")
        gh release download "$earlier" --pattern "$asset" --dir "$dir"
        previous+=("$dir/$asset")
    done
fi

patches=()
for earlier_apk in "${previous[@]}"; do patches+=(--previous-apk "$earlier_apk"); done
python3 "$root/scripts/prepare-release.py" --apk "$apk" --name Scribe --out "$out" "${patches[@]}" \
    --butler "${BUTLER:-butler}" --aapt "$build_tools/aapt" --apksigner "$build_tools/apksigner"
rm -rf "$work"
ls -l "$out"
