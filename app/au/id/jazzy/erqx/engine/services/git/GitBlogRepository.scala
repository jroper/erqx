package au.id.jazzy.erqx.engine.services.git

import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.services.MetaDataParser
import scala.util.control.Exception._
import play.api.Play.current
import play.api.{Logger, Play}
import play.api.i18n.Lang
import au.id.jazzy.erqx.engine.models.BlogInfo

/**
 * Loads the repository from git
 */
class GitBlogRepository(gitRepo: GitRepository) {

  def loadBlog(commitId: String): List[BlogPost] = {
    (gitRepo.listAllFilesInPath(commitId, "_posts").map { files =>
      files.map { file =>
        val path = "_posts/" + file
        val Some((_, is)) = gitRepo.loadStream(commitId, path)
        try {
          MetaDataParser.parseFrontMatter(is, path, path.substring(path.lastIndexOf('/') + 1))
        } finally {
          is.close()
        }
      }
    } getOrElse Nil).toList
  }

  def loadConfig(commitId: String) = {
    val config = gitRepo.loadContent(commitId, "_config.yml").map(Yaml.parse).getOrElse(Yaml.empty)

    val theme = config.getString("theme").flatMap { themeClassName =>
      allCatch.either {
        if (themeClassName.endsWith("$"))
          Play.classloader.loadClass(themeClassName).getMethod("MODULE$").invoke(null).asInstanceOf[BlogTheme]
        else
          Play.classloader.loadClass(themeClassName).newInstance().asInstanceOf[BlogTheme]
      } match {
        case Right(t) => Some(t)
        case Left(e) =>
          Logger.warn("Unable to load theme", e)
          None
      }
    } getOrElse DefaultTheme

    BlogInfo(
      title = config.getString("title").getOrElse("A blog with no name"),
      subTitle = config.getString("subTitle"),
      author = config.getString("author").getOrElse("No one"),
      language = config.getString("language").getOrElse(Lang.defaultLang.code),
      description = config.getString("description"),
      theme = theme,
      properties = config
    )
  }

}
