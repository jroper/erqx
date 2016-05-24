package au.id.jazzy.erqx.engine.actors

import akka.actor.{ActorRef, Props, Actor}
import au.id.jazzy.erqx.engine.models._

/**
 * Actor responsible for starting some blogs
 */
class BlogsActor(blogConfigs: List[BlogConfig], classLoader: ClassLoader) extends Actor {

  val blogs: List[(BlogConfig, ActorRef)] = {
    blogConfigs.map { config =>
      val actor = context.actorOf(Props(new BlogActor(config.gitConfig, config.path, classLoader)), config.name)
      config -> actor
    }
  }

  def receive = PartialFunction.empty

}
