package scoverage

import java.io.{File, FileNotFoundException}
import java.net.URL

import scala.collection.mutable.ListBuffer
import scala.tools.nsc.{Settings, Global, Phase}
import scala.tools.nsc.plugins.PluginComponent

import scala.tools.nsc.transform.{Transform, TypingTransformers}

trait InjectablePluginComponent {
  def apply(global: Global)(body: global.Tree)
}

/** @author Stephen Samuel */
object ScoverageCompiler {

  val ScalaVersion = "2.11.4"
  val ShortScalaVersion = ScalaVersion.dropRight(2)

  def classPath = getScalaJars.map(_.getAbsolutePath) :+ sbtCompileDir.getAbsolutePath // :+ runtimeClasses.getAbsolutePath

  def settings: Settings = {
    val s = new scala.tools.nsc.Settings
    s.Xprint.value = List("all")
    s.Yrangepos.value = true
    s.Yposdebug.value = true
    s.classpath.value = classPath.mkString(File.pathSeparator)

    val path = s"./target/scala-$ShortScalaVersion/test-generated-classes"
    new File(path).mkdirs()
    s.d.value = path
    s
  }

  def default(unitTestObj: InjectablePluginComponent): ScoverageCompiler = {
    val reporter = new scala.tools.nsc.reporters.ConsoleReporter(settings)
    new ScoverageCompiler(settings, reporter, unitTestObj)
  }

  def locationCompiler: LocationCompiler = {
    val reporter = new scala.tools.nsc.reporters.ConsoleReporter(settings)
    new LocationCompiler(settings, reporter)
  }

  private def getScalaJars: List[File] = {
    val scalaJars = List("scala-compiler", "scala-library", "scala-reflect")
    scalaJars.map(findScalaJar)
  }

  private def sbtCompileDir: File = {
    val dir = new File("./target/scala-" + ShortScalaVersion + "/classes")
    if (!dir.exists)
      throw new FileNotFoundException(s"Could not locate SBT compile directory for plugin files [$dir]")
    dir
  }

  // private def runtimeClasses: File = new File("./scalac-scoverage-runtime/target/scala-2.11/classes")

  private def findScalaJar(artifactId: String): File = findIvyJar("org.scala-lang", artifactId, ScalaVersion)

  private def findIvyJar(groupId: String, artifactId: String, version: String): File = {
    val userHome = System.getProperty("user.home")
    val sbtHome = userHome + "/.ivy2"
    val jarPath = sbtHome + "/cache/" + groupId + "/" + artifactId + "/jars/" + artifactId + "-" + version + ".jar"
    val file = new File(jarPath)
    if (!file.exists)
      throw new FileNotFoundException(s"Could not locate [$jarPath].")
    file
  }
}

class ScoverageCompiler(settings: scala.tools.nsc.Settings, 
                        reporter: scala.tools.nsc.reporters.Reporter,
                        unitTestObj: InjectablePluginComponent)
  extends scala.tools.nsc.Global(settings, reporter) {

  def addToClassPath(groupId: String, artifactId: String, version: String): Unit = {
    settings.classpath.value = settings.classpath.value + File.pathSeparator + ScoverageCompiler
      .findIvyJar(groupId, artifactId, version)
      .getAbsolutePath
  }

  val instrumentationComponent = new ScoverageInstrumentationComponent(this)
  instrumentationComponent.setOptions(new ScoverageOptions())
  val canveUnitTest = new CanveUnitTest(this)
  val validator = new PositionValidator(this)

  def compileSourceFiles(files: File*): ScoverageCompiler = {
    val command = new scala.tools.nsc.CompilerCommand(files.map(_.getAbsolutePath).toList, settings)
    new Run().compile(command.files)
    this
  }

  def writeCodeSnippetToTempFile(code: String): File = {
    val file = File.createTempFile("scoverage_snippet", ".scala")
    IOUtils.writeToFile(file, code)
    file.deleteOnExit()
    file
  }

  def compileCodeSnippet(code: String): ScoverageCompiler = compileSourceFiles(writeCodeSnippetToTempFile(code))
  def compileSourceResources(urls: URL*): ScoverageCompiler = {
    compileSourceFiles(urls.map(_.getFile).map(new File(_)): _*)
  }

  class PositionValidator(val global: Global) extends PluginComponent with TypingTransformers with Transform {

    override val phaseName: String = "scoverage-validator"
    override val runsAfter: List[String] = List("typer")
    override val runsBefore = List[String]("scoverage-instrumentation")

    override protected def newTransformer(unit: global.CompilationUnit): global.Transformer = new Transformer(unit)
    class Transformer(unit: global.CompilationUnit) extends TypingTransformer(unit) {

      override def transform(tree: global.Tree) = {
        global.validatePositions(tree)
        tree
      }
    }
  }

  class CanveUnitTest(val global: Global) extends PluginComponent {

    val runsAfter = List("typer")

    override val runsRightAfter = Some("typer")
  
    val phaseName = "canve-unit-tester"
    
    
    override def newPhase(prev: Phase): Phase = new Phase(prev) {
      def name : String = phaseName 
      override def run() {
        
        println(Console.BLUE + Console.BOLD + "\ncanve unit test running" + Console.RESET)
        
        def units = global.currentRun
                    .units
                    .toSeq
                    .sortBy(_.source.content.mkString.hashCode())
        
        units.foreach { unit =>
          unitTestObj.apply(global)(unit.body)
          println(Console.BLUE + "canve unit testing plugin examining source file" + unit.source.path + "..." + Console.RESET)
        }
      }
    }
  }

  override def computeInternalPhases() {
    val phs = List(
      syntaxAnalyzer -> "parse source into ASTs, perform simple desugaring",
      analyzer.namerFactory -> "resolve names, attach symbols to named trees",
      analyzer.packageObjects -> "load package objects",
      analyzer.typerFactory -> "the meat and potatoes: type the trees",
      canveUnitTest -> "the unit test injector"
    )
    phs foreach (addToPhasesSet _).tupled
  }
}


