#!/usr/bin/env bash

set -e
set -o pipefail

if [ $# -lt 2 ]; then
  echo "USAGE: $(basename "$0") <project-dir> <project-artifact-coords> \"\$@\""
  exit 1
fi

if ! type -p xmllint > /dev/null; then
  echo "ERROR: xmllint not found in PATH" >&2
  exit 1
fi


PROGRAM_DIR="$1"
shift
PROGRAM_ARTIFACT="$1"
shift

UPSTART_PROGRAM=${UPSTART_PROGRAM:-"$(basename "$PROGRAM_DIR")"}

if ! type -p jq > /dev/null; then
  echo "ERROR: jq is required to run $UPSTART_PROGRAM (run 'brew install jq' or similar)" >&2
  exit 1
fi

if [[ $BASH_VERSION =~ ^[0-3]\. ]]; then
  echo "ERROR: $UPSTART_PROGRAM requires bash 4 or newer (run 'brew install bash' or similar)" >&2
  exit 1
fi

MVN_PROFILES="${UPSTART_MVN_PROFILES:+-P$UPSTART_MVN_PROFILES}"

ARTIFACT_ID="$(echo "$PROGRAM_ARTIFACT" | cut -d: -f2)"
depgraph_basename=$ARTIFACT_ID-depgraph.json
depgraph=tmp/dependency-graphs/$depgraph_basename

report() {
  if [[ -z $UPSTART_SILENT ]]; then
    local echoargs=()
    while getopts "Een" opt; do
      echoargs+=("-$opt")
      shift
    done
    echo "${echoargs[@]}" "$(date '+%F_%H:%M:%S') $ARTIFACT_ID -" "$@"
  fi
}

debug () {
  if [[ -n $UPSTART_DEBUG ]]; then
    report "$@" >&2
  fi
}

debug "PROGRAM_ARTIFACT=$PROGRAM_ARTIFACT"

in_list() {
  local i match="$1"
  shift
  for i; do [[ "$i" == "$match" ]] && return 0; done
  return 1
}

check_clean_depgraph() {
  if [[ ! -f $depgraph ]]; then return 1; fi
  local poms
  readarray -d '' poms < <(find . -name pom.xml -print0 -o -name build -prune -o -name target -prune -o -name .git -prune -o -name src -prune)
  if [[ ! -z "$(find "${poms[@]}" -newer $depgraph)" ]]; then
    return 1
  fi
}

refresh_depgraph() {
  if ! check_clean_depgraph; then
    report "Computing dependency graph for $PROGRAM_ARTIFACT"
    mkdir -p "$(dirname "$depgraph")"
    mvn -q ${MVN_PROFILES} com.github.ferstl:depgraph-maven-plugin:for-artifact -DgraphFormat=json -DoutputFileName=$depgraph_basename -Dartifact=$PROGRAM_ARTIFACT || exit 1
    mv target/$depgraph_basename $depgraph
  fi
}

check_clean() {
  debug "Checking for clean working directory for $ARTIFACT_ID"
  local artifacts artifact

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

# handle -D (with or without a space), pass anything else along to java
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
   --)
     shift
     break
     ;;
  *) break
    ;;
  esac
done

PROGRAM_JAR="${PROGRAM_JAR:-"$PROGRAM_DIR/target/package/$(basename $PROGRAM_DIR).jar"}"

if [[ $REBUILD = true ]]; then rm -f $depgraph; fi

refresh_depgraph

if [[ $REBUILD = true || ! -f "$PROGRAM_JAR" ]] || ! check_clean; then
  report "Rebuilding..."
  set +e
  out=$(mvn clean package ${MVN_PROFILES} -DskipTests -Dmaven.javadoc.skip=true -pl $PROGRAM_DIR -am -T1.5C)
  status=$?
  set -e
  if [ $status != 0 ]; then
    report -e "\n$out\n\n$PROGRAM_DIR - Rebuild failed!"
    exit $status
  fi
  report "Rebuilt"
fi

#if [ -z "$NOLOG" ] && type rotatelogs > /dev/null 2>&1; then
#  exec java -Db4.program-name="$UPTART_PROGRAM" "${PROPS[@]@Q}" -jar "$PROGRAM_JAR" "$@" | tee >(rotatelogs -Dln7 tmp/$UPSTART_PROGRAM-logs/$UPSTART_PROGRAM.log 86400)
#else
  exec java -Db4.program-name="$UPSTART_PROGRAM" "${PROPS[@]}" -jar "$PROGRAM_JAR" "$@"
#fi
