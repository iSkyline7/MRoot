#!/usr/bin/env bash
set -euo pipefail

KMIS=(
    "android12-5.10"
    "android13-5.10"
    "android13-5.15"
    "android14-5.15"
    "android14-6.1"
    "android15-6.6"
    "android16-6.12"
    "android17-6.18"
)

PROJ_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$PROJ_DIR/app/src/main/jni/ko"

for kmi in "${KMIS[@]}"; do
    echo "Building LKM for $kmi..."
    podman run --rm --pid=host --network=none \
        -v "$PROJ_DIR":/proj \
        -e KMI="$kmi" \
        "ghcr.io/ylarod/ddk-min:$kmi" \
        bash -c 'set -e; \
            cd /proj/lkm; \
            make -j$(nproc); \
            llvm-objcopy --strip-unneeded \
              -R .comment -R .note.gnu.build-id -R .note.gnu.property \
              -R .note.Linux -R .note.GNU-stack \
              -R .BTF -R .BTF.base -R .llvm_addrsig \
              -R .hyp.text -R .hyp.bss -R .hyp.rodata -R .hyp.event_ids \
              -R .hyp.patchable_function_entries -R .hyp.data dfroot.ko; \
            mkdir -p /proj/app/src/main/jni/ko; \
            cp dfroot.ko /proj/app/src/main/jni/ko/dfroot-$KMI.ko; \
            make clean; \
            echo "Built dfroot-$KMI.ko"'
done
echo "All LKMs built successfully in app/src/main/jni/ko/"
