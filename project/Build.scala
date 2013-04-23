import sbt.{Node => _, _}

import Keys._
import ScriptedPlugin._
import com.github.siasia._
import SonatypePlugin._

object PluginBuild extends Build {

  def sharedSettings = sonatypeSettings ++ Seq(
    projectID <<= (organization, moduleName, version, artifacts, crossPaths) {
      (org, module, version, as, crossEnabled) =>
        ModuleID(org, module, version).cross(crossEnabled).artifacts(as: _*)
    },
    organization := "com.github.siasia",
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    pomUrl := "http://github.com/siasia/xsbt-web-plugin",
    licenses := Seq(
      "BSD 3-Clause" -> new URL("https://github.com/siasia/xsbt-web-plugin/blob/master/LICENSE")
    ),
    scm :=(
      "scm:git:git@github.com:siasia/xsbt-web-plugin.git",
      "scm:git:git@github.com:siasia/xsbt-web-plugin.git",
      "git@github.com:siasia/xsbt-web-plugin.git"
      ),
    developers := Seq((
      "siasia",
      "Artyom Olshevskiy",
      "siasiamail@gmail.com"
      ))
  )

  def appendedSettings = Seq(
    version <<= (sbtVersion, version)(_ + "-" + _)
  )

  lazy val ivyHome = System.getProperty("sbt.ivy.home")

  def rootSettings: Seq[Setting[_]] = sharedSettings ++ scriptedSettings ++ Seq(
    sbtPlugin := true,
    name := "xsbt-web-plugin",
    version := "0.2.11.2",
    ivyLoggingLevel := UpdateLogging.Full,
    logLevel in Global := Level.Debug,
    libraryDependencies ++= Seq(
      "org.mortbay.jetty" % "jetty" % "6.1.22" % "optional",
      "org.mortbay.jetty" % "jetty-plus" % "6.1.22" % "optional",
      "org.eclipse.jetty" % "jetty-webapp" % "8.1.10.v20130312" % "optional",
      "org.eclipse.jetty" % "jetty-plus" % "8.1.10.v20130312" % "optional",
      "org.eclipse.jetty" % "jetty-annotations" % "8.1.10.v20130312" % "optional"
    ),
    publishLocal <<= (publishLocal in commons, publishLocal) map ((_, p) => p),
    scalacOptions += "-deprecation",
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= (if (ivyHome != null) Seq("-Dsbt.ivy.home="+ivyHome,"-Xdebug","-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5006") else Seq()),
    classpathTypes += "orbit",
    ivyXML :=
      <dependency org="org.eclipse.jetty.orbit" name="javax.servlet" rev="3.0.0.v201112011016">
        <artifact name="javax.servlet" type="orbit" ext="jar"/>
      </dependency>
  ) ++ appendedSettings

  def commonsSettings = sharedSettings ++ Seq(
    name := "plugin-commons",
    version := "0.1.2-SNAPSHOT",
    libraryDependencies <++= (sbtVersion) {
      (v) => Seq(
        "org.scala-sbt" % "classpath" % v % "provided"
      )
    }
  ) ++ appendedSettings

  lazy val root = Project("root", file(".")) settings (rootSettings: _*) dependsOn (commons)
  lazy val commons = Project("commons", file("commons")) settings (commonsSettings: _*)
}
