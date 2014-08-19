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
* Renderable static pages.

## Planned features

* [prismic.io](http://prismic.io) backend.
* Virtual hosting.
* Pluggable blog post formats.
* Multiple language support.

## Installation

1. If you don't have an existing Play project that you want to embed it into, create a new one.
2. Add the following to your `build.sbt` file:

        resolvers += "ERQX Releases" at "https://jroper.github.io/releases"

        libraryDependencies += "au.id.jazzy.erqx" %% "erqx-engine" % "1.0.0"

3. Add a route to the blog router to your `conf/routes` file:

        ->  /       au.id.jazzy.erqx.engine.controllers.BlogsRouter

4. Add the following configuration to your `application.conf`:

        # The blogs
        blogs {

          # A blog with name default.  The name can be anything, it is only used internally.
          default {

            # The path of the blog.  This should not end in a slash.  This path will be relative to the path that the blogs
            # router is deployed to.
            path = "/blog"

            # The order that the blog is routed.  Important if you have blogs that are nested in other blogs paths.
            # Defaults to 10.
            # order = 10

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

You can place posts into a `_posts` directory in the repo, all posts will be picked up from there.  Blog posts should have a name in the format `year-month-day-permalink-title.format`, for example, `2013-11-23-my-first-post.md`.  Allowed formats are `md` and `html`.

Similarly, you can also place static pages into a `_pages` directory in the repo, and all pages will be picked up from there.

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

    # The description - optional, may contain HTML.
    description: |
        This is the description of the blog.  It can contain HTML, such as:
        <ul style="text-align: left">
            <li>Lists
            <li>Images
            <li>Anything else
        </ul>

    # The footer - optional, may contain HTML.
    footer: |
        Copyright Someone. All rights reserved.

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

## Pages

Pages may contain a YAML front matter, containing meta data about the page:

    ---
    # The title of the page. Optional.
    title: Some page title

    # The permalink for the page. Optional. If not specified, will use the path of the file relative to the _pages
    # directory, with the format extension replaced with .html
    # permalink: path/to/page.html
    ---

## Custom themes

Custom themes can be made by implementing `au.id.jazzy.erqx.engine.models.BlogTheme`.  The entry points to the theme are `blogPost`, `blogPosts`, `page` and `notFound`, the default implementations of these methods use the default theme.

Typically, these methods will simply delegate to Scala templates (as the default implementations do).

The other methods are used by the default theme.  The `main` method provides the decorator that the entry points use.

The default theme main template also delegates to the `head`, `navigation` and `footer` templates.  For simple themes, it will often suffice to simply override some of these methods.

## Samples

Sample blogs can be found in the `samples` directory of this repository.  To run them, clone this repository, and then from the root directory, run `play`, then from the console run `project <sampleprojectname>` to swith to the sample project, then run `run`.

## Example sites using erqx

* [all that jazz](http://jazzy.id.au)
* [RopedIn](http://jazzy.id.au/ropedin)
