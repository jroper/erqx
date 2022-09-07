package au.id.jazzy.erqx.engine.services.git

import java.time.{ZoneId, ZonedDateTime}

import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.services.MetaDataParser

import scala.util.control.Exception._
import play.api.i18n.Lang
import au.id.jazzy.erqx.engine.models.BlogInfo
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

/**
 * Loads the repository from git
 */
class GitBlogRepository(gitRepo: GitRepository, classLoader: ClassLoader) {

  private val log = LoggerFactory.getLogger(getClass)

  def loadBlog(id: String, path: String, commitId: String, gitDrafts: Seq[GitDraft]): Blog = {
    val blogInfo = loadInfo(id, commitId)
    val lastUpdated = ZonedDateTime.ofInstant(gitRepo.commitDate(commitId), blogInfo.timezone)

    val drafts = gitDrafts.map { draft =>
        val draftId = s"$id-draft-${draft.name}"
        val draftInfo = loadInfo(draftId, draft.commitId)
        val lastUpdated = ZonedDateTime.ofInstant(gitRepo.commitDate(draft.commitId), draftInfo.timezone)
        draft.commitId -> new Blog(draftId,
          loadBlogPosts(draftId, draft.commitId, draftInfo.timezone),
          loadPages(draft.commitId, draftInfo.timezone), draft.commitId, path, draftInfo, lastUpdated, Map.empty)
    }

    val blog = new Blog(id, loadBlogPosts(id, commitId, blogInfo.timezone),
      loadPages(commitId, blogInfo.timezone), commitId, path, blogInfo, lastUpdated, drafts.toMap)

    val draftsMessage = if (drafts.isEmpty) {
      "no drafts"
    } else {
      drafts.map(_._2.id).mkString("drafts: [", ",", "]")
    }
    log.info(s"Loaded blog with $draftsMessage")
    blog
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

  private def loadInfo(blogId: String, commitId: String) = {
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
          log.warn(s"Unable to load theme for blog $blogId", e)
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
      properties = config,
      assetsExpiry = config.getString("assetsExpiry").flatMap { duration =>
        try {
          Some(Duration(duration))
        } catch {
          case NonFatal(t) =>
            log.warn(s"Unable to parse assetsExpiry '$duration' for blog $blogId", t)
            None
        }
      }
    )
  }

}
