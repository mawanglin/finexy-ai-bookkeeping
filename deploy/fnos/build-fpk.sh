#!/usr/bin/env bash
# Build a native (non-Docker) fnOS .fpk for Finexy. Does not modify tracked project files.
#
# Requires: go, gcc, node, npm (fnpack, the arm64 cross toolchain and Pillow are fetched when missing).
# Usage:    [ARCHS="x86 arm"] deploy/fnos/build-fpk.sh [--skip-build]
#   ARCHS        space separated targets, default "x86" (x86_64). "arm" builds linux/arm64.
#   --skip-build reuses ./ezbookkeeping (x86), deploy/fnos/out/ezbookkeeping-arm64 (arm) and ./dist
#   CC_ARM64     C cross compiler for arm64 (default: aarch64-linux-musl-gcc / aarch64-linux-gnu-gcc from
#                PATH, otherwise the musl.cc toolchain is downloaded)
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
ARCHS="${ARCHS:-x86}"
COMMIT="$(git rev-parse --short=7 HEAD)"

find_arm64_cc() {
    if [ -n "${CC_ARM64:-}" ]; then echo "$CC_ARM64"; return; fi
    for c in aarch64-linux-musl-gcc aarch64-linux-gnu-gcc; do
        command -v "$c" >/dev/null 2>&1 && { command -v "$c"; return; }
    done
    local dir="${TOOLCHAIN_CACHE:-$HOME/.cache/finexy-fpk}"
    if [ ! -x "$dir/aarch64-linux-musl-cross/bin/aarch64-linux-musl-gcc" ]; then
        mkdir -p "$dir"
        echo "Downloading aarch64 musl cross toolchain..." >&2
        curl -fsSL "https://musl.cc/aarch64-linux-musl-cross.tgz" | tar xz -C "$dir"
    fi
    echo "$dir/aarch64-linux-musl-cross/bin/aarch64-linux-musl-gcc"
}

# Fully static binary (pure-Go DNS/user lookups) so it does not depend on the NAS libc.
build_backend() {
    local arch="$1"
    case "$arch" in
        x86) CGO_ENABLED=1 go build -trimpath -tags 'netgo osusergo' \
                -ldflags "-w -s -linkmode external -extldflags '-static' -X main.Version=$BASE_VERSION -X main.CommitHash=$COMMIT" \
                -o ezbookkeeping ezbookkeeping.go ;;
        arm) CC="$(find_arm64_cc)" CGO_ENABLED=1 GOOS=linux GOARCH=arm64 go build -trimpath -tags 'netgo osusergo' \
                -ldflags "-w -s -linkmode external -extldflags '-static' -X main.Version=$BASE_VERSION -X main.CommitHash=$COMMIT" \
                -o "$OUT/ezbookkeeping-arm64" ezbookkeeping.go ;;
    esac
}

if [ "${1:-}" != "--skip-build" ]; then
    mkdir -p "$OUT"
    for arch in $ARCHS; do build_backend "$arch"; done
    ./build.sh frontend --no-test --no-lint
fi
[ -d dist ] || { echo "missing ./dist" >&2; exit 1; }

package() {
    local arch="$1" bin
    case "$arch" in
        x86) bin=ezbookkeeping ;;
        arm) bin="$OUT/ezbookkeeping-arm64" ;;
        *) echo "unknown arch: $arch (use x86 or arm)" >&2; exit 2 ;;
    esac
    [ -f "$bin" ] || { echo "missing $bin" >&2; exit 1; }

    local stage pkg app
    stage="$(mktemp -d)"
    pkg="$stage/finexy"
    cp -R "$HERE/template" "$pkg"
    sed -i "s/@VERSION@/$VERSION/; s/@PLATFORM@/$arch/" "$pkg/manifest"

    # Application payload -> app.tgz (${TRIM_APPDEST}); the binary is always named ezbookkeeping
    app="$pkg/app"
    cp "$bin" "$app/ezbookkeeping"
    cp -R dist "$app/public"
    cp -R conf "$app/conf"
    cp -R templates "$app/templates"
    cp LICENSE "$app/"
    mkdir -p "$app/public/prototypes/family-web"
    cp docs/prototypes/family-web/{index.html,style.css,app.js} "$app/public/prototypes/family-web/"

    # Icons (derived from the project's touch icon)
    python3 - "$ROOT/public/touchicon.png" "$pkg" <<'PY'
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
    ( cd "$pkg" && "$FNPACK" build )
    mv "$pkg"/*.fpk "$OUT/finexy-$VERSION-$arch.fpk"
    rm -rf "$stage"
    sha256sum "$OUT/finexy-$VERSION-$arch.fpk"
}

for arch in $ARCHS; do package "$arch"; done
