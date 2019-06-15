import org.apache.commons.io.FileUtils


// this script has access to the following variables
// alignmentModel which is the result of the alignment process
// projectRoot which is the absolute path of the of the aligned model

final String alignedVersion = alignmentModel.version

final buildGradle = new File(projectRoot,'build.gradle')
final lines = FileUtils.readLines(buildGradle)
final newLines = new ArrayList(lines.size())

for (String line : lines) {
    if(line.contains("CustomVersion('1.0.1')")) {
        newLines.add(line.replace("CustomVersion('1.0.1')", "CustomVersion('${alignedVersion}')"))
    } else {
        newLines.add(line)
    }
}

FileUtils.writeLines(buildGradle, newLines)
