# utility functions for shell scripts
absolutepath() {
  echo "$(cd "$(dirname "$1")" && pwd -P)/$(basename "$1")"
}

delink() {
  local path="$1"
  while [[ -L $path ]]; do
    path="$(readlink "$path")"
  done
  echo "$path"
}
