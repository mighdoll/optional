import sbt._

class OptionalProject(info: ProjectInfo) extends DefaultProject(info) {  
  val localMaven   = "Local Maven" at "file://"+Path.userHome+"/.m2/repository"
  val localIvy     = "Local Ivy" at "file://"+Path.userHome+"/.ivy2/local"
  
  val paranamer  = "com.thoughtworks.paranamer" % "paranamer" % "2.2.1"
  override def managedStyle = ManagedStyle.Maven
}
