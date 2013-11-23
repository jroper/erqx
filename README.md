# The ERQX blog engine

ERQX is an embeddable blog engine for Play Framework.

I wrote it to host my own blog, so currently it has the minimum feature set that I needed.

Currently it supports one backend - git.

## Features

* Can be easily embedded into any Play application via a module include in the routes file.
* Git backend with directory layout similar to Jekyll, YAML front matter on blog posts, etc.
* Instant deploy from git hosting provides via commit hooks.
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

3.