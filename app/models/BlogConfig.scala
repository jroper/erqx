package models

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
  branch: String,
  remote: Option[String],
  fetchKey: Option[String]
)