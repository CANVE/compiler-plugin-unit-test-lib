package scoverage

import java.io.File

import scala.tools.nsc.Global
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.transform.{Transform, TypingTransformers}

class LocationCompiler(settings: scala.tools.nsc.Settings, reporter: scala.tools.nsc.reporters.Reporter)
  extends scala.tools.nsc.Global(settings, reporter) {

  val locations = List.newBuilder[(String, Location)]

  def compile(code: String): Unit = {
    val files = writeCodeSnippetToTempFile(code)
    val command = new scala.tools.nsc.CompilerCommand(List(files.getAbsolutePath), settings)
    new Run().compile(command.files)
  }

  def writeCodeSnippetToTempFile(code: String): File = {
    val file = File.createTempFile("code_snippet", ".scala")
    IOUtils.writeToFile(file, code)
    file.deleteOnExit()
    file
  }
}