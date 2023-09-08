val snapshot = false

allprojects {
  var ver = "0.17.0"
  val release = findProperty("otel.release")
  if (release != null) {
    ver += "-" + release
  }
  if (snapshot) {
    ver += "-SNAPSHOT"
  }
  version = ver
}
