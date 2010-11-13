import sbt._

class OptionalProject(info: ProjectInfo) extends DefaultProject(info) with ProguardProject {  
  val localMaven   = "Local Maven" at "file://"+Path.userHome+"/.m2/repository"
  val localIvy     = "Local Ivy" at "file://"+Path.userHome+"/.ivy2/local"
  
  val paranamer  = "com.thoughtworks.paranamer" % "paranamer" % "2.2.1"
  override def managedStyle = ManagedStyle.Maven

  override def proguardInJars = super.proguardInJars +++ scalaLibraryPath
  override def proguardOptions = List(
    "-keep class optional.** ",                   // doesn't keep optional.examples.Intergrated$.main
    "-keep class optional.examples.Integrated$",  // doesn't keep optional.examples.Intergrated$.main
    "-dontshrink",                                // this keeps optional.examples.Intergrated$.main...
    proguardKeepLimitedSerializability,
    proguardKeepAllScala,
    "-keep class ch.epfl.** { *; }",
    "-keep interface scala.ScalaObject"
  )
}
