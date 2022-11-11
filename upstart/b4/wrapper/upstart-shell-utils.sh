set -e -o pipefail
# utility functions for shell scripts
absolutepath() {
  echo "$(cd "$(dirname "$1")" && pwd -P)/$(basename "$1")"
}
