#!/usr/bin/env bash
set -e -o pipefail

UPSTART_MVN_PROFILES=b4 exec bash "$(dirname $0)"/upstart-wrapper.sh "$@"

