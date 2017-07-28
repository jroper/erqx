package au.id.jazzy.erqx.engine.services.git

import java.time.{ZoneId, ZonedDateTime}

import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.services.MetaDataParser

import scala.util.control.Exception._
import play.api.Logger
import play.api.i18n.Lang
import au.id.jazzy.erqx.engine.models.BlogInfo

import scala.util.control.NonFatal

/**
 * Loads the repository from git
 */
class GitBlogRepository(gitRepo: GitRepository, classLoader: ClassLoader) {

  def loadBlog(id: String, path: String, commitId: String): Blog = {
    val blogInfo = loadInfo(commitId)
    val lastUpdated = ZonedDateTime.ofInstant(gitRepo.commitDate(commitId), blogInfo.timezone)
    new Blog(id, loadBlogPosts(id, commitId, blogInfo.timezone),
      loadPages(commitId, blogInfo.timezone), commitId, path, blogInfo, lastUpdated)
  }

  def loadBlogPosts(id: String, commitId: String, timezone: ZoneId): List[BlogPost] = {
    (gitRepo.listAllFilesInPath(commitId, "_posts").map { files =>
      files.map { file =>
        val path = "_posts/" + file
        val Some(fileLoader) = gitRepo.loadStream(commitId, path)
        val is = fileLoader.openStream()
        try {
          MetaDataParser.parsePostFrontMatter(is, path, path.substring(path.lastIndexOf('/') + 1), timezone)
        } catch {
          case NonFatal(e) =>
            throw new RuntimeException(s"Error loading post '$path' from blog '$id'", e)
        } finally {
          is.close()
        }
      }
    } getOrElse Nil).toList
  }
  
  def loadPages(commitId: String, timezone: ZoneId): List[Page] = {
    (gitRepo.listAllFilesInPath(commitId, "_pages").map { files =>
      files.map { file =>
        val path = "_pages/" + file
        val Some(fileLoader) = gitRepo.loadStream(commitId, path)
        val is = fileLoader.openStream()
        try {
          MetaDataParser.parsePageFrontMatter(is, path, file, timezone)
        } finally {
          is.close()
        }
      }
    } getOrElse Nil).toList    
  }

  def loadInfo(commitId: String) = {
    val config = gitRepo.loadContent(commitId, "_config.yml").map(Yaml.parse(_, ZoneId.systemDefault())).getOrElse(Yaml.empty)

    val theme = config.getString("theme").flatMap { themeClassName =>
      allCatch.either {
        if (themeClassName.endsWith("$"))
          classLoader.loadClass(themeClassName).getDeclaredField("MODULE$").get(null).asInstanceOf[BlogTheme]
        else
          classLoader.loadClass(themeClassName).newInstance().asInstanceOf[BlogTheme]
      } match {
        case Right(t) => Some(t)
        case Left(e) =>
          Logger.warn("Unable to load theme", e)
          None
      }
    } getOrElse DefaultTheme

    val timezone = config.getString("timezone")
      .fold(ZoneId.systemDefault())(ZoneId.of)

    BlogInfo(
      title = config.getString("title").getOrElse("A blog with no name"),
      subTitle = config.getString("subTitle"),
      author = config.getString("author").getOrElse("No one"),
      language = config.getString("language").getOrElse(Lang.defaultLang.code),
      description = config.getString("description"),
      footer = config.getString("footer"),
      theme = theme,
      timezone = timezone,
      properties = config
    )
  }

}
