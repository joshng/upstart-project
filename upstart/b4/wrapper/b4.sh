#!/usr/bin/env bash

set -e
set -o pipefail

if [ $# -lt 2 ]; then
  echo "USAGE: b4.sh <project-dir> <project-artifact-coords>"
  exit 1
fi

if ! type -p xmllint > /dev/null; then
  echo "ERROR: xmllint not found in PATH" >&2
  exit 1
fi

if ! type -p jq > /dev/null; then
  echo "ERROR: jq is required to run b4 (run 'brew install jq' or similar)" >&2
  exit 1
fi

if [[ $BASH_VERSION =~ ^[0-3]\. ]]; then
  echo "ERROR: b4 requires bash 4 or newer (run 'brew install bash' or similar)" >&2
  exit 1
fi


PROGRAM_DIR="$1"
shift
PROGRAM_ARTIFACT="$1"
shift

PROGRAM=${B4_NAME:-"$(basename "$PROGRAM_DIR")"}
: "${B4_CONFIG:=B4.conf}"

ARTIFACT_ID="$(echo "$PROGRAM_ARTIFACT" | cut -d: -f2)"

report() {
  if [[ -z $B4_SILENT ]]; then
    local echoargs=()
    while getopts "Een" opt; do
      echoargs+=("-$opt")
      shift
    done
    echo "${echoargs[@]}" "$(date '+%F_%H:%M:%S') $ARTIFACT_ID -" "$@"
  fi
}

debug () {
  if [[ -n $B4_DEBUG ]]; then
    report "$@" >&2
  fi
}

debug "PROGRAM_ARTIFACT=$PROGRAM_ARTIFACT"

find_b4_home() {
  (
    while [ ! -f "$B4_CONFIG" ] && [ $PWD != / ]; do
      command cd .. > /dev/null
    done

    if [ ! -f "$B4_CONFIG" ]; then
      echo "Unable to find file config-file '$B4_CONFIG' in current directory hierarchy" >&2
      exit 1
    fi

    echo $PWD
  )
}

in_list() {
  local i match="$1"
  shift
  for i; do [[ "$i" == "$match" ]] && return 0; done
  return 1
}

check_clean() {
  local depgraph_basename=$ARTIFACT_ID-depgraph.json
  local depgraph=target/$depgraph_basename


  debug "Checking for clean working directory for $ARTIFACT_ID"
  local poms artifacts artifact
  readarray -d '' poms < <(find . -name pom.xml -print0 -o -name build -prune -o -name target -prune -o -name .git -prune -o -name src -prune)

  if ! [[ -f $depgraph && -z "$(find "${poms[@]}" -newer $depgraph)" ]] ; then
    report "Computing dependency graph for $PROGRAM_ARTIFACT"
    mvn -q -Pb4 com.github.ferstl:depgraph-maven-plugin:for-artifact -DgraphFormat=json -DoutputFileName=$depgraph_basename -Dartifact=$PROGRAM_ARTIFACT || exit 1
  fi

  # jq -r '.artifacts[] | "\\(.groupId):\\(.artifactId):\\(.version)"'
  readarray -t artifacts < <(jq -r '.artifacts[].artifactId' < $depgraph)

  for pom in "${poms[@]}"; do
    artifact="$(xmllint --xpath "//*[local-name()='project']/*[local-name()='artifactId']/text()" "$pom")"
    if in_list "$artifact" "${artifacts[@]}"; then
      local proj_dir modified
      proj_dir="$(dirname "$pom")"
      modified="$(find "$proj_dir/src/main" -type f -newer "$PROGRAM_JAR" -print | head -1)"
      if [ -n "$modified" ] ; then
        report "Found modified file(s) in $proj_dir"
        return 1
      fi
    fi
  done
  debug "Working directory is clean for $ARTIFACT_ID"
}

REBUILD=${REBUILD:-false}
unset PROPS
declare -a PROPS

# handle -D (with or without a space) and -r (rebuild); pass anything else along to java
while [ $# -gt 0 ]; do
  case "$1" in
  -D*)
    if [ "$1" = "-D" ]; then
      shift
      PROPS+=("-D$1")
    else
      PROPS+=("$1")
    fi
    shift
     ;;
  -r)
    REBUILD=true
    shift
     ;;
  *) break
    ;;
  esac
done

B4_HOME="${B4_HOME:-"$(find_b4_home)"}"
cd "$B4_HOME"

#TODO: could alter this to support multiple task-definition jars, by running b4.jar with extension-jars on the classpath,
# and providing colocated config-files
PROGRAM_JAR="${PROGRAM_JAR:-"$PROGRAM_DIR/target/package/$(basename $PROGRAM_DIR).jar"}"

if [[ $REBUILD = true || ! -f "$PROGRAM_JAR" ]] || ! check_clean; then
  report "Rebuilding..."
  set +e
  # TODO: make the build profile (-Pb4) configurable
  out=$(mvn clean package -Pb4 -DskipTests -pl $PROGRAM_DIR -am -T1.5C)
  status=$?
  set -e
  if [ $status != 0 ]; then
    report -e "\n$out\n\n$PROGRAM - Rebuild failed!"
    exit $status
  fi
  report "Rebuilt"
fi

#if [ -z "$NOLOG" ] && type rotatelogs > /dev/null 2>&1; then
#  exec java -Db4.program-name="$PROGRAM" "${PROPS[@]@Q}" -jar "$PROGRAM_JAR" "$@" | tee >(rotatelogs -Dln7 tmp/$PROGRAM-logs/$PROGRAM.log 86400)
#else
  exec java -Db4.program-name="$PROGRAM" "${PROPS[@]}" -jar "$PROGRAM_JAR" "$@"
#fi
#echo java -Db4.program-name="$PROGRAM" "${PROPS[@]@Q}" -jar "$PROGRAM_JAR" "$@"
