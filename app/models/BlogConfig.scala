package models

import java.io.File

/**
 * Configuration for blogs
 */
case class BlogConfig(
  name: String,
  gitConfig: GitConfig,
  path: String
)

case class GitConfig(
  gitRepo: File,
  branch: String,
  remote: Option[String]
)