package au.id.jazzy.erqx.engine.models

import java.io.File

import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

/**
 * Configuration for blogs
 */
case class BlogConfig(
  name: String,
  path: String,
  gitConfig: GitConfig,
  order: Int
)

object BlogConfig {
  def fromConfig(name: String, config: Configuration): BlogConfig = {
    BlogConfig(
      name,
      config.get[String]("path"),
      GitConfig.fromConfig(name, config.get[Configuration]("gitConfig")),
      config.get[Int]("order")
    )
  }
}

/**
 * Git config
 */
case class GitConfig(
  id: String,
  gitRepo: File,
  path: Option[String],
  branch: String,
  remote: Option[String],
  fetchKey: Option[String],
  updateInterval: Option[FiniteDuration],
  authConfig: GitAuthConfig,
  draftPrefix: Option[String]
)

object GitConfig {
  def fromConfig(name: String, config: Configuration): GitConfig = {
    val authConfig = config.get[Option[Configuration]]("auth") match {
      case Some(c) =>
        c.get[String]("type") match {
          case "password" =>
            GitPasswordAuthConfig(
              c.get[String]("username"),
              c.get[String]("password")
            )
          case "ssh" =>
            GitSshAuthConfig(
              c.get[String]("key-file")
            )
          case unknown => sys.error(s"Unknown git auth config type ${unknown}")
        }
      case None => GitNoAuthConfig
    }


    GitConfig(
      name,
      new File(config.get[String]("gitRepo")),
      config.get[Option[String]]("path"),
      config.get[String]("branch"),
      config.get[Option[String]]("remote"),
      config.get[Option[String]]("fetchKey"),
      config.get[Option[FiniteDuration]]("updateInterval"),
      authConfig,
      config.get[Option[String]]("draft-prefix")
    )
  }
}

sealed trait GitAuthConfig

case object GitNoAuthConfig extends GitAuthConfig

case class GitPasswordAuthConfig(username: String, password: String) extends GitAuthConfig

case class GitSshAuthConfig(keyFile: String) extends GitAuthConfig