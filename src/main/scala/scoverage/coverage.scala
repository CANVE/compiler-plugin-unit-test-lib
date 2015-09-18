package compilerPluginUnitTest

import java.io.File

import scala.collection.mutable

/**
 * @author Stephen Samuel */

sealed trait ClassType
object ClassType {
  case object Object extends ClassType
  case object Class extends ClassType
  case object Trait extends ClassType
  def fromString(str: String): ClassType = {
    str.toLowerCase match {
      case "object" => Object
      case "trait" => Trait
      case _ => Class
    }
  }
}
