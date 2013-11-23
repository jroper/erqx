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
This is the first blog post for this blog.

The file for this post can be found in `_posts/2013-11-23-my-first-blog-post.md`.

It is a very exciting blog post, with some technical content, such as this code snippet:

@[mycode](/_code/firstpost/src/test/scala/Foo.scala)

If you look at the markdown source for this blog post, the above code is just a reference to a snippet, it looks like this:

```markdown
@[mycode](/_code/firstpost/src/test/scala/Foo.scala)
```

This pulls the code from the location `_code/firstpost/src/test/scala/Foo.scala` between the two `#mycode` markers into the rendered file.  The full source code of that file is:

@[fullcode](/_code/firstpost/src/test/scala/Foo.scala)

There is also an SBT build file in `_code/firstpost/build.sbt`, so when running `sbt test` in the `_code/firstpost` directory, it compiles and tests the code snippet, ensuring that all the code in the blog post compiles.

