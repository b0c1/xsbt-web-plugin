package com.github.siasia

import org.eclipse.jetty.server.{Handler, Server}
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector
import org.eclipse.jetty.webapp.{WebAppContext, WebInfConfiguration, Configuration, FragmentConfiguration, JettyWebXmlConfiguration, TagLibConfiguration, WebXmlConfiguration, MetaInfConfiguration}
import org.eclipse.jetty.util.{Scanner => JScanner}
import org.eclipse.jetty.util.log.{Log, Logger => JLogger}
import org.eclipse.jetty.util.resource.{Resource, ResourceCollection}
import org.eclipse.jetty.xml.XmlConfiguration
import org.eclipse.jetty.plus.webapp.{EnvConfiguration, PlusConfiguration}

import sbt._
import classpath.ClasspathUtilities.toLoader
import scala.xml.NodeSeq


class JettyXRunner extends Runner {

  private[this] val forceJettyLoad = classOf[Server]
  private var server: Server = null
  private var contexts: Map[String, (WebAppContext, Deployment)] = Map()
  private var selectedClasspathList: Seq[File] = null
  private val contextCollection: ContextHandlerCollection = new ContextHandlerCollection

  private def setContextLoader(context: WebAppContext, classpath: Seq[File]) {
    selectedClasspathList = classpath
    val appLoader = toLoader(classpath, loader)
    context.setClassLoader(appLoader)
  }


  private def setEnvConfiguration(context: WebAppContext, file: Option[File]) {
    val preArray: Seq[Configuration] = Seq(
      new WebInfConfiguration,
      new WebXmlConfiguration,
      new MetaInfConfiguration,
      new FragmentConfiguration
    )
    val annotationSupportedConfiguration: Seq[Configuration] = try {
      import org.eclipse.jetty.annotations.{ClassNameResolver, AbstractDiscoverableAnnotationHandler, AnnotationParser, AnnotationConfiguration}
      Seq(new AnnotationConfiguration {

        def classpathLoader(context: WebAppContext, parser: AnnotationParser, path: File) = {
          val classesDir: Resource = Resource.newResource(path)
          if (classesDir exists) {
            parser.clearHandlers
            import scala.collection.JavaConversions._
            for (h <- _discoverableAnnotationHandlers) {
              if (h.isInstanceOf[AbstractDiscoverableAnnotationHandler]) (h.asInstanceOf[AbstractDiscoverableAnnotationHandler]).setResource(null)
            }
            parser.registerHandlers(_discoverableAnnotationHandlers)
            parser.registerHandler(_classInheritanceHandler)
            parser.registerHandlers(_containerInitializerAnnotationHandlers)
            parser.parse(classesDir, new ClassNameResolver {
              def isExcluded(name: String): Boolean = {
                if (context.isSystemClass(name)) return true
                if (context.isServerClass(name)) return false
                return false
              }

              def shouldOverride(name: String): Boolean = {
                return true
              }
            })
          }
        }


        override def parseWebInfClasses(context: WebAppContext, parser: AnnotationParser) {
          for (item: File <- selectedClasspathList) {
            if (item isDirectory) {
              classpathLoader(context, parser, item)
            }
          }
          super.parseWebInfClasses(context, parser)
        }

      })
    } catch {
      case e: Throwable => Seq()
    }


    val customConfiguration = try {
      Seq(file match {
        case Some(config) =>
          new EnvConfiguration {
            setJettyEnvXml(config.toURI.toURL)
          }
      }, new PlusConfiguration)
    } catch {
      case e: Throwable => Seq()
    }

    val postArray: Seq[Configuration] = Seq(
      new JettyWebXmlConfiguration,
      new TagLibConfiguration)

    context.setConfigurations((preArray ++ customConfiguration ++ annotationSupportedConfiguration ++ postArray).toArray)
  }

  private def deploy(contextPath: String, deployment: Deployment) = {
    import deployment._
    val context = new WebAppContext()
    context.setContextPath(contextPath)
    context.setBaseResource(
      new ResourceCollection(
        webappResources.map(_.getPath).toArray
      ))
    setContextLoader(context, classpath)
    setEnvConfiguration(context, env)
    if (!scanDirectories.isEmpty)
      new Scanner(scanDirectories, scanInterval, () => reload(contextPath))
    contexts += contextPath ->(context, deployment)
    context
  }

  private def configureContexts(apps: Seq[(String, Deployment)]) {
    val contexts = apps.map {
      case (contextPath, deployment) =>
        deploy(contextPath, deployment)
    }
    contextCollection.setHandlers(contexts.toArray)
    server.setHandler(contextCollection)
  }

  private def configureCustom(confFiles: Seq[File], confXml: NodeSeq) {
    confXml.foreach(x => new XmlConfiguration(x.toString) configure (server))
    confFiles.foreach(f => new XmlConfiguration(f.toURI.toURL) configure (server))
  }

  private def configureConnector(port: Int) {
    val conn = new SelectChannelConnector
    conn.setPort(port)
    server.addConnector(conn)
  }

  private def configureSecureConnector(ssl: SslSettings) {
    val conn = new SslSelectChannelConnector()
    conn.setPort(ssl.port)
    conn.setKeystore(ssl.keystore)
    conn.setPassword(ssl.password)
    conn.setKeyPassword(ssl.keyPassword)
    server.addConnector(conn)
  }

  def start(port: Int, ssl: Option[SslSettings], logger: AbstractLogger, apps: Seq[(String, Deployment)], customConf: Boolean, confFiles: Seq[File], confXml: NodeSeq) {
    println("Jetty Runner :", this.getClass.getName)
    if (server != null)
      return
    try {
      Log.setLog(new DelegatingLogger(logger))
      server = new Server
      configureCustom(confFiles, confXml)
      if (!customConf) {
        configureConnector(port)
        ssl match {
          case Some(s) => configureSecureConnector(s)
          case _ =>
        }
        configureContexts(apps)
      }
      server.start()
    } catch {
      case e =>
        server = null
        throw e
    }
  }



  def reload(contextPath: String) {
    val (context, deployment) = contexts(contextPath)
    context.stop()
    contextCollection.removeHandler(context)
    val newContext = deploy(contextPath, deployment)
    contextCollection.addHandler(newContext)

    newContext.start()
  }

  def stop() {
    if (server != null)
      server.stop()
    server = null
  }

  class DelegatingLogger(delegate: AbstractLogger) extends LoggerBase(delegate) with JLogger {
    def getLogger(name: String) = this
  }

  class Scanner(scanDirs: Seq[File], scanInterval: Int, thunk: () => Unit) extends JScanner {

    import scala.collection.JavaConversions._

    setScanDirs(scanDirs)
    setRecursive(true)
    setScanInterval(scanInterval)
    setReportExistingFilesOnStartup(false)
    val listener = new JScanner.BulkListener {
      def filesChanged(files: java.util.List[String]) {
        thunk()
      }
    }
    addListener(listener)
    start()
  }

}
