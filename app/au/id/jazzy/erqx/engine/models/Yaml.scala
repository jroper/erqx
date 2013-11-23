package au.id.jazzy.erqx.engine.models

import org.joda.time.DateTime
import scala.reflect.ClassTag
import play.api.Logger
import java.util.Date
import scala.util.control.NonFatal

case class Yaml(map: Map[String, AnyRef]) {

  def getString(key: String) = getAs[String](key)
  def getInt(key: String) = getAs[Int](key)
  def getBoolean(key: String) = getAs[Boolean](key)
  def getDate(key: String) = getAs[DateTime](key)
  def getYamlMap(key: String) = getAs[Yaml](key)

  def getMap[T](key: String)(implicit ct: ClassTag[T]): Option[Map[String, T]] = getAs[Yaml](key).map(_.map.filter {
    case (k, t) if ct.runtimeClass.isInstance(t) => true
    case (k, other) =>
      Logger.warn("Ignoring map value for key " + k + ", expected " + ct + " but was " + other)
      false
  }.asInstanceOf[Map[String, T]])

  def getList[T](key: String)(implicit ct: ClassTag[T]): Option[List[T]] = getAs[List[_]](key).map(_.filter {
    case t if ct.runtimeClass.isInstance(t) => true
    case other =>
      Logger.warn("Ignoring list value for key " + key + ", expected " + ct + " but was " + other)
      false
  }.asInstanceOf[List[T]])

  def getAs[T](key: String)(implicit ct: ClassTag[T]): Option[T] = map.get(key).flatMap {
    case t if ct.runtimeClass.isInstance(t) => Some(t.asInstanceOf[T])
    case other =>
      Logger.warn("Ignoring value for key " + key + ", expected " + ct + " but was " + other)
      None
  }
}

object Yaml {
  val empty = Yaml(Map())

  def parse(yaml: String) = {

    import scala.collection.JavaConverters._

    def yamlToScala(obj: AnyRef): AnyRef = obj match {
      case map: java.util.Map[String, AnyRef] => new Yaml(map.asScala.toMap.mapValues(yamlToScala))
      case list: java.util.List[AnyRef] => list.asScala.toList.map(yamlToScala)
      case s: String => s
      case n: Number => n
      case b: java.lang.Boolean => b
      case d: Date => new DateTime(d)
      case null => null
      case other => {
        Logger.warn("Unexpected YAML object of type " + other.getClass)
        other.toString
      }
    }

    try {
      yamlToScala(new org.yaml.snakeyaml.Yaml().load(yaml)) match {
        case y: Yaml => y
        case other =>
          Logger.warn("YAML was not object: " + other)
          Yaml.empty
      }
    } catch {
      case NonFatal(t) =>
        Logger.warn("Error parsing YAML content", t)
        Yaml.empty
    }
  }
}