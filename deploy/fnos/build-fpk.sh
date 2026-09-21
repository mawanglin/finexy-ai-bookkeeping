#!/usr/bin/env bash
# Build a native (non-Docker) fnOS .fpk for Finexy. Does not modify tracked project files.
#
# Requires: go, gcc, node, npm, and fnpack (set FNPACK=/path/to/fnpack or put it in PATH).
# Usage:    deploy/fnos/build-fpk.sh [--skip-build]   # --skip-build reuses ./ezbookkeeping and ./dist
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
FNPACK="${FNPACK:-fnpack}"
OUT="${OUT:-$ROOT/deploy/fnos/out}"
BASE_VERSION="$(grep '"version": ' "$ROOT/package.json" | head -1 | awk -F'"' '{print $4}')"

# Package version = <base>-<N>. CI passes BUILD_NUMBER (e.g. the run number); locally every build
# bumps the counter kept in deploy/fnos/BUILD_NUMBER.
if [ -z "${BUILD_NUMBER:-}" ]; then
    BUILD_FILE="$HERE/BUILD_NUMBER"
    BUILD_NUMBER=$(( $(cat "$BUILD_FILE" 2>/dev/null || echo 0) + 1 ))
    echo "$BUILD_NUMBER" > "$BUILD_FILE"
fi
VERSION="$BASE_VERSION-$BUILD_NUMBER"
echo "Building Finexy fpk version $VERSION"

# Fetch fnpack when it is not available.
FNPACK_VERSION="${FNPACK_VERSION:-1.2.3}"
if ! command -v "$FNPACK" >/dev/null 2>&1; then
    FNPACK="$(mktemp -d)/fnpack"
    echo "Downloading fnpack $FNPACK_VERSION..."
    curl -fsSL -o "$FNPACK" "https://static2.fnnas.com/fnpack/fnpack-$FNPACK_VERSION-linux-amd64"
    chmod +x "$FNPACK"
fi

# Pillow is used to derive the package icons.
python3 -c "import PIL" 2>/dev/null || python3 -m pip install --quiet --user pillow

cd "$ROOT"
if [ "${1:-}" != "--skip-build" ]; then
    # Fully static binary (pure-Go DNS/user lookups) so it does not depend on the NAS glibc version.
    COMMIT="$(git rev-parse --short=7 HEAD)"
    CGO_ENABLED=1 go build -trimpath -tags 'netgo osusergo' \
        -ldflags "-w -s -linkmode external -extldflags '-static' -X main.Version=$BASE_VERSION -X main.CommitHash=$COMMIT" \
        -o ezbookkeeping ezbookkeeping.go
    ./build.sh frontend --no-test --no-lint
fi
[ -x ezbookkeeping ] && [ -d dist ] || { echo "missing ./ezbookkeeping or ./dist" >&2; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
PKG="$STAGE/finexy"
cp -R "$HERE/template" "$PKG"
sed -i "s/@VERSION@/$VERSION/" "$PKG/manifest"

# Application payload -> app.tgz (${TRIM_APPDEST})
APP="$PKG/app"
cp ezbookkeeping "$APP/"
cp -R dist "$APP/public"
cp -R conf "$APP/conf"
cp -R templates "$APP/templates"
cp LICENSE "$APP/"
mkdir -p "$APP/public/prototypes/family-web"
cp docs/prototypes/family-web/{index.html,style.css,app.js} "$APP/public/prototypes/family-web/"

# Icons (derived from the project's touch icon)
python3 - "$ROOT/public/touchicon.png" "$PKG" <<'PY'
import sys
from PIL import Image
src, pkg = sys.argv[1:3]
im = Image.open(src).convert("RGBA")
for size, names in ((64, ["ICON.PNG", "app/ui/images/icon_64.png"]), (256, ["ICON_256.PNG", "app/ui/images/icon_256.png"])):
    r = im.resize((size, size), Image.LANCZOS)
    for n in names:
        r.save(f"{pkg}/{n}")
PY

mkdir -p "$OUT"
( cd "$PKG" && "$FNPACK" build )
mv "$PKG"/*.fpk "$OUT/finexy-$VERSION-x86.fpk" 
sha256sum "$OUT/finexy-$VERSION-x86.fpk"
