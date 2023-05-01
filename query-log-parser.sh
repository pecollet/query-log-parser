#!/usr/bin/env bash

set -o errexit -o nounset -o pipefail
[[ "${TRACE:-}" ]] && set -o xtrace

PROGRAM="$(basename "$0")"
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
readonly PROGRAM

detect_os() {
  if uname -s | grep -q Darwin; then
    DIST_OS="macosx"
  else
    DIST_OS="other"
  fi
}

_find_java_cmd() {
  [[ "${JAVA_CMD:-}" ]] && return
  detect_os
  _find_java_home

  if [[ "${JAVA_HOME:-}" ]] ; then
    JAVA_CMD="${JAVA_HOME}/bin/java"
    if [[ ! -f "${JAVA_CMD}" ]]; then
      echo "ERROR: JAVA_HOME is incorrectly defined as ${JAVA_HOME} (the executable ${JAVA_CMD} does not exist)"
      exit 1
    fi
  else
    if [ "${DIST_OS}" != "macosx" ] ; then
      # Don't use default java on Darwin because it displays a misleading dialog box
      JAVA_CMD="$(command -v java || true)"
    fi
  fi

  if [[ ! "${JAVA_CMD:-}" ]]; then
    echo "ERROR: Unable to find Java executable. Make sure the java executable is on the PATH or define JAVA_HOME."
    echo "* Please use Oracle(R) Java(TM) 11, OpenJDK(TM) 11."
    exit 1
  fi
}

_find_java_home() {
  [[ "${JAVA_HOME:-}" ]] && return

  case "${DIST_OS}" in
    "macosx")
      JAVA_HOME="$(/usr/libexec/java_home -v 11)"
      ;;
  esac
}

check_java() {
  _find_java_cmd
  "${JAVA_CMD}" "-version"
  version_command=("${JAVA_CMD}" "-version")

  JAVA_VERSION=$("${version_command[@]}" 2>&1 | awk -F '"' '/version/ {print $2}')
  if [[ $JAVA_VERSION = "1."* ]] || [[ $JAVA_VERSION = "9"* ]] || [[ $JAVA_VERSION = "10"* ]]; then
      echo "ERROR! Tool cannot be started using java version ${JAVA_VERSION}. "
      echo "* Please use Oracle(R) Java(TM) 11, OpenJDK(TM) 11."
      exit 1
  elif [[ $JAVA_VERSION != "11"* ]] ; then
    echo "WARNING! You are using an unsupported Java runtime. "
    echo "* Please use Oracle(R) Java(TM) 11, OpenJDK(TM) 11."
  else
    if ! ("${version_command[@]}" 2>&1 | grep -Eq "(Java HotSpot\\(TM\\)|OpenJDK) (64-Bit Server|Server|Client) VM"); then
    echo "WARNING! You are using an unsupported Java runtime. "
    echo "* Please use Oracle(R) Java(TM) 11, OpenJDK(TM) 11."
    fi
  fi
}

call_main_class() {
  check_java
  JAR_FILE="${SCRIPT_DIR}/target/query-log-parser.jar"
  JAVA_MEMORY_OPTS_XMX="-Xmx2g"
  class_name=$1
  shift

  exec "${JAVA_CMD}" ${JAVA_MEMORY_OPTS_XMX-} \
    "-Dfile.encoding=UTF-8" \
    -jar "$JAR_FILE"  "$@"
}


call_main_class "org.neo4j.logging.QueryLogParser" "$@"