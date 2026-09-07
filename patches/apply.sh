#!/usr/bin/env bash
#
# Applies the patches under patches/sdl to the SDL submodule.
#
# SDL is a submodule of upstream libsdl-org/SDL, so a fix that upstream has not released
# yet cannot be carried as a commit of our own. Every patch here is meant to go upstream;
# once a release picks one up, delete the file and bump the submodule instead.
#
# Safe to run twice: a patch that is already applied is skipped rather than failing.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
patch_dir="$repo_root/patches/sdl"
sdl_dir="$repo_root/SDL"

if [ ! -d "$sdl_dir/src" ]; then
    echo "The SDL submodule is not checked out at $sdl_dir" >&2
    exit 1
fi

shopt -s nullglob
patches=("$patch_dir"/*.patch)
shopt -u nullglob

if [ ${#patches[@]} -eq 0 ]; then
    echo "No SDL patches to apply."
    exit 0
fi

for patch in "${patches[@]}"; do
    name="$(basename "$patch")"
    if git -C "$sdl_dir" apply --reverse --check "$patch" 2>/dev/null; then
        echo "Already applied, skipping: $name"
        continue
    fi
    echo "Applying: $name"
    # Failing here means SDL moved under the patch. Rebase the patch rather than dropping
    # it, otherwise the natives quietly ship without the fix.
    git -C "$sdl_dir" apply --verbose "$patch"
done
