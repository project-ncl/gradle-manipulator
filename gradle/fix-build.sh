#!/bin/sh

if [ -z "$1" ]; then
    echo "Please specify a Gradle version"
    exit 1
fi

VERSION=$1

echo "Gradle version: ${VERSION}"

case "${VERSION}" in
    4.10*|5.0*)
        echo "Modifying build"
        find ../ -type f -name '*.gradle*' -print0 | xargs -0 -t sed -i \
             -e 's|archiveClassifier\.set(\(.*\))|classifier = \1|g;' \
             -e 's|archiveClassifier = |classifier = |g;' \
             -e 's|${archiveClassifier}|${classifier}|g;' \
             -e 's|archiveFileName = |archiveName |g;' \
             -e 's|destinationDirectory = |destinationDir |g;' \
             -e 's|archiveBaseName = |baseName = |g;' \
             -e 's|${archiveBaseName}|${baseName}|g;'
        ;;
    *)
        echo "Not modifying build"
        ;;
esac
