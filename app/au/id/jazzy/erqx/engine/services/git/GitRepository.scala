package au.id.jazzy.erqx.engine.services.git

import au.id.jazzy.erqx.engine.models.{GitConfig, GitNoAuthConfig, GitPasswordAuthConfig, GitSshAuthConfig}
import com.jcraft.jsch.{JSch, Session}

import java.io.{File, InputStream}
import java.time.Instant
import org.eclipse.jgit.lib._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand.ListMode
import org.eclipse.jgit.treewalk.filter.{PathFilter, PathSuffixFilter, TreeFilter}
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.{JschConfigSessionFactory, OpenSshConfig, SshTransport, Transport, UsernamePasswordCredentialsProvider}
import org.eclipse.jgit.util.FS
import play.doc.{FileHandle, FileRepository}

import scala.io.Source
import scala.jdk.CollectionConverters._

class GitRepository(config: GitConfig) {

  private val repository = new RepositoryBuilder().setGitDir(new File(config.gitRepo, ".git")).build()
  private val git = new Git(repository)

  def close() = repository.close()

  /**
   * Do a fetch if configured to do so
   */
  def fetch(): Unit = config.remote.foreach { r =>
    val command = config.authConfig match {
      case GitNoAuthConfig => git.fetch()
      case GitPasswordAuthConfig(username, password) =>
        git.fetch().setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
      case GitSshAuthConfig(keyFile) =>
        git.fetch().setTransportConfigCallback((transport: Transport) => {
          val sshTransport = transport.asInstanceOf[SshTransport]
          sshTransport.setSshSessionFactory(new JschConfigSessionFactory() {
            override def configure(hc: OpenSshConfig.Host, session: Session): Unit = {
              session.setConfig("StrictHostKeyChecking", "no")
            }
            override def createDefaultJSch(fs: FS): JSch = {
              val jsch = super.createDefaultJSch(fs)
              jsch.addIdentity(keyFile)
              jsch
            }
          })
        })
    }
    command.setRemote(r).call()
  }

  /**
   * Get the current hash for the repo
   */
  def currentHash: String = {
    val ref = config.remote.map("refs/remotes/" + _ + "/").getOrElse("") + config.branch
    Option(repository.findRef(ref))
      .map(_.getObjectId.name())
      .getOrElse {
        throw new RuntimeException("Could not find ref \"" + ref + "\" in repository " + config.gitRepo)
      }
  }

  /**
    * Get the date of the commit
    */
  def commitDate(commitId: String): Instant = {
    Instant.ofEpochSecond(repository.parseCommit(ObjectId.fromString(commitId)).getCommitTime)
  }

  /**
   * Load the file with the given path in the given ref
   *
   * @param path The path to load
   * @return A tuple of the file size and its input stream, if the file was found
   */
  def loadStream(commitId: String, path: String): Option[ObjectLoader] = {
    scanFiles(ObjectId.fromString(commitId), PathFilter.create(config.path.getOrElse("") + path)) { treeWalk =>
      if (!treeWalk.next()) {
        None
      } else {
        val file = repository.open(treeWalk.getObjectId(0))
        Some(file)
      }
    }
  }

  def loadContent(commitId: String, path: String): Option[String] = {
    loadStream(commitId, path).map {
      case file if file.isLarge =>
        val is = file.openStream()
        try {
          Source.fromInputStream(file.openStream()).mkString
        } finally {
          is.close()
        }
      case smallFile =>
        new String(smallFile.getCachedBytes, "utf-8")
    }
  }


  // A tree filter that finds files with the given name under the given base path
  private class FileWithNameFilter(basePath: String, name: String) extends TreeFilter {
    val pathRaw = Constants.encode(basePath)

    // Due to this bug: https://bugs.eclipse.org/bugs/show_bug.cgi?id=411999
    // we have to supply a byte array that has one dummy byte at the front of it.
    // As soon as that bug is fixed, this code will break, just remove the #.
    val nameRaw = Constants.encode("#/" + name)

    def shouldBeRecursive() = false

    def include(walker: TreeWalk) = {
      // The way include works is if it's a subtree (directory), then we return true if we want to descend into it,
      // and if it's not, then we return true if the file is the one we want.
      walker.isPathPrefix(pathRaw, pathRaw.length) == 0 &&
        (walker.isSubtree || walker.isPathSuffix(nameRaw, nameRaw.length))
    }

    override def clone() = this
  }

  def findFileWithName(commitId: String, basePath: Option[String], name: String): Option[String] = {
    scanFiles(ObjectId.fromString(commitId),
      basePath.map(new FileWithNameFilter(_, name)).getOrElse(PathSuffixFilter.create("#/" + name))
    ) { treeWalk =>
      if (!treeWalk.next()) {
        None
      } else {
        Some(treeWalk.getPathString.drop(basePath.map(_.length + 1).getOrElse(0)))
      }
    }
  }

  def listAllFilesInPath(commitId: String, path: String): Option[Seq[String]] = {
    val prefixedPath = config.path.getOrElse("") + path
    scanFiles(ObjectId.fromString(commitId), PathFilter.create(prefixedPath)) { treeWalk =>
      def extract(list: List[String]): List[String] = {
        if (!treeWalk.next()) {
          list
        } else {
          extract(treeWalk.getPathString.drop(prefixedPath.length + 1) :: list)
        }
      }
      Some(extract(Nil))
    }
  }

  private def scanFiles[T](commitId: ObjectId, filter: TreeFilter)(block: TreeWalk => Option[T]): Option[T] = {
    // Now find the tree for that commit id
    val revWalk = new RevWalk(repository)
    try {
      val tree = revWalk.parseCommit(commitId).getTree
      // And walk it
      val treeWalk = new TreeWalk(repository)
      try {
        treeWalk.addTree(tree)
        treeWalk.setRecursive(true)
        treeWalk.setFilter(filter)
        block(treeWalk)
      } finally {
        treeWalk.close()
      }
    } finally {
      revWalk.dispose()
    }
  }

  def listDrafts(): Seq[GitDraft] = {
    config.draftPrefix match {
      case Some(prefix) =>
        val fullPrefix = config.remote match {
          case Some(remote) => s"refs/remotes/$remote/$prefix"
          case None => s"refs/heads/$prefix"
        }
        git.branchList().setListMode(ListMode.ALL).call()
          .asScala
          .collect {
            case draft if draft.getName.startsWith(fullPrefix) =>
              val branchName = draft.getName.stripPrefix(fullPrefix)
              GitDraft(branchName, draft.getObjectId.name())
          }.toSeq
      case None => Nil
    }
  }
}

class GitFileRepository(gitRepository: GitRepository, commitId: String, base: Option[String]) extends FileRepository {
  def loadFile[A](path: String)(loader: (InputStream) => A) = {
    gitRepository.loadStream(commitId, base.map(_ + "/" + path).getOrElse(path)).map { file =>
      val is = file.openStream()
      try {
        loader(is)
      } finally {
        is.close()
      }
    }
  }

  def findFileWithName(name: String) = gitRepository.findFileWithName(commitId, base, name)

  def handleFile[A](path: String)(handler: (FileHandle) => A) = {
    gitRepository.loadStream(commitId, base.map(_ + "/" + path).getOrElse(path)).map { file =>
      val is = file.openStream()
      handler(FileHandle(path.drop(path.lastIndexOf('/') + 1), file.getSize, is, () => is.close()))
    }
  }
}

case class GitDraft(name: String, commitId: String)
