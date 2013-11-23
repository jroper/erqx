package au.id.jazzy.erqx.engine.models

import java.io.File

/**
 * Configuration for blogs
 */
case class BlogConfig(
  name: String,
  path: String,
  gitConfig: GitConfig
)

/**
 * Git config
 */
case class GitConfig(
  gitRepo: File,
  path: Option[String],
  branch: String,
  remote: Option[String],
  fetchKey: Option[String],
  updateInterval: Option[Long]
)