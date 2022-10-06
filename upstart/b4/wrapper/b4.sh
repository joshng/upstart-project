#!/usr/bin/env bash

set -e
set -o pipefail

if [ $# -lt 2 ]; then
  echo "USAGE: b4.sh <project-dir> <project-artifact-coords>"
  exit 1
fi

B4_PROJECT="$1"
shift
PROGRAM_ARTIFACT="$1"
shift

PROGRAM_DIR="$B4_PROJECT"
PROGRAM=${B4_NAME:-"$(basename "$B4_PROJECT")"}
: "${B4_CONFIG:=B4.conf}"

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
  if ! find target/dependency-graph.json -mmin +10 > /dev/null 2>&1 ; then
    echo "Computing dependency-graph for $PROGRAM_ARTIFACT"
    mvn -q -Pb4 com.github.ferstl:depgraph-maven-plugin:for-artifact  -DgraphFormat=json  -Dartifact=$PROGRAM_ARTIFACT
  fi

  # jq -r '.artifacts[] | "\\(.groupId):\\(.artifactId):\\(.version)"'
  readarray -t artifacts < <(jq -r '.artifacts[].artifactId' < target/dependency-graph.json)

  for proj in $(find . -name pom.xml -print -o -name build -prune); do
    artifact=$(xmllint --xpath "//*[local-name()='project']/*[local-name()='artifactId']/text()" "$proj")
    if in_list "$artifact" "${artifacts[@]}"; then
      local proj_dir="$(dirname "$proj")"
      local modified="$(find "$proj_dir" -type f -newer "$PROGRAM_JAR")"
      newcount=$(( $(echo -n "$modified" | wc -l) ))
      if ! [ $newcount -eq 0 ] ; then
        echo -e "Found $newcount modified file(s) in $proj_dir"
        return 1
      fi
    fi
  done
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

#TODO: could alter this to support multiple task-definition jars, by running b4.jar with extension-jars on the classpath
PROGRAM_JAR="${PROGRAM_JAR:-"$PROGRAM_DIR/target/package/$(basename $PROGRAM_DIR).jar"}"

if [ $REBUILD = true -o ! -f "$PROGRAM_JAR" ] || ! check_clean; then
  echo "Rebuilding..."
  rm -f target/dependency-graph.json
  set +e
  out=$(mvn clean package -Pb4 -DskipTests -pl $PROGRAM_DIR -am -T1.5C)
  status=$?
  set -e
  if [ $status != 0 ]; then
    echo -e "\n$out\n\n$PROGRAM: rebuild failed!"
    exit $status
  fi
  echo
fi

#if [ -z "$NOLOG" ] && type rotatelogs > /dev/null 2>&1; then
#  exec java -Db4.program-name="$PROGRAM" "${PROPS[@]@Q}" -jar "$PROGRAM_JAR" "$@" | tee >(rotatelogs -Dln7 tmp/$PROGRAM-logs/$PROGRAM.log 86400)
#else
  exec java -Db4.program-name="$PROGRAM" "${PROPS[@]}" -jar "$PROGRAM_JAR" "$@"
#fi
#echo java -Db4.program-name="$PROGRAM" "${PROPS[@]@Q}" -jar "$PROGRAM_JAR" "$@"
