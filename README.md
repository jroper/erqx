# The ERQX blog engine

ERQX is an embeddable blog engine for Play Framework.

I wrote it to host my own blog, so currently it has the minimum feature set that I needed.

Currently it supports one backend - git.

## Features

* Can be easily embedded into any Play application via a module include in the routes file.
* Git backend with directory layout similar to Jekyll, YAML front matter on blog posts, etc.
* Instant deploy from git hosting provided via commit hooks.
* Supports Markdown and HTML.
* Markdown blog posts may include code snippets from files external files - this allows embedding code snippets that are compiled and/or tested.
* Can host multiple blogs.
* Completely themeable.
* Default theme uses responsive layout and Disqus for comments.
* Support for many common blog features including tags, archive navigation, ATOM feeds.

## Planned features

* [prismic.io](http://prismic.io) backend.
* Virtual hosting.
* Pluggable blog post formats.
* Renderable static pages.
* Multiple language support.

## Installation

1. If you don't have an existing Play project that you want to embed it into, create a new one.
2. Add the following to your `build.sbt` file:

        resolvers += // TODO add resolver

        libraryDependencies += "au.id.jazzy.erqx" %% "erqx-engine" % "1.0.0"

3. Add the blog plugin to your `conf/play.plugins` file:

        1100:au.id.jazzy.erqx.engine.BlogPlugin

4. Add a route to the blog router to your `conf/routes` file:

        ->  /       au.id.jazzy.erqx.engine.controllers.BlogsRouter

5. Add the following configuration to your `application.conf`:

        # The file loader dispatcher is used by the actor that loads files out of git.  All blocking git IO is done on these
        # threads.
        file-loader-dispatcher {
          type = Dispatcher
          executor = "thread-pool-executor"
          thread-pool-executor {
            core-pool-size-min = 10
            core-pool-size-max = 10
          }
        }

        # The blog loader dispatcher is used to do fetching and reindexing of the blog when it's updated.  There only needs to
        # be one of these threads, all tasks done on this dispatcher are background tasks.
        blog-loader-dispatcher {
          type = Dispatcher
          executor = "thread-pool-executor"
          thread-pool-executor {
            core-pool-size-min = 1
            core-pool-size-max = 1
          }
        }

        # The blogs
        blogs {

          # A blog with name default.  The name can be anything, it is only used internally.
          default {

            # The path of the blog.  This should not end in a slash.  This path will be relative to the path that the blogs
            # router is deployed to.
            path = "/blog"

            # The git configuration
            gitConfig {

              # The repo must be a repo on the filesystem that has been cloned from somewhere.
              gitRepo = "/path/to/some/repo"

              # The path within the repo to serve the blog from.  Optional.
              # path = "blog/"

              # The branch to read the blog from.  Defaults to published.
              # branch = "published"

              # The name of the remote to fetch from.  If not specified, no fetch will be done when updating.
              # remote = "origin"

              # The fetch key.  Used to authenticate commit hooks from a remote git repository such as GitHub.
              # If not specified, remote triggering of fetching is disabled.
              # fetchKey = "somesecret"

              # The update interval.  If specified, the blog will be fetched (if a remote is configured) and reindexed at this
              # interval.  Reindexing is only done if the blog has actually changed.
              updateInterval = 10 minutes
            }
          }
        }

Now you're good to go!

## Blog repo layout

You can place posts into `_posts` directory in the repo, all posts will be picked up from there.  Blog posts should have a name in the format `year-month-day-permalink-title.format`, for example, `2013-11-23-my-first-post.md`.  Allowed formats are `md` and `html`.

A file called `_config.yml` should be placed in the root directory of the repo.

Anything found in the root directory that doesn't start with an underscore will be served as a static asset, so images, for example, can be placed anywhere in the repo.

## Config file

The config file contains the main properties for the blog post.  For example:

    # The title
    title: My blog

    # Optional subtitle
    # subTitle: Just another ERQX blog

    # Author - used by atom feed
    author: Someone

    # The description
    description: |
        This is the description of the blog.  It can contain HTML, such as:
        <ul style="text-align: left">
            <li>Lists
            <li>Images
            <li>Anything else
        </ul>

    # The theme to use, if not specified uses the default theme.
    # theme: au.id.jazzy.erqx.engine.models.DefaultTheme$

    # Other arbitrary properties may go here that may be used by the theme.

    # The default theme allows specifying a disqus id:
    # disqus_shortname: mydisqusblog

## Blog posts

Blog posts may contain a YAML front matter, containing meta data about the blog post.  If there is no meta data, the meta data will be attempted to be extracted from the blog post file name. Here is an example front matter:

    ---
    # The title of the post.  Optional.  Extracted from the filename if not present.
    title: My first blog post

    # Any tags for the post. Space separated.  Multi word tags can have their spaces escaped with +
    tags: first post

    # The date for the post, in YAML date format.  Extracted from the filename if not present.
    # date: 2013-11-23

    # Optional id. This is used for example when identifying the post to disqus.
    # id: some-id
    ---

If the blog post is in markdown format, it will be rendered using the [Play documentation markdown renderer](https://github.com/playframework/play-doc) (which itself is built on top of [pegdown](https://github.com/sirthias/pegdown)).  This means code snippets may be included from external files using the following syntax:

    @[some-label](_code/path/to/some/Code.scala)

Within the `Code.scala` source file, the markdown formatter will look for a snippet of code between two lines containing the string `#some-label`.  Typically this string will be found in a comment line, for example, in Scala:

    //#some-label
    val four = 1 + 3
    //#some-label

## Custom themes

Custom themes can be made by implementing `au.id.jazzy.erqx.engine.models.BlogTheme`.  The main methods on this to implement are `main`, `blogPost`, `blogPosts` and `notFound`, the default implementations of these use the default them.  Typically, these methods will simply delegate to Scala templates (as the default implementations do).

The `main` method provides the decorator that the default templates for the other 3 page types use.  If implementing them, it is not necessary to implement the `main` method.  However it is provided so that to change the overall style of the site, only the `main` method needs to be overridden.

## Samples

Sample blogs can be found in the `samples` directory of this repository.  To run them, clone this repository, and then from the root directory, run `play`, then from the console run `project <sampleprojectname>` to swith to the sample project, then run `run`.
