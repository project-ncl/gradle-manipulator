#!/bin/sh

REVERSE=0
if [ "$1" = "-r" ]; then
    REVERSE=1
    shift
fi

if [ -z "$1" ]; then
    echo "Please specify a Gradle version"
    exit 1
fi

VERSION=$1

echo "Gradle version: ${VERSION}"

case "${VERSION}" in
    4.10*|5.0*)
        if [ "${REVERSE}" = "1" ]; then
            echo "Reversing archiveClassifier modification"
            find ../ -type f -name '*.gradle*' -print0 | xargs -0 -t sed -i \
                 -e 's|classifier = \(.*\)|archiveClassifier.set(\1)|g;' \
                 -e 's|archiveName |archiveFileName = |g;' \
                 -e 's|destinationDir |destinationDirectory = |g;' \
                 -e 's|baseName = |archiveBaseName = |g;' \
                 -e 's|\.archivePath|.archiveFile|g;' \
                 -e 's|${classifier}|${archiveClassifier}|g;' \
                 -e 's|${baseName}|${archiveBaseName}|g;'
        else
            echo "Modifying build for archiveClassifier"
            find ../ -type f -name '*.gradle*' -print0 | xargs -0 -t sed -i \
                 -e 's|archiveClassifier\.set(\(.*\))|classifier = \1|g;' \
                 -e 's|archiveClassifier = |classifier = |g;' \
                 -e 's|${archiveClassifier}|${classifier}|g;' \
                 -e 's|archiveFileName = |archiveName |g;' \
                 -e 's|destinationDirectory = |destinationDir |g;' \
                 -e 's|archiveBaseName = |baseName = |g;' \
                 -e 's|\.archiveFile|.archivePath|g;' \
                 -e 's|${archiveBaseName}|${baseName}|g;'
        fi
        ;;
    *)
        echo "Not modifying build"
        ;;
esac

case "${VERSION}" in
    4.10*)
        if [ "${REVERSE}" = "1" ]; then
            echo "Reversing named tasks modification"
            find ../ -name 'build.gradle.kts' ! -path '*/functTest/*' -print0 | xargs -0 sed -i \
                 -e 's|tasks.getByName(|tasks.named(|g;'
        else
            echo "Modifying build for named tasks"
            find ../ -name 'build.gradle.kts' ! -path '*/functTest/*' -print0 | xargs -0 sed -i \
                 -e 's|tasks.named[(]|tasks.getByName(|g;'
        fi
        ;;
    *)
        echo "Not modifying build"
        ;;
esac

case "${VERSION}" in
    4.10*|5.*|6.*|7.0*)
        if [ "${REVERSE}" = "1" ]; then
            echo "Reversing GradleVersion modification"
            find ../ -name 'build.gradle.kts' -print0 | xargs -0 sed -i \
                 -e 's|org\.gradle\.util\.GradleVersion|GradleVersion|g;'
        else
            echo "Modifying build for GradleVersion"
            find ../ -name 'build.gradle.kts' -print0 | xargs -0 sed -i \
                 -e 's|GradleVersion|org.gradle.util.GradleVersion|g;'
        fi
        ;;
    *)
        echo "Not modifying build"
        ;;
esac
