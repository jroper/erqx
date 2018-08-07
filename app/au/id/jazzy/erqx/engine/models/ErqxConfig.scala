package au.id.jazzy.erqx.engine.models

import com.google.inject.{ProvidedBy, Provider}
import com.typesafe.config.ConfigMemorySize
import javax.inject.{Inject, Singleton}
import play.api.Configuration

@ProvidedBy(classOf[ErqxConfigProvider])
case class ErqxConfig(
  blogs: Seq[BlogConfig],
  cache: CacheConfig,
  serverPush: ServerPush
)

@Singleton
class ErqxConfigProvider @Inject() (configuration: Configuration) extends Provider[ErqxConfig] {
  lazy val get: ErqxConfig = ErqxConfig.fromConfig(configuration)
}

object ErqxConfig {
  def fromConfig(configuration: Configuration): ErqxConfig = {
    val erqxConfig = configuration.get[Configuration]("erqx")

    val blogs = configuration.getPrototypedMap("blogs", "erqx.blogs.prototype").map {
      case (name, blogConfig) =>
        BlogConfig.fromConfig(name, blogConfig)
    }.toList.sortBy(_.order)

    ErqxConfig(
      blogs,
      CacheConfig.fromConfig(erqxConfig.get[Configuration]("cache")),
      ServerPush.fromConfig(erqxConfig.get[Configuration]("http2-server-push"))
    )
  }

}

case class CacheConfig(
  lowWatermark: ConfigMemorySize,
  highWatermark: ConfigMemorySize
)

object CacheConfig {
  def fromConfig(config: Configuration): CacheConfig = {
    CacheConfig(
      config.get[ConfigMemorySize]("low-watermark"),
      config.get[ConfigMemorySize]("high-watermark")
    )
  }
}

case class ServerPush(method: ServerPushMethod, cookie: String)

object ServerPush {
  def fromConfig(config: Configuration): ServerPush = {
    ServerPush(
      config.get[String]("method") match {
        case "none" => ServerPushMethod.None
        case "link" => ServerPushMethod.Link
      },
      config.get[String]("cookie")
    )
  }
}

sealed trait ServerPushMethod

object ServerPushMethod {
  case object Link extends ServerPushMethod
  case object None extends ServerPushMethod
}