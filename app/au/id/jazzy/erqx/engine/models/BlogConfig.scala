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
  updateInterval: Option[FiniteDuration]
)

object GitConfig {
  def fromConfig(name: String, config: Configuration): GitConfig = {
    GitConfig(
      name,
      new File(config.get[String]("gitRepo")),
      config.get[Option[String]]("path"),
      config.get[String]("branch"),
      config.get[Option[String]]("remote"),
      config.get[Option[String]]("fetchKey"),
      config.get[Option[FiniteDuration]]("updateInterval")
    )
  }
}